package com.jvsleiman.ilha

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualização dentro do app: lê a última versão publicada no GitHub,
 * baixa o APK e instala por cima (mesma assinatura).
 */
object Updater {
    private const val API =
        "https://api.github.com/repos/jvsleiman/ilha-dinamica/releases/latest"

    data class Release(val build: Long, val name: String, val apkUrl: String)

    fun installedBuild(ctx: Context): Long =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val c = URL(API).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        try {
            when (c.responseCode) {
                200 -> {}
                404 -> throw Exception("Não encontrei as versões. O repositório precisa estar público.")
                else -> throw Exception("GitHub respondeu ${c.responseCode}. Tente mais tarde.")
            }
            val json = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = json.getString("tag_name")
            // As versões são marcadas como v0.1.<número da compilação>
            val build = Regex("(\\d+)$").find(tag)?.value?.toLong() ?: 0L
            val assets = json.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
            }
            Release(build, json.optString("name", tag), url ?: throw Exception("A versão não tem APK."))
        } finally {
            c.disconnect()
        }
    }

    suspend fun download(ctx: Context, url: String, progress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val file = File(ctx.cacheDir, "atualizacao.apk")
            var link = url
            var c: HttpURLConnection
            // Segue os redirecionamentos do GitHub até o arquivo
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

    suspend fun install(ctx: Context, file: File) = withContext(Dispatchers.IO) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            // Depois da primeira atualização feita pelo próprio app, as próximas podem dispensar confirmação
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
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
