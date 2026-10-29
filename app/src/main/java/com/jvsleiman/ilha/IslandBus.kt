package com.jvsleiman.ilha

import android.app.Notification
import android.app.PendingIntent
import android.graphics.Bitmap
import android.media.session.MediaController
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Música tocando agora (vem das sessões de mídia do Android). */
data class MusicInfo(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val accent: Int,
    val playing: Boolean,
    val position: Long,
    val positionTime: Long,
    val speed: Float,
    val duration: Long,
    val controller: MediaController?,
) {
    fun currentPosition(now: Long = SystemClock.elapsedRealtime()): Long {
        val p = if (playing) position + ((now - positionTime) * speed).toLong() else position
        return if (duration > 0) p.coerceIn(0, duration) else p.coerceAtLeast(0)
    }
}

/** Notificação nova que deve aparecer na ilha. */
data class NotifInfo(
    val key: String,
    val app: String,
    val title: String,
    val text: String,
    val icon: Bitmap?,
    val intent: PendingIntent?,
    /** Botões que o app oferece (ex.: Responder, Marcar como lida). */
    val actions: List<Notification.Action> = emptyList(),
    val pkg: String = "",
    /** Ícone do app (para o cabeçalho e o estado compacto). */
    val appIcon: Bitmap? = null,
    /** Cor do app (verde no WhatsApp). */
    val accent: Int = 0xFF0A84FF.toInt(),
    val time: Long = 0L,
)

/** Resposta em andamento: a tela de resposta lê daqui. */
data class ReplyRequest(val notif: NotifInfo, val action: Notification.Action)

/** Ponte entre o leitor de notificações e a ilha (os dois rodam no mesmo processo). */
object IslandBus {
    val music = MutableStateFlow<MusicInfo?>(null)
    val notifs = MutableSharedFlow<NotifInfo>(extraBufferCapacity = 8)
    val removidas = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Resposta que a tela de resposta deve enviar. */
    var reply: ReplyRequest? = null

    /** Notificações que acabaram de receber uma ação: não reaparecem por alguns segundos. */
    private val silenciadas = HashMap<String, Long>()

    fun silenciar(key: String, ms: Long = 5_000L) {
        silenciadas[key] = SystemClock.elapsedRealtime() + ms
    }

    fun silenciada(key: String): Boolean =
        (silenciadas[key] ?: 0L) > SystemClock.elapsedRealtime()

    // Diagnóstico (aparece na tela do app)
    val serviceOn = MutableStateFlow(false)
    val listenerOn = MutableStateFlow(false)
    val lastEvent = MutableStateFlow("Nada ainda")

    fun log(msg: String) {
        val hora = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        lastEvent.value = "$hora · $msg"
    }
}
