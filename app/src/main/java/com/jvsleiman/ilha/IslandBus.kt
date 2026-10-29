package com.jvsleiman.ilha

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
)

/** Ponte entre o leitor de notificações e a ilha (os dois rodam no mesmo processo). */
object IslandBus {
    val music = MutableStateFlow<MusicInfo?>(null)
    val notifs = MutableSharedFlow<NotifInfo>(extraBufferCapacity = 8)

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
