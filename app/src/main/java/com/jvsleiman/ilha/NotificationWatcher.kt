package com.jvsleiman.ilha

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lê as sessões de mídia (música), as notificações novas e as notificações "em andamento"
 * (chamadas, temporizadores, navegação, downloads) e repassa para a ilha.
 * Tudo é processado no aparelho; nada é enviado para fora.
 */
class NotificationWatcher : NotificationListenerService() {

    private var msm: MediaSessionManager? = null
    private val scope = kotlinx.coroutines.MainScope()
    private var cancelJob: kotlinx.coroutines.Job? = null
    private var current: MediaController? = null
    private val main = Handler(Looper.getMainLooper())
    private val lastText = HashMap<String, String>()
    private val iconesApp = HashMap<String, Bitmap?>()
    private val nomesApp = HashMap<String, String>()
    private val cn by lazy { ComponentName(this, NotificationWatcher::class.java) }
    private lateinit var prefs: SharedPreferences
    private var cfg = Config()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { p, _ ->
        val antes = cfg
        cfg = Prefs.read(p)
        if (antes.musica != cfg.musica) publish()
        if (antes.chamadas != cfg.chamadas || antes.atividades != cfg.atividades || antes.timer != cfg.timer) varrerAtivas()
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list ?: emptyList()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Se esta pausou e outra começou a tocar, troca para a outra
            if (state?.state != PlaybackState.STATE_PLAYING) refreshSessions() else publish()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()

        override fun onSessionDestroyed() {
            current = null
            refreshSessions()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        prefs = Prefs.get(this)
        cfg = Prefs.read(prefs)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        val m = getSystemService(MediaSessionManager::class.java)
        msm = m
        IslandBus.listenerOn.value = true
        if (cancelJob?.isActive != true) {
            cancelJob = scope.launch {
                IslandBus.cancelar.collect { key ->
                    try { cancelNotification(key) } catch (e: Exception) { IslandBus.log("Não consegui apagar: ${e.message}") }
                }
            }
        }
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
            val list = m.getActiveSessions(cn)
            IslandBus.log("Leitor conectado · ${list.size} sessão(ões) de mídia")
            pick(list)
        } catch (e: SecurityException) {
            IslandBus.log("Sem permissão para ler músicas: ${e.message}")
            IslandBus.music.value = null
        }
        varrerAtivas()
    }

    override fun onListenerDisconnected() {
        msm?.removeOnActiveSessionsChangedListener(sessionsListener)
        current?.unregisterCallback(callback)
        current = null
        IslandBus.music.value = null
        IslandBus.lives.value = emptyMap()
        IslandBus.listenerOn.value = false
        cancelJob?.cancel()
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        IslandBus.log("Leitor de notificações desconectado")
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun bloqueado(pkg: String) = ::prefs.isInitialized && pkg in Prefs.bloqueados(prefs)

    // ---------- música ----------

    private fun refreshSessions() {
        val m = msm ?: return
        try {
            pick(m.getActiveSessions(cn))
        } catch (e: SecurityException) {
            publish()
        }
    }

    private fun pick(list: List<MediaController>) {
        val validos = list.filter { !bloqueado(it.packageName) }
        val best = validos.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: validos.firstOrNull { it.sessionToken == current?.sessionToken }
            ?: validos.firstOrNull()
        if (best?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            current = best
            best?.registerCallback(callback, main)
            IslandBus.log("Música: acompanhando ${best?.packageName ?: "nenhum app"}")
        }
        publish()
    }

    private fun publish() {
        val c = current
        val md = c?.metadata
        if (!cfg.musica || c == null || md == null) {
            IslandBus.music.value = null
            return
        }
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank()) {
            IslandBus.log("Música: ${c.packageName} sem título")
            IslandBus.music.value = null
            return
        }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val prev = IslandBus.music.value
        val accent = if (prev != null && art != null && art === prev.art) prev.accent else dominantColor(art)
        val st = c.playbackState

        IslandBus.music.value = MusicInfo(
            title = title,
            artist = artist,
            art = art,
            accent = accent,
            playing = st?.state == PlaybackState.STATE_PLAYING,
            position = st?.position ?: 0L,
            positionTime = st?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            controller = c,
            pkg = c.packageName,
        )
    }

    /** Cor da capa, clareada, para a ondinha animada. */
    private fun dominantColor(bmp: Bitmap?): Int {
        val fallback = 0xFFFF9F7A.toInt()
        if (bmp == null) return fallback
        return try {
            val src = if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
            val px = Bitmap.createScaledBitmap(src, 1, 1, true).getPixel(0, 0)
            val hsv = FloatArray(3)
            Color.colorToHSV(px, hsv)
            hsv[1] = hsv[1].coerceIn(0.35f, 0.8f)
            hsv[2] = hsv[2].coerceAtLeast(0.85f)
            Color.HSVToColor(hsv)
        } catch (e: Exception) {
            fallback
        }
    }

    // ---------- apps ----------

    private fun nomeApp(pkg: String): String = nomesApp.getOrPut(pkg) {
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun iconeApp(pkg: String): Bitmap? = iconesApp.getOrPut(pkg) {
        try { packageManager.getApplicationIcon(pkg).toBitmap(64, 64) } catch (e: Exception) { null }
    }

    private fun ignorarPacote(pkg: String) = pkg == packageName || pkg == "android" || pkg == "com.android.systemui"

    // ---------- atividades contínuas ----------

    /** Relê todas as notificações ativas (ao conectar e quando as integrações mudam). */
    private fun varrerAtivas() {
        val todas = try { activeNotifications ?: emptyArray() } catch (e: Exception) { emptyArray() }
        val mapa = HashMap<String, LiveInfo>()
        for (sbn in todas) {
            if (ignorarPacote(sbn.packageName) || bloqueado(sbn.packageName)) continue
            paraLive(sbn)?.let { mapa[sbn.key] = it }
        }
        IslandBus.lives.value = mapa
    }

    /** Transforma uma notificação em atividade contínua, se ela for uma. */
    private fun paraLive(sbn: StatusBarNotification): LiveInfo? {
        val n = sbn.notification ?: return null
        val ex = n.extras
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION) || n.category == Notification.CATEGORY_TRANSPORT) return null
        val cat = n.category
        val chamada = cat == Notification.CATEGORY_CALL
        if (!chamada && !sbn.isOngoing) return null

        val cronometro = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val regressivo = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val atual = ex.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminado = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val temProgresso = max > 0 || indeterminado

        val tipo = when {
            chamada -> Tipo.CHAMADA
            cat == "stopwatch" || cat == Notification.CATEGORY_ALARM && cronometro -> Tipo.TIMER
            cronometro && regressivo -> Tipo.TIMER
            cat == "navigation" -> Tipo.ATIVIDADE
            cronometro -> Tipo.ATIVIDADE // gravação, compartilhamento de tela...
            temProgresso -> Tipo.ATIVIDADE
            else -> return null
        }
        when (tipo) {
            Tipo.CHAMADA -> if (!cfg.chamadas) return null
            Tipo.TIMER -> if (!cfg.timer) return null
            else -> if (!cfg.atividades) return null
        }

        val title = (ex.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString()
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString()
        // Android 12+: o próprio app diz se a chamada está tocando (1) ou em andamento (2)
        val tipoChamada = ex.getInt("android.callType", 0)
        val recebendo = chamada && when (tipoChamada) {
            1, 3 -> true
            2 -> false
            else -> !cronometro && n.fullScreenIntent != null
        }
        val appIcon = iconeApp(sbn.packageName)
        val foto = if (chamada) {
            try { n.getLargeIcon()?.loadDrawable(this)?.toBitmap(112, 112) } catch (e: Exception) { null }
        } else null

        return LiveInfo(
            key = sbn.key,
            tipo = tipo,
            pkg = sbn.packageName,
            app = nomeApp(sbn.packageName),
            title = title,
            text = text,
            icon = foto ?: appIcon,
            appIcon = appIcon,
            accent = when (tipo) {
                Tipo.CHAMADA -> 0xFF34C759.toInt()
                Tipo.TIMER -> 0xFFFF9F0A.toInt()
                else -> corDoApp(sbn.packageName, n.color)
            },
            chronoBase = if (cronometro) n.`when` else 0L,
            countDown = regressivo,
            progresso = if (max > 0 && !indeterminado) (atual.toFloat() / max).coerceIn(0f, 1f) else null,
            indeterminado = indeterminado,
            recebendo = recebendo,
            intent = n.contentIntent,
            actions = n.actions?.filter { it.actionIntent != null && !it.title.isNullOrBlank() }?.take(3) ?: emptyList(),
            postTime = sbn.postTime,
        )
    }

    // ---------- notificações ----------

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val n = sbn.notification ?: return
        val pkg = sbn.packageName
        if (ignorarPacote(pkg)) return
        if (::prefs.isInitialized) Prefs.marcarVisto(prefs, pkg)
        if (bloqueado(pkg)) return

        // Chamadas e atividades em andamento vão para o gerenciador de atividades
        val live = paraLive(sbn)
        if (live != null) {
            IslandBus.lives.value = IslandBus.lives.value + (sbn.key to live)
            return
        } else if (IslandBus.lives.value.containsKey(sbn.key)) {
            IslandBus.lives.value = IslandBus.lives.value - sbn.key
        }

        if (!cfg.notificacoes) return
        if (sbn.isOngoing) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category == Notification.CATEGORY_TRANSPORT) return
        if (n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return

        // Ignora notificações silenciosas
        val ranking = Ranking()
        if (currentRanking?.getRanking(sbn.key, ranking) == true &&
            ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
        ) {
            IslandBus.log("Notificação silenciosa ignorada ($pkg)")
            return
        }

        var title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        if (IslandBus.silenciada(sbn.key)) return
        var text = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: n.extras.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString()

        // Atualizações da mesma notificação sem texto novo não aparecem de novo
        val sig = "$title|$text"
        if (lastText[sbn.key] == sig) return
        lastText[sbn.key] = sig

        val app = nomeApp(pkg)
        val appIcon = iconeApp(pkg)
        var icon = try {
            n.getLargeIcon()?.loadDrawable(this)?.toBitmap(112, 112) ?: appIcon
        } catch (e: Exception) {
            appIcon
        }
        var acoes = n.actions?.filter { it.actionIntent != null && !it.title.isNullOrBlank() }
            ?.take(3) ?: emptyList()

        // Privacidade na tela bloqueada: respeita a visibilidade que o app pediu
        val bloqueada = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if (bloqueada) {
            if (n.visibility == Notification.VISIBILITY_SECRET) return
            if (cfg.privacidade || n.visibility == Notification.VISIBILITY_PRIVATE) {
                val pub = n.publicVersion
                title = pub?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: app
                text = pub?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "Desbloqueie para ver"
                icon = appIcon
                acoes = emptyList()
            }
        }

        IslandBus.log("Notificação enviada à ilha: $app")
        IslandBus.notifs.tryEmit(
            NotifInfo(
                sbn.key, app, title, text, icon, n.contentIntent, acoes,
                pkg = pkg, appIcon = appIcon, accent = corDoApp(pkg, n.color), time = sbn.postTime,
            ),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        lastText.remove(sbn.key)
        if (IslandBus.lives.value.containsKey(sbn.key)) {
            IslandBus.lives.value = IslandBus.lives.value - sbn.key
        }
        IslandBus.removidas.tryEmit(sbn.key)
    }

    /** Cor de destaque do app: a do WhatsApp é fixa; nos outros, a cor da notificação clareada. */
    private fun corDoApp(pkg: String, cor: Int): Int {
        if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") return 0xFF25D366.toInt()
        if (cor == 0 || Color.alpha(cor) == 0) return 0xFF0A84FF.toInt()
        val hsv = FloatArray(3)
        Color.colorToHSV(cor, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.45f)
        hsv[2] = hsv[2].coerceAtLeast(0.85f)
        return Color.HSVToColor(hsv)
    }
}
