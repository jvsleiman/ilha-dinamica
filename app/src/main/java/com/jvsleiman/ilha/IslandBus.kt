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
    val pkg: String = "",
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

/**
 * Atividade contínua: chamada, temporizador, cronômetro, navegação, download, gravação...
 * Vem de notificações "em andamento" de outros apps (dados reais publicados por eles)
 * ou do temporizador do próprio D.I.A.
 */
data class LiveInfo(
    val key: String,
    val tipo: Tipo,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val icon: Bitmap?,
    val appIcon: Bitmap?,
    val accent: Int,
    /** Base do cronômetro em hora do relógio (ms). 0 = sem cronômetro. */
    val chronoBase: Long = 0L,
    val countDown: Boolean = false,
    /** Progresso 0..1, se o app informa. */
    val progresso: Float? = null,
    val indeterminado: Boolean = false,
    /** Chamada tocando (ainda não atendida). */
    val recebendo: Boolean = false,
    val intent: PendingIntent? = null,
    val actions: List<Notification.Action> = emptyList(),
    val postTime: Long = 0L,
    /** Temporizador do próprio app (ações tratadas internamente). */
    val proprio: TimerProprio? = null,
) {
    /** Texto curto do lado direito da ilha compacta. */
    fun textoCurto(agoraParede: Long = System.currentTimeMillis(), agoraMono: Long = SystemClock.elapsedRealtime()): String {
        proprio?.let { return it.texto(agoraMono) }
        if (chronoBase > 0L) return relogio(if (countDown) chronoBase - agoraParede + 999 else agoraParede - chronoBase)
        progresso?.let { return "${(it * 100).toInt()}%" }
        return ""
    }

    fun progressoAgora(agoraMono: Long = SystemClock.elapsedRealtime()): Float? =
        proprio?.let { if (it.cronometro) null else it.progresso(agoraMono) } ?: progresso

    /** Precisa redesenhar a cada segundo? */
    val tiqueTaque get() = chronoBase > 0L || proprio != null
}

/** Resposta em andamento: a tela de resposta lê daqui. */
data class ReplyRequest(val notif: NotifInfo, val action: Notification.Action)

/** Ponte entre o leitor de notificações, o app e a ilha (todos rodam no mesmo processo). */
object IslandBus {
    val music = MutableStateFlow<MusicInfo?>(null)
    val notifs = MutableSharedFlow<NotifInfo>(extraBufferCapacity = 8)
    val removidas = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Pedidos da ilha para apagar uma notificação (o leitor de notificações executa). */
    val cancelar = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Atividades contínuas de outros apps, por chave da notificação. */
    val lives = MutableStateFlow<Map<String, LiveInfo>>(emptyMap())
    /** Temporizador/cronômetro do próprio app. */
    val timer = MutableStateFlow<TimerProprio?>(null)
    /** Pedidos de demonstração vindos da tela "Testar animação". */
    val demo = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Resposta que a tela de resposta deve enviar. */
    var reply: ReplyRequest? = null

    /** Notificações que acabaram de receber uma ação: não reaparecem por alguns segundos. */
    private val silenciadas = HashMap<String, Long>()

    fun silenciar(key: String, ms: Long = 5_000L) {
        silenciadas[key] = SystemClock.elapsedRealtime() + ms
    }

    fun silenciada(key: String): Boolean =
        (silenciadas[key] ?: 0L) > SystemClock.elapsedRealtime()

    // ---------- temporizador próprio ----------

    fun iniciarTimer(ms: Long) { timer.value = TimerProprio.temporizador(ms, SystemClock.elapsedRealtime()) }
    fun iniciarCronometro() { timer.value = TimerProprio.cronometro(SystemClock.elapsedRealtime()) }
    fun pausarOuRetomar() {
        val t = timer.value ?: return
        val agora = SystemClock.elapsedRealtime()
        timer.value = if (t.pausado) t.retomar(agora) else t.pausar(agora)
    }
    fun cancelarTimer() { timer.value = null }

    // ---------- diagnóstico (aparece na tela do app) ----------
    val serviceOn = MutableStateFlow(false)
    val listenerOn = MutableStateFlow(false)
    val lastEvent = MutableStateFlow("Nada ainda")
    /** Geometria detectada da tela, para a tela de calibração. */
    val tela = MutableStateFlow("Ainda não detectada (ative a ilha)")
    /** Se a ilha está visível agora e, se não, por quê. */
    val visibilidade = MutableStateFlow("Serviço desligado")

    fun log(msg: String) {
        val hora = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        lastEvent.value = "$hora · $msg"
    }
}
