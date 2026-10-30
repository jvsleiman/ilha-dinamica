package com.jvsleiman.ilha

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Formas da ilha.
 * NOTIF = faixa larga "Nome (1)  Ver"; NOTIF_X = cartão aberto com botões;
 * NOTIF_C = só o ícone do app ao lado da câmera (mensagem pendente);
 * LIVE_C/LIVE_X = atividade contínua compacta/expandida; CALL_IN = chamada tocando;
 * ALERTA = aviso rápido (carregador, bateria, temporizador concluído).
 */
enum class Modo { IDLE, MUSIC_C, MUSIC_X, NOTIF, NOTIF_X, NOTIF_C, LIVE_C, LIVE_X, CALL_IN, ALERTA }

private enum class Fase { FAIXA, ABERTA, PENDENTE }

enum class AlertaTipo { CARREGANDO, CHEIA, BAIXA, TIMER }

/** Aviso rápido na ilha. */
data class Alerta(val tipo: AlertaTipo, val titulo: String, val valor: String, val cor: Int, val nivel: Float = 0f)

/** O que aparece na bolinha da ilha dividida: uma notificação de passagem ou a segunda atividade. */
data class Bolha(
    val key: String,
    val icone: Bitmap?,
    val cor: Int,
    val contagem: Int = 1,
    val live: LiveInfo? = null,
    val musica: MusicInfo? = null,
)

/** Uma atividade que pode ocupar a ilha (música ou atividade contínua). */
private sealed class Ativ {
    abstract val key: String
    abstract val tipo: Tipo
    abstract val recencia: Long

    data class Musica(val m: MusicInfo) : Ativ() {
        override val key = "musica"
        override val tipo = Tipo.MUSICA
        override val recencia = 0L
    }

    data class Viva(val l: LiveInfo) : Ativ() {
        override val key = l.key
        override val tipo = l.tipo
        override val recencia = l.postTime
    }
}

/** Tudo que a ilha precisa para se desenhar. Tamanhos em dp. */
data class UiState(
    val modo: Modo = Modo.IDLE,
    val w: Float = 100f,
    val h: Float = 30f,
    val r: Float = 15f,
    val music: MusicInfo? = null,
    val notif: NotifInfo? = null,
    val live: LiveInfo? = null,
    val alerta: Alerta? = null,
    /** Centro horizontal da câmera, em pixels da tela. */
    val cx: Float = 0f,
    /** Faixa livre no topo das formas expandidas (a altura da ilha parada + respiro), em dp. */
    val topo: Float = 38f,
    /** Topo da forma, em pixels da tela (sobe quando expande). */
    val y: Float = 0f,
    /** Quantas mensagens novas do mesmo app. */
    val contagem: Int = 0,
    val acima: Float = 8f,
    val alturaCamera: Float = 30f,
    /** Ilha dividida: largura só da parte principal e deslocamento dela (dp). Sem divisão, wMain = w e dx = 0. */
    val wMain: Float = 100f,
    val dx: Float = 0f,
    val bolha: Bolha? = null,
    /** Quantas atividades estão rodando (para os pontinhos de "deslize para trocar"). */
    val atividades: Int = 0,
    // aparência e animação
    val amortecimento: Float = 0.82f,
    val rigidez: Float = 260f,
    val opacidade: Float = 1f,
    val escalaIcones: Float = 1f,
    val tamanhoTexto: Float = 1f,
    val animOnda: Boolean = true,
    val animBolha: Boolean = true,
    val editando: Boolean = false,
)

/** Espaço entre a ilha principal e a bolinha, em dp. */
const val ESPACO_BOLHA = 8f
/** Quanto tempo a bolinha de notificação fica antes de a ilha voltar a ficar inteira. */
private const val BOLHA_MS = 4_000L

/** Margem interna padrão das formas expandidas: laterais e base iguais, em dp. */
const val MARGEM_INTERNA = 20f
fun Modo.expandido() = this == Modo.MUSIC_X || this == Modo.NOTIF_X || this == Modo.LIVE_X || this == Modo.CALL_IN
fun Modo.compactoComAtividade() = this == Modo.MUSIC_C || this == Modo.LIVE_C

/** Altura da maior forma da ilha, em dp. */
const val MAX_H = 300f

/** Quanto tempo a música pausada continua na ilha. */
private const val PAUSA_VISIVEL_MS = 30_000L
/** Quanto tempo o ícone de mensagem fica ao lado da câmera antes de a ilha voltar ao normal. */
private const val PENDENTE_MS = 2_000L
private const val ALERTA_MS = 2_600L

class IslandService : AccessibilityService() {

    private var wm: WindowManager? = null
    // Janela de desenho: tamanho fixo, não recebe toques (passam para o que está embaixo).
    private var view: ComposeView? = null
    private var lp: WindowManager.LayoutParams? = null
    // Janela de toque: invisível, do tamanho exato da ilha; repassa os toques ao desenho.
    private var touch: View? = null
    private var touchLp: WindowManager.LayoutParams? = null
    // Sonda: janela de 1 px que só serve para saber se a barra de status está visível (tela cheia).
    private var sonda: View? = null
    private val owner = OverlayOwner()
    private lateinit var prefs: SharedPreferences

    private var cfg = Config()
    private var editando = false
    private var arrastoX = 0f
    private var arrastoY = 0f
    private var telaCheia = false
    private var expanded = false
    private var collapseJob: Job? = null
    private var music: MusicInfo? = null
    private var musicVisible = false
    private var hideMusicJob: Job? = null
    private var lives: Map<String, LiveInfo> = emptyMap()
    private var timer: TimerProprio? = null
    private var timerJob: Job? = null
    /** Atividade que o usuário escolheu deslizando (fica na ilha principal). */
    private var escolhida: String? = null
    /** Atividades que o usuário tirou da ilha (voltam quando a notificação muda de chave). */
    private val ocultas = HashSet<String>()
    /** Chamadas tocando que o usuário encolheu. */
    private val minimizadas = HashSet<String>()
    private var notif: NotifInfo? = null
    private var fase = Fase.FAIXA
    /** Quantas mensagens chegaram do mesmo app desde a última vez que você viu. */
    private var contagem = 0
    private var notifJob: Job? = null
    private var bolhaNotif: NotifInfo? = null
    private var bolhaContagem = 0
    private var bolhaJob: Job? = null
    private var alerta: Alerta? = null
    private var alertaJob: Job? = null
    private var ultimoNivel = -1
    private var carregando = false

    private val ui = mutableStateOf(UiState())

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            Prefs.EDITANDO -> editando = p.getBoolean(Prefs.EDITANDO, false)
            Prefs.REDETECTAR -> {
                expanded = false
                relatarTela()
            }
            Prefs.BLOQUEADOS, Prefs.VISTOS, "favoritos" -> return@OnSharedPreferenceChangeListener
            else -> cfg = Prefs.read(p)
        }
        refresh()
    }

    private val bateriaReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = onBateria(i)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onServiceConnected() {
        super.onServiceConnected()
        val manager = getSystemService(WindowManager::class.java)
        wm = manager
        prefs = Prefs.get(this)
        cfg = Prefs.read(prefs)
        editando = prefs.getBoolean(Prefs.EDITANDO, false)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        owner.start()
        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                Island(
                    ui = ui.value,
                    onTap = { tocar() },
                    onLongPress = { segurar() },
                    onAction = { acao(it) },
                    onSwipe = { deslizar(it) },
                    onFechar = { fechar() },
                )
            }
        }
        val t = View(this).apply {
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE) {
                    tocouFora()
                    return@setOnTouchListener false
                }
                // Modo edição: arrastar move a ilha (coordenadas da tela, que não mudam quando a janela anda)
                if (editando) {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { arrastoX = e.rawX; arrastoY = e.rawY }
                        MotionEvent.ACTION_MOVE -> {
                            mover(e.rawX - arrastoX, e.rawY - arrastoY, false)
                            arrastoX = e.rawX; arrastoY = e.rawY
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mover(0f, 0f, true)
                    }
                    return@setOnTouchListener true
                }
                // Converte a posição do toque para a janela de desenho e entrega ao Compose
                val tl = touchLp
                val dl = lp
                if (tl != null && dl != null) {
                    val ev = MotionEvent.obtain(e)
                    ev.offsetLocation((tl.x - dl.x).toFloat(), (tl.y - dl.y).toFloat())
                    view?.dispatchTouchEvent(ev)
                    ev.recycle()
                }
                true
            }
        }
        val s = View(this).apply {
            setOnApplyWindowInsetsListener { _, insets ->
                // Só conta como tela cheia se a barra de status existe para esta janela e está escondida.
                // (Algumas janelas sobrepostas recebem a barra como "invisível" sempre — isso escondia a ilha.)
                val tipo = WindowInsets.Type.statusBars()
                val existe = insets.getInsetsIgnoringVisibility(tipo).top > 0
                val cheia = existe && !insets.isVisible(tipo)
                if (cheia != telaCheia) {
                    telaCheia = cheia
                    refresh()
                }
                insets
            }
        }

        fun base(flags: Int) = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        view = v
        lp = base(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply { title = "Ilha" }
        touch = t
        touchLp = base(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH).apply { title = "Ilha toque" }
        sonda = s
        val sondaLp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            title = "Ilha sonda"
        }
        ui.value = computeUi()
        computeWindows()
        manager.addView(v, lp)
        manager.addView(t, touchLp)
        try { manager.addView(s, sondaLp) } catch (e: Exception) { sonda = null }
        IslandBus.serviceOn.value = true
        IslandBus.log("Ilha conectada")
        relatarTela()

        try { ContextCompat.registerReceiver(
            this, bateriaReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        ) } catch (e: Exception) { IslandBus.log("Sem avisos de bateria: ${e.message}") }

        owner.lifecycleScope.launch { IslandBus.music.collect { m -> onMusic(m) } }
        owner.lifecycleScope.launch { IslandBus.notifs.collect { n -> onNotif(n) } }
        owner.lifecycleScope.launch {
            IslandBus.lives.collect { l ->
                lives = l
                ocultas.retainAll(l.keys)
                minimizadas.retainAll(l.keys)
                refresh()
            }
        }
        owner.lifecycleScope.launch { IslandBus.timer.collect { t -> onTimer(t) } }
        owner.lifecycleScope.launch { IslandBus.demo.collect { d -> demo(d) } }
        owner.lifecycleScope.launch {
            // Lida em outro lugar (ou apagada): tira da ilha
            IslandBus.removidas.collect { key ->
                if (bolhaNotif?.key == key) {
                    bolhaJob?.cancel()
                    bolhaNotif = null
                    refresh()
                }
                if (notif?.key == key && fase != Fase.ABERTA) fecharNotif()
            }
        }
    }

    // ---------- atividades ----------

    /** Atividades rodando agora, na ordem de prioridade (a primeira fica na ilha principal). */
    private fun ativas(): List<Ativ> {
        val itens = ArrayList<Ativ>()
        val m = music
        if (cfg.musica && musicVisible && m != null) itens.add(Ativ.Musica(m))
        for (l in lives.values) {
            if (l.key in ocultas) continue
            val ok = when (l.tipo) {
                Tipo.CHAMADA -> cfg.chamadas
                Tipo.TIMER -> cfg.timer
                else -> cfg.atividades
            }
            if (ok) itens.add(Ativ.Viva(l))
        }
        val t = timer
        if (t != null && cfg.timer && "proprio" !in ocultas) itens.add(Ativ.Viva(timerLive(t)))

        var ordem = ordenarPorPrioridade(itens, cfg.prioridades, { it.tipo }, { it.recencia })
        // Escolha do usuário (deslizando) vai para a frente...
        val e = escolhida
        if (e != null) {
            val i = ordem.indexOfFirst { it.key == e }
            if (i > 0) ordem = listOf(ordem[i]) + ordem.filterIndexed { j, _ -> j != i }
            if (i < 0) escolhida = null
        }
        // ...mas chamada tocando sempre vem primeiro
        return ordem.sortedByDescending { it is Ativ.Viva && it.l.recebendo && it.l.key !in minimizadas }
    }

    private fun timerLive(t: TimerProprio) = LiveInfo(
        key = "proprio",
        tipo = Tipo.TIMER,
        pkg = packageName,
        app = "D.I.A",
        title = if (t.cronometro) "Cronômetro" else "Temporizador",
        text = if (t.pausado) "Pausado" else "",
        icon = null,
        appIcon = null,
        accent = 0xFFFF9F0A.toInt(),
        proprio = t,
        postTime = Long.MAX_VALUE,
    )

    private fun onTimer(t: TimerProprio?) {
        timer = t
        timerJob?.cancel()
        if (t != null && !t.cronometro && !t.pausado) {
            val restante = t.restante(SystemClock.elapsedRealtime())
            timerJob = owner.lifecycleScope.launch {
                delay(restante)
                if (IslandBus.timer.value === t) timerAcabou()
            }
        }
        if (t == null) ocultas.remove("proprio")
        refresh()
    }

    private fun timerAcabou() {
        IslandBus.timer.value = null
        IslandBus.log("Temporizador concluído")
        mostrarAlerta(Alerta(AlertaTipo.TIMER, "Temporizador", "Concluído", 0xFFFF9F0A.toInt()), 6_000L)
        vibrar(longArrayOf(0, 300, 150, 300, 150, 500))
        try {
            RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        } catch (e: Exception) { }
    }

    private fun vibrar(padrao: LongArray) {
        try {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            v.vibrate(VibrationEffect.createWaveform(padrao, -1))
        } catch (e: Exception) { }
    }

    // ---------- bateria ----------

    private fun onBateria(i: Intent) {
        if (!cfg.bateria) return
        val nivel = nivelBateria(i)
        when (i.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                val n = if (nivel >= 0) nivel else nivelBateria(registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
                mostrarAlerta(Alerta(AlertaTipo.CARREGANDO, "Carregando", "$n%", 0xFF34C759.toInt(), n / 100f))
            }
            Intent.ACTION_BATTERY_LOW -> {
                val n = if (nivel >= 0) nivel else ultimoNivel
                mostrarAlerta(Alerta(AlertaTipo.BAIXA, "Bateria fraca", "$n%", 0xFFFF453A.toInt(), n / 100f), 4_000L)
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val ligado = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                val cheia = status == BatteryManager.BATTERY_STATUS_FULL || nivel >= 100
                if (ligado && cheia && ultimoNivel in 0..99) {
                    mostrarAlerta(Alerta(AlertaTipo.CHEIA, "Carga completa", "100%", 0xFF34C759.toInt(), 1f), 3_500L)
                }
                carregando = ligado
                if (nivel >= 0) ultimoNivel = nivel
            }
        }
    }

    private fun nivelBateria(i: Intent?): Int {
        i ?: return -1
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (l >= 0 && s > 0) (l * 100 / s) else -1
    }

    private fun mostrarAlerta(a: Alerta, ms: Long = ALERTA_MS) {
        alerta = a
        alertaJob?.cancel()
        alertaJob = owner.lifecycleScope.launch {
            delay(ms)
            alerta = null
            refresh()
        }
        refresh()
    }

    // ---------- música e notificações ----------

    private fun onMusic(m: MusicInfo?) {
        music = m
        when {
            m == null -> {
                hideMusicJob?.cancel()
                musicVisible = false
            }
            m.playing -> {
                hideMusicJob?.cancel()
                hideMusicJob = null
                musicVisible = true
            }
            musicVisible && hideMusicJob?.isActive != true -> {
                hideMusicJob = owner.lifecycleScope.launch {
                    delay(PAUSA_VISIVEL_MS)
                    musicVisible = false
                    refresh()
                }
            }
        }
        refresh()
    }

    private fun onNotif(n: NotifInfo) {
        // Atividade na ilha: a notificação vai para a bolinha ao lado, sem interromper a atividade
        if (ativas().isNotEmpty() && notif == null) {
            bolhaContagem = if (bolhaNotif?.pkg == n.pkg) bolhaContagem + 1 else 1
            bolhaNotif = n
            bolhaJob?.cancel()
            bolhaJob = owner.lifecycleScope.launch {
                delay(BOLHA_MS)
                bolhaNotif = null
                refresh()
            }
            refresh()
            return
        }
        contagem = if (notif?.pkg == n.pkg) contagem + 1 else 1
        notif = n
        fase = Fase.FAIXA
        depois((cfg.tempoNotif * 1000).toLong()) { pendente() }
        refresh()
    }

    private fun depois(ms: Long, bloco: () -> Unit) {
        notifJob?.cancel()
        notifJob = owner.lifecycleScope.launch {
            delay(ms)
            bloco()
        }
    }

    /** Encolhe para o ícone do app ao lado da câmera; some sozinho depois de um tempo. */
    private fun pendente() {
        if (notif == null) return
        fase = Fase.PENDENTE
        depois(PENDENTE_MS) { fecharNotif() }
        refresh()
    }

    private fun fecharNotif() {
        notifJob?.cancel()
        notif = null
        fase = Fase.FAIXA
        contagem = 0
        refresh()
    }

    private fun expandir(sim: Boolean) {
        expanded = sim
        collapseJob?.cancel()
        if (sim) {
            collapseJob = owner.lifecycleScope.launch {
                delay((cfg.tempoExpandida * 1000).toLong())
                expanded = false
                refresh()
            }
        }
    }

    // ---------- gestos ----------

    private fun tocouFora() {
        if (notif != null && fase == Fase.ABERTA) {
            pendente()
        } else if (expanded) {
            expandir(false)
            refresh()
        }
    }

    private fun primeiraLive(): LiveInfo? = (ativas().firstOrNull() as? Ativ.Viva)?.l

    private fun tocar() {
        when (ui.value.modo) {
            Modo.NOTIF, Modo.NOTIF_C -> {
                // Abre o cartão; fica aberto mais tempo antes de voltar ao ícone
                fase = Fase.ABERTA
                depois((cfg.tempoExpandida * 1000).toLong()) { pendente() }
            }
            Modo.NOTIF_X -> {
                abrir(notif?.intent)
                fecharNotif()
            }
            // Como no iPhone: toque abre o app; segurar expande
            Modo.MUSIC_C -> abrir(music?.controller?.sessionActivity)
            Modo.MUSIC_X -> expandir(false)
            Modo.LIVE_C -> {
                val l = primeiraLive()
                if (l?.proprio != null) expandir(true) else abrir(l?.intent)
            }
            Modo.LIVE_X -> {
                val l = primeiraLive()
                if (l?.proprio == null) abrir(l?.intent)
                expandir(false)
            }
            Modo.CALL_IN -> abrir(primeiraLive()?.intent)
            Modo.ALERTA -> {
                alertaJob?.cancel()
                alerta = null
            }
            Modo.IDLE -> {}
        }
        refresh()
    }

    private fun segurar() {
        when (ui.value.modo) {
            Modo.MUSIC_C, Modo.LIVE_C -> {
                vibrar(longArrayOf(0, 18))
                expandir(true)
            }
            Modo.MUSIC_X -> {
                abrir(music?.controller?.sessionActivity)
                expandir(false)
            }
            Modo.LIVE_X -> {
                abrir(primeiraLive()?.intent)
                expandir(false)
            }
            Modo.NOTIF, Modo.NOTIF_C -> {
                fase = Fase.ABERTA
                depois((cfg.tempoExpandida * 1000).toLong()) { pendente() }
            }
            else -> {}
        }
        refresh()
    }

    private fun acao(a: String) {
        if (a == "bolha" || a == "bolha_abrir") {
            tocarBolha(a == "bolha_abrir")
            return
        }
        if (a.startsWith("acao:")) {
            acaoNotif(a.removePrefix("acao:").toIntOrNull() ?: return)
            return
        }
        if (a.startsWith("live:")) {
            val l = primeiraLive() ?: return
            val i = a.removePrefix("live:").toIntOrNull() ?: return
            val act = l.actions.getOrNull(i) ?: return
            try {
                act.actionIntent.send()
                IslandBus.log("Ação \"${act.title}\" enviada a ${l.app}")
            } catch (e: Exception) {
                IslandBus.log("Ação falhou: ${e.message}")
            }
            if (l.recebendo) minimizadas.add(l.key)
            refresh()
            return
        }
        when (a) {
            "timer:pausar" -> { IslandBus.pausarOuRetomar(); expandir(true); return }
            "timer:cancelar" -> { IslandBus.cancelarTimer(); expandir(false); refresh(); return }
        }
        val t = music?.controller?.transportControls ?: return
        if (a.startsWith("seek:")) {
            val f = a.removePrefix("seek:").toFloatOrNull() ?: return
            val d = music?.duration ?: 0L
            if (d > 0) t.seekTo((f * d).toLong())
            return
        }
        expandir(true) // mexeu nos controles: reinicia o tempo para recolher
        when (a) {
            "play" -> if (music?.playing == true) t.pause() else t.play()
            "next" -> t.skipToNext()
            "prev" -> t.skipToPrevious()
            "app" -> {
                abrir(music?.controller?.sessionActivity)
                expandir(false)
                refresh()
            }
        }
    }

    private fun tocarBolha(abrirApp: Boolean) {
        val bn = bolhaNotif
        if (bn != null) {
            bolhaJob?.cancel()
            bolhaNotif = null
            if (abrirApp) {
                abrir(bn.intent)
            } else {
                // Toque na bolinha: a notificação vem para a ilha principal, já aberta
                notif = bn
                contagem = bolhaContagem
                fase = Fase.ABERTA
                expandir(false)
                depois((cfg.tempoExpandida * 1000).toLong()) { pendente() }
            }
            refresh()
            return
        }
        // Bolinha com a segunda atividade: ela passa para a ilha principal
        val segunda = ativas().getOrNull(1) ?: return
        if (abrirApp) {
            when (segunda) {
                is Ativ.Musica -> abrir(segunda.m.controller?.sessionActivity)
                is Ativ.Viva -> abrir(segunda.l.intent)
            }
        } else {
            escolhida = segunda.key
            expandir(false)
        }
        refresh()
    }

    /** Botão da notificação: responder abre o campo de texto; os outros executam direto. */
    private fun acaoNotif(i: Int) {
        val n = notif ?: return
        val action = n.actions.getOrNull(i) ?: return
        IslandBus.silenciar(n.key)
        if (!action.remoteInputs.isNullOrEmpty()) {
            IslandBus.reply = ReplyRequest(n, action)
            try {
                startActivity(
                    Intent(this, ReplyActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                )
            } catch (e: Exception) {
                IslandBus.log("Não consegui abrir a resposta: ${e.message}")
            }
        } else {
            try {
                action.actionIntent.send()
                IslandBus.log("Ação \"${action.title}\" enviada")
            } catch (e: Exception) {
                IslandBus.log("Ação falhou: ${e.message}")
            }
        }
        fecharNotif()
    }

    /** Deslizar: com várias atividades, alterna entre elas; com só música, troca de faixa. */
    private fun deslizar(direita: Boolean) {
        val lista = ativas()
        if (lista.size > 1 && (ui.value.modo.compactoComAtividade() || ui.value.modo == Modo.LIVE_X || ui.value.modo == Modo.MUSIC_X)) {
            val prox = if (direita) lista.last() else lista[1]
            escolhida = prox.key
            vibrar(longArrayOf(0, 12))
            refresh()
            return
        }
        val m = ui.value.modo
        if ((m == Modo.MUSIC_C || m == Modo.MUSIC_X) && cfg.deslizarTrocaMusica) {
            val t = music?.controller?.transportControls ?: return
            if (direita) t.skipToPrevious() else t.skipToNext()
        }
    }

    /**
     * Arrastou a ilha toda para a esquerda: fecha o que está nela.
     * Música: para a reprodução. Notificação: apaga. Atividade: tira da ilha. Chamada tocando: encolhe.
     */
    private fun fechar() {
        when (ui.value.modo) {
            Modo.MUSIC_C, Modo.MUSIC_X -> {
                music?.controller?.transportControls?.let { t ->
                    t.pause()
                    t.stop()
                }
                hideMusicJob?.cancel()
                musicVisible = false
                expandir(false)
                IslandBus.log("Música fechada pela ilha")
            }
            Modo.NOTIF, Modo.NOTIF_C, Modo.NOTIF_X -> {
                notif?.let { IslandBus.cancelar.tryEmit(it.key) }
                fecharNotif()
                IslandBus.log("Notificação fechada pela ilha")
            }
            Modo.CALL_IN -> primeiraLive()?.let { minimizadas.add(it.key) }
            Modo.LIVE_C, Modo.LIVE_X -> {
                primeiraLive()?.let { l ->
                    if (l.proprio != null) IslandBus.cancelarTimer() else ocultas.add(l.key)
                }
                expandir(false)
            }
            Modo.ALERTA -> {
                alertaJob?.cancel()
                alerta = null
            }
            Modo.IDLE -> {}
        }
        refresh()
    }

    /** Modo de edição: arrastar a ilha muda a posição. */
    private fun mover(dxPx: Float, dyPx: Float, fim: Boolean) {
        if (!editando) return
        val d = resources.displayMetrics.density
        cfg = cfg.copy(
            offsetX = (cfg.offsetX + dxPx / d).coerceIn(-200f, 200f),
            offsetY = (cfg.offsetY + dyPx / d).coerceIn(-60f, 120f),
        )
        if (fim) {
            cfg = cfg.copy(offsetX = cfg.offsetX.roundToInt().toFloat(), offsetY = cfg.offsetY.roundToInt().toFloat())
            Prefs.write(prefs, cfg)
        }
        refresh()
    }

    /** Abre o app dono da notificação/música. */
    private fun abrir(pi: PendingIntent?) {
        pi ?: return
        try {
            val opts = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                }.toBundle()
            } else null
            pi.send(this, 0, null, null, null, null, opts)
        } catch (e: Exception) {
            // app fechado ou intent cancelada: ignora
        }
    }

    // ---------- demonstração ----------

    private fun demo(d: String) {
        when (d) {
            "carregando" -> mostrarAlerta(Alerta(AlertaTipo.CARREGANDO, "Carregando", "${ultimoNivel.coerceAtLeast(0)}%", 0xFF34C759.toInt(), ultimoNivel.coerceAtLeast(0) / 100f))
            "bateria" -> mostrarAlerta(Alerta(AlertaTipo.BAIXA, "Bateria fraca", "15%", 0xFFFF453A.toInt(), 0.15f), 4_000L)
            "timer" -> IslandBus.iniciarTimer(60_000L)
            "notif" -> IslandBus.notifs.tryEmit(
                NotifInfo("teste", "D.I.A", "Pré-visualização", "Assim fica uma notificação na ilha", null, null, pkg = "teste"),
            )
            "expandir" -> { if (ativas().isNotEmpty()) { expandir(true); refresh() } }
        }
    }

    // ---------- layout ----------

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun screenWidthDp(): Float {
        val w = wm?.currentWindowMetrics?.bounds?.width() ?: 1080
        return w / resources.displayMetrics.density
    }

    private fun computeUi(): UiState {
        val lista = ativas()
        val primeira = lista.firstOrNull()
        val m = (primeira as? Ativ.Musica)?.m ?: music
        val live = (primeira as? Ativ.Viva)?.l
        val chamadaTocando = live != null && live.recebendo && live.key !in minimizadas
        val al = alerta

        val modo = when {
            editando -> Modo.IDLE
            chamadaTocando -> Modo.CALL_IN
            notif != null && fase == Fase.FAIXA -> Modo.NOTIF
            notif != null && fase == Fase.ABERTA -> Modo.NOTIF_X
            al != null -> Modo.ALERTA
            primeira is Ativ.Musica -> if (expanded) Modo.MUSIC_X else Modo.MUSIC_C
            primeira is Ativ.Viva -> if (expanded) Modo.LIVE_X else Modo.LIVE_C
            notif != null -> Modo.NOTIF_C
            else -> Modo.IDLE
        }
        if (modo == Modo.IDLE) expanded = false
        val largo = screenWidthDp() - 2f * cfg.margem
        // Faixa do topo: folga acima da câmera + altura da câmera + espaço até o conteúdo
        val topo = cfg.acima + cfg.height + cfg.respiro
        val base = MARGEM_INTERNA
        val rc = (cfg.height / 2f) * cfg.cantos.coerceIn(0.15f, 1f)
        val rx = cfg.raioExpandido
        val compactoLargo = cfg.width + cfg.musicaExtra
        val (w, h, r) = when (modo) {
            Modo.IDLE -> Triple(cfg.width, cfg.height, rc)
            Modo.MUSIC_C, Modo.LIVE_C -> Triple(compactoLargo, cfg.height, rc)
            Modo.MUSIC_X -> Triple(largo, topo + 142f + base, rx)
            Modo.NOTIF -> Triple(largo, cfg.height, rc)
            Modo.NOTIF_C -> Triple(cfg.width + 44f, cfg.height, rc)
            // cabeçalho do app (na altura da câmera) + pessoa e mensagem + botões
            Modo.NOTIF_X -> Triple(
                largo,
                topo + 44f + 10f + 56f + (if (notif?.actions.isNullOrEmpty()) 0f else 14f + 36f) + base,
                rx,
            )
            Modo.LIVE_X -> {
                val l = live
                val barra = l != null && (l.progressoAgora() != null || l.indeterminado || l.chronoBase > 0L || l.proprio != null)
                val botoes = l != null && (l.actions.isNotEmpty() || l.proprio != null)
                Triple(largo, topo + 48f + (if (barra) 34f else 0f) + (if (botoes) 50f else 0f) + base, rx)
            }
            Modo.CALL_IN -> Triple(largo, topo + 56f + base, rx)
            Modo.ALERTA -> Triple(minOf(compactoLargo + 70f, largo), cfg.height, rc)
        }

        // Ilha dividida: notificação de passagem ou a segunda atividade na bolinha ao lado
        val segunda = lista.getOrNull(1)
        val bn = bolhaNotif
        val bolha: Bolha? = when {
            !modo.compactoComAtividade() -> null
            bn != null -> Bolha(bn.key, bn.appIcon ?: bn.icon, bn.accent, bolhaContagem)
            segunda is Ativ.Musica -> Bolha(segunda.key, segunda.m.art, segunda.m.accent, musica = segunda.m)
            segunda is Ativ.Viva -> Bolha(segunda.key, segunda.l.appIcon, segunda.l.accent, live = segunda.l)
            else -> null
        }
        val dividida = bolha != null
        val wMain = if (dividida) w - ESPACO_BOLHA - cfg.height else w
        val dx = if (dividida) -(ESPACO_BOLHA + cfg.height) / 2f else 0f
        return UiState(
            modo = modo, w = w, h = h, r = r, music = m, notif = notif, live = live, alerta = al,
            cx = ui.value.cx, topo = topo, y = ui.value.y, contagem = contagem,
            acima = cfg.acima, alturaCamera = cfg.height,
            wMain = wMain, dx = dx, bolha = bolha, atividades = lista.size,
            amortecimento = cfg.amortecimento, rigidez = cfg.rigidez, opacidade = cfg.opacidade,
            escalaIcones = cfg.escalaIcones, tamanhoTexto = cfg.tamanhoTexto,
            animOnda = cfg.animOnda, animBolha = cfg.animBolha, editando = editando,
        )
    }

    private fun refresh() {
        ui.value = computeUi()
        applyLayout()
    }

    /** Recorte da câmera (punch-hole/notch) no topo da tela, se o aparelho informa. */
    private fun furoDaCamera(): android.graphics.Rect? {
        val metrics = wm?.currentWindowMetrics ?: return null
        val bounds = metrics.bounds
        return metrics.windowInsets.displayCutout?.boundingRects
            ?.filter { it.top < bounds.height() / 4 }
            ?.minByOrNull { kotlin.math.abs(it.exactCenterX() - bounds.width() / 2f) }
    }

    /** Publica a geometria detectada para a tela de calibração do app. */
    private fun relatarTela() {
        val metrics = wm?.currentWindowMetrics ?: return
        val b = metrics.bounds
        val dm = resources.displayMetrics
        val furo = furoDaCamera()
        val status = metrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top
        val cantos = if (Build.VERSION.SDK_INT >= 31) {
            metrics.windowInsets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius
        } else null
        IslandBus.tela.value = buildString {
            append("Tela ${b.width()}×${b.height()} px · ${dm.densityDpi} dpi (${"%.2f".format(dm.density)}x)\n")
            append("${(b.width() / dm.density).roundToInt()}×${(b.height() / dm.density).roundToInt()} dp · barra de status ${(status / dm.density).roundToInt()} dp\n")
            if (furo != null) {
                append("Câmera: centro x=${(furo.exactCenterX() / dm.density).roundToInt()} dp, y=${(furo.exactCenterY() / dm.density).roundToInt()} dp, ")
                append("${(furo.width() / dm.density).roundToInt()}×${(furo.height() / dm.density).roundToInt()} dp")
                val desvio = furo.exactCenterX() - b.width() / 2f
                if (kotlin.math.abs(desvio) > dp(4f)) append(" (fora do centro)")
            } else {
                append("Câmera: o aparelho não informa o recorte. Use a calibração manual.")
            }
            if (cantos != null) append("\nCantos da tela: raio ${(cantos / dm.density).roundToInt()} dp")
        }
    }

    /**
     * A janela de desenho nunca muda de tamanho durante as animações (é isso que evitava o "pulo").
     * Só a janela de toque, que é invisível, acompanha o tamanho da ilha.
     */
    private fun computeWindows() {
        val manager = wm ?: return
        val v = view ?: return
        val p = lp ?: return
        val t = touch ?: return
        val tp = touchLp ?: return
        val s = ui.value

        val paisagem = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val escondida = !cfg.enabled ||
            (cfg.esconderPaisagem && paisagem) ||
            (cfg.esconderTelaCheia && telaCheia && !editando && s.modo != Modo.CALL_IN)
        IslandBus.visibilidade.value = when {
            !cfg.enabled -> "Escondida: \"Mostrar a ilha\" está desligado"
            cfg.esconderPaisagem && paisagem -> "Escondida: celular deitado"
            escondida -> "Escondida: tela cheia detectada"
            else -> "Visível"
        }
        val vis = if (escondida) View.GONE else View.VISIBLE
        v.visibility = vis
        t.visibility = vis

        val metrics = manager.currentWindowMetrics
        val bounds = metrics.bounds
        val hole = furoDaCamera()
        val statusTop = metrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top

        val cx = (hole?.exactCenterX() ?: (bounds.width() / 2f)) + dp(cfg.offsetX)
        val cy = (hole?.exactCenterY() ?: (statusTop / 2f)) + dp(cfg.offsetY)
        val top = (cy - dp(cfg.height) / 2f).roundToInt().coerceAtLeast(0)

        // Expandida, a forma sobe para envolver a câmera com uma folga acima
        val topExp = (top - dp(cfg.acima)).coerceAtLeast(0f).roundToInt()
        val y = if (s.modo.expandido()) topExp else top

        // Desenho: do topo da tela, largura da tela, altura para a maior forma (+ folga da mola)
        p.x = 0
        p.y = 0
        p.width = bounds.width()
        p.height = top + dp(MAX_H + 24f).roundToInt()
        if (ui.value.cx != cx || ui.value.y != y.toFloat()) {
            ui.value = ui.value.copy(cx = cx, y = y.toFloat())
        }

        // Toque: exatamente onde a ilha vai ficar (no modo edição, um pouco maior para pegar fácil)
        val folga = if (editando) dp(16f) else 0f
        val w = dp(s.w) + 2 * folga
        tp.width = w.roundToInt()
        tp.height = (dp(s.h) + 2 * folga).roundToInt()
        tp.x = (cx - w / 2f).roundToInt()
        tp.y = (y - folga).roundToInt()
    }

    private fun applyLayout() {
        computeWindows()
        val manager = wm ?: return
        view?.let { if (it.isAttachedToWindow) manager.updateViewLayout(it, lp) }
        touch?.let { if (it.isAttachedToWindow) manager.updateViewLayout(it, touchLp) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        expanded = false
        ui.value = computeUi()
        applyLayout()
        relatarTela()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Não lemos o conteúdo das telas. A tela cheia é detectada pela "sonda" (insets da barra de status).
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        try { unregisterReceiver(bateriaReceiver) } catch (e: Exception) { }
        view?.let { v -> if (v.isAttachedToWindow) wm?.removeView(v) }
        touch?.let { v -> if (v.isAttachedToWindow) wm?.removeView(v) }
        sonda?.let { v -> if (v.isAttachedToWindow) wm?.removeView(v) }
        view = null
        touch = null
        sonda = null
        IslandBus.serviceOn.value = false
        owner.stop()
        super.onDestroy()
    }
}
