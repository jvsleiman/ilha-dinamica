package com.jvsleiman.ilha

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
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
import kotlinx.coroutines.launch

/**
 * Lê as sessões de mídia (música) e as notificações novas e repassa para a ilha.
 */
class NotificationWatcher : NotificationListenerService() {

    private var msm: MediaSessionManager? = null
    private val scope = kotlinx.coroutines.MainScope()
    private var cancelJob: kotlinx.coroutines.Job? = null
    private var current: MediaController? = null
    private val main = Handler(Looper.getMainLooper())
    private val lastText = HashMap<String, String>()
    private val cn by lazy { ComponentName(this, NotificationWatcher::class.java) }

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
    }

    override fun onListenerDisconnected() {
        msm?.removeOnActiveSessionsChangedListener(sessionsListener)
        current?.unregisterCallback(callback)
        current = null
        IslandBus.music.value = null
        IslandBus.listenerOn.value = false
        cancelJob?.cancel()
        IslandBus.log("Leitor de notificações desconectado")
        super.onListenerDisconnected()
    }

    private fun refreshSessions() {
        val m = msm ?: return
        try {
            pick(m.getActiveSessions(cn))
        } catch (e: SecurityException) {
            publish()
        }
    }

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.sessionToken == current?.sessionToken }
            ?: list.firstOrNull()
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
        if (c == null || md == null) {
            if (c != null) IslandBus.log("Música: ${c.packageName} sem informações da faixa")
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

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val n = sbn.notification ?: return
        val pkg = sbn.packageName
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return
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

        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        if (IslandBus.silenciada(sbn.key)) return
        val text = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: n.extras.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString()

        // Atualizações da mesma notificação sem texto novo não aparecem de novo
        val sig = "$title|$text"
        if (lastText[sbn.key] == sig) return
        lastText[sbn.key] = sig

        val pm = packageManager
        val app = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
        val appIcon = try {
            pm.getApplicationIcon(pkg).toBitmap(64, 64)
        } catch (e: Exception) {
            null
        }
        // Foto da pessoa (se o app mandar); senão, o ícone do app
        val icon = try {
            n.getLargeIcon()?.loadDrawable(this)?.toBitmap(112, 112) ?: appIcon
        } catch (e: Exception) {
            appIcon
        }

        IslandBus.log("Notificação enviada à ilha: $app")
        val acoes = n.actions?.filter { it.actionIntent != null && !it.title.isNullOrBlank() }
            ?.take(3) ?: emptyList()
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
