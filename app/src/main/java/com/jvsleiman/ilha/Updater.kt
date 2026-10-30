package com.jvsleiman.ilha

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Atualização dentro do app: lê as versões publicadas no GitHub (HTTPS), baixa o APK,
 * confere o SHA-256 e a assinatura e entrega ao instalador oficial do Android,
 * que sempre pede a sua confirmação.
 */
object Updater {

    data class Release(
        val build: Long,
        val name: String,
        val apkUrl: String?,
        val notas: String,
        val data: String,
        /** "sha256:..." informado pelo GitHub para o APK (se houver). */
        val digest: String?,
    )

    private fun api(repo: String) = "https://api.github.com/repos/${repo.trim().trim('/')}/releases"

    fun installedBuild(ctx: Context): Long =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode

    fun installedName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        try {
            when (c.responseCode) {
                200 -> {}
                404 -> throw Exception("Não encontrei as versões. Confira a fonte e se o repositório é público.")
                403 -> throw Exception("O GitHub limitou as consultas por agora. Tente de novo em alguns minutos.")
                else -> throw Exception("GitHub respondeu ${c.responseCode}. Tente mais tarde.")
            }
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private fun parse(json: JSONObject): Release {
        val tag = json.getString("tag_name")
        val assets = json.optJSONArray("assets") ?: JSONArray()
        var url: String? = null
        var digest: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                url = a.getString("browser_download_url")
                digest = a.optString("digest").takeIf { it.startsWith("sha256:") }
            }
        }
        return Release(
            build = numeroDaVersao(tag),
            name = json.optString("name", tag).ifBlank { tag },
            apkUrl = url,
            notas = json.optString("body", "").trim(),
            data = json.optString("published_at", "").take(10),
            digest = digest,
        )
    }

    suspend fun latest(repo: String): Release = withContext(Dispatchers.IO) {
        parse(JSONObject(get(api(repo) + "/latest")))
    }

    suspend fun historico(repo: String, quantas: Int = 15): List<Release> = withContext(Dispatchers.IO) {
        val arr = JSONArray(get(api(repo) + "?per_page=$quantas"))
        (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
    }

    suspend fun download(ctx: Context, url: String, progress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            require(url.startsWith("https://")) { "Download recusado: o endereço não usa HTTPS." }
            val file = File(ctx.cacheDir, "atualizacao.apk")
            var link = url
            var c: HttpURLConnection
            // Segue os redirecionamentos do GitHub até o arquivo (sempre HTTPS)
            var tentativas = 0
            while (true) {
                c = URL(link).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                val code = c.responseCode
                if (code in 300..399 && tentativas < 5) {
                    link = c.getHeaderField("Location") ?: break
                    c.disconnect()
                    if (!link.startsWith("https://")) throw Exception("Redirecionamento inseguro recusado.")
                    tentativas++
                    continue
                }
                if (code != 200) throw Exception("Falha ao baixar (código $code).")
                break
            }
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lido = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        lido += n
                        if (total > 0) withContext(Dispatchers.Main) { progress(lido.toFloat() / total) }
                    }
                }
            }
            c.disconnect()
            file
        }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun infoComAssinatura(pm: PackageManager, arquivo: String?, pkg: String): PackageInfo? {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        return if (arquivo != null) {
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(arquivo, PackageManager.PackageInfoFlags.of(flags.toLong()))
            else pm.getPackageArchiveInfo(arquivo, flags)
        } else {
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
            else pm.getPackageInfo(pkg, flags)
        }
    }

    private fun assinaturas(p: PackageInfo?): Set<String> =
        p?.signingInfo?.apkContentsSigners?.map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }?.toSet() ?: emptySet()

    /**
     * Confere integridade (SHA-256 publicado pelo GitHub) e autenticidade
     * (mesmo pacote e mesma assinatura do app instalado). Lança exceção se algo não bater.
     */
    suspend fun verificar(ctx: Context, file: File, digest: String?) = withContext(Dispatchers.IO) {
        if (digest != null) {
            val esperado = digest.removePrefix("sha256:").lowercase()
            if (sha256(file) != esperado) {
                file.delete()
                throw Exception("O arquivo baixado está corrompido (SHA-256 não confere). Tente de novo.")
            }
        }
        val pm = ctx.packageManager
        val novo = infoComAssinatura(pm, file.path, ctx.packageName)
            ?: throw Exception("O arquivo baixado não é um APK válido.")
        if (novo.packageName != ctx.packageName) throw Exception("O APK é de outro app (${novo.packageName}).")
        val atual = assinaturas(infoComAssinatura(pm, null, ctx.packageName))
        val dele = assinaturas(novo)
        if (atual.isNotEmpty() && dele != atual) {
            file.delete()
            throw Exception("A assinatura do APK é diferente da do app instalado. Instalação cancelada por segurança.")
        }
    }

    suspend fun install(ctx: Context, file: File) = withContext(Dispatchers.IO) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            // Sempre com a confirmação do Android
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("app.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                s.fsync(out)
            }
            val intent = Intent(ctx, InstallReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                ctx, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            s.commit(pi.intentSender)
        }
    }

    // ---------- verificação automática ----------

    private const val TRABALHO = "verificar-atualizacao"
    const val CANAL = "atualizacoes"

    /** Agenda (ou cancela, com 0 dias) a verificação automática. Roda só com internet. */
    fun agendar(ctx: Context, dias: Int) {
        val wm = WorkManager.getInstance(ctx)
        if (dias <= 0) {
            wm.cancelUniqueWork(TRABALHO)
            return
        }
        val req = PeriodicWorkRequestBuilder<VerificarAtualizacao>(dias.toLong(), TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(TRABALHO, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun avisar(ctx: Context, r: Release) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Atualizações", NotificationManager.IMPORTANCE_DEFAULT))
        val abrir = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).putExtra("aba", "atualizar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            7,
            NotificationCompat.Builder(ctx, CANAL)
                .setSmallIcon(R.drawable.ic_launcher_fg)
                .setContentTitle("Nova versão do D.I.A")
                .setContentText("${r.name} — toque para ver as novidades e atualizar")
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .build(),
        )
    }
}

/** Trabalho periódico: só consulta a versão e avisa. Nunca baixa nem instala sozinho. */
class VerificarAtualizacao(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        val cfg = Prefs.read(Prefs.get(applicationContext))
        val r = Updater.latest(cfg.fonteAtualizacao)
        if (r.build > Updater.installedBuild(applicationContext)) Updater.avisar(applicationContext, r)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

/** Recebe o resultado da instalação; abre a tela de confirmação do Android quando preciso. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmar: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmar?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirmar?.let { ctx.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> IslandBus.log("Atualização instalada")
            else -> IslandBus.log(
                "Atualização falhou: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "erro desconhecido"}",
            )
        }
    }
}

/** Depois de reiniciar: decide se a ilha volta ligada e reagenda a verificação de versões. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val p = Prefs.get(ctx)
        val cfg = Prefs.read(p)
        if (!cfg.ligarAoReiniciar && cfg.enabled) Prefs.write(p, cfg.copy(enabled = false))
        p.edit().putBoolean(Prefs.EDITANDO, false).apply()
        Updater.agendar(ctx, cfg.autoAtualizar)
    }
}
