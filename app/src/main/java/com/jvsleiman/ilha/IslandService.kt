package com.jvsleiman.ilha

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

enum class Modo { IDLE, MUSIC_C, MUSIC_X, NOTIF, NOTIF_X }

/** Tudo que a ilha precisa para se desenhar. Tamanhos em dp. */
data class UiState(
    val modo: Modo = Modo.IDLE,
    val w: Float = 100f,
    val h: Float = 30f,
    val r: Float = 15f,
    val music: MusicInfo? = null,
    val notif: NotifInfo? = null,
    /** Centro horizontal da câmera, em pixels da tela. */
    val cx: Float = 0f,
    /** Faixa livre no topo das formas expandidas (a altura da ilha parada + respiro), em dp. */
    val topo: Float = 38f,
    /** Topo da forma, em pixels da tela (sobe quando expande). */
    val y: Float = 0f,
)

/** Margem interna padrão das formas expandidas: laterais e base iguais, em dp. */
const val MARGEM_INTERNA = 20f
fun Modo.expandido() = this == Modo.MUSIC_X || this == Modo.NOTIF || this == Modo.NOTIF_X


/** Altura da maior forma da ilha (música expandida), em dp. */
const val MAX_H = 300f

/** Quanto tempo a música pausada continua na ilha. */
private const val PAUSA_VISIVEL_MS = 30_000L
/** Quanto tempo uma notificação fica aberta. */
private const val NOTIF_MS = 4_500L

class IslandService : AccessibilityService() {

    private var wm: WindowManager? = null
    // Janela de desenho: tamanho fixo, não recebe toques (passam para o que está embaixo).
    private var view: ComposeView? = null
    private var lp: WindowManager.LayoutParams? = null
    // Janela de toque: invisível, do tamanho exato da ilha; repassa os toques ao desenho.
    private var touch: View? = null
    private var touchLp: WindowManager.LayoutParams? = null
    private val owner = OverlayOwner()
    private lateinit var prefs: SharedPreferences

    private var cfg = Config()
    private var expanded = false
    private var music: MusicInfo? = null
    private var musicVisible = false
    private var notif: NotifInfo? = null
    private var notifAberta = false
    private var hideMusicJob: Job? = null
    private var notifJob: Job? = null

    private val ui = mutableStateOf(UiState())

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { p, _ ->
        cfg = Prefs.read(p)
        refresh()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onServiceConnected() {
        super.onServiceConnected()
        val manager = getSystemService(WindowManager::class.java)
        wm = manager
        prefs = Prefs.get(this)
        cfg = Prefs.read(prefs)
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
                )
            }
        }
        val t = View(this).apply {
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE) {
                    if (notifAberta) {
                        fecharNotif()
                    } else if (expanded) {
                        expanded = false
                        refresh()
                    }
                    return@setOnTouchListener false
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
        ui.value = computeUi()
        computeWindows()
        manager.addView(v, lp)
        manager.addView(t, touchLp)
        IslandBus.serviceOn.value = true
        IslandBus.log("Ilha conectada")

        owner.lifecycleScope.launch {
            IslandBus.music.collect { m -> onMusic(m) }
        }
        owner.lifecycleScope.launch {
            IslandBus.notifs.collect { n -> onNotif(n) }
        }
    }

    // ---------- eventos ----------

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
        notif = n
        notifAberta = false
        agendarFechamento(NOTIF_MS)
        refresh()
    }

    private fun agendarFechamento(ms: Long) {
        notifJob?.cancel()
        notifJob = owner.lifecycleScope.launch {
            delay(ms)
            fecharNotif()
        }
    }

    private fun fecharNotif() {
        notifJob?.cancel()
        notif = null
        notifAberta = false
        refresh()
    }

    private fun tocar() {
        when (ui.value.modo) {
            Modo.NOTIF -> {
                if (notif?.actions.isNullOrEmpty()) {
                    abrir(notif?.intent)
                    fecharNotif()
                } else {
                    // Expande e mostra os botões; fica aberta mais tempo
                    notifAberta = true
                    agendarFechamento(12_000L)
                }
            }
            Modo.NOTIF_X -> {
                abrir(notif?.intent)
                fecharNotif()
            }
            Modo.MUSIC_C -> expanded = true
            Modo.MUSIC_X -> expanded = false
            Modo.IDLE -> {}
        }
        refresh()
    }

    private fun segurar() {
        when (ui.value.modo) {
            Modo.MUSIC_C, Modo.MUSIC_X -> {
                abrir(music?.controller?.sessionActivity)
                expanded = false
            }
            else -> {}
        }
        refresh()
    }

    private fun acao(a: String) {
        if (a.startsWith("acao:")) {
            acaoNotif(a.removePrefix("acao:").toIntOrNull() ?: return)
            return
        }
        val t = music?.controller?.transportControls ?: return
        if (a.startsWith("seek:")) {
            val f = a.removePrefix("seek:").toFloatOrNull() ?: return
            val d = music?.duration ?: 0L
            if (d > 0) t.seekTo((f * d).toLong())
            return
        }
        when (a) {
            "play" -> if (music?.playing == true) t.pause() else t.play()
            "next" -> t.skipToNext()
            "prev" -> t.skipToPrevious()
            "app" -> {
                abrir(music?.controller?.sessionActivity)
                expanded = false
                refresh()
            }
        }
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

    /** Deslizar a ilha com música: troca de faixa. */
    private fun deslizar(direita: Boolean) {
        val m = ui.value.modo
        if (m != Modo.MUSIC_C && m != Modo.MUSIC_X) return
        val t = music?.controller?.transportControls ?: return
        if (direita) t.skipToPrevious() else t.skipToNext()
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

    // ---------- layout ----------

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun screenWidthDp(): Float {
        val w = wm?.currentWindowMetrics?.bounds?.width() ?: 1080
        return w / resources.displayMetrics.density
    }

    private fun computeUi(): UiState {
        val m = music
        val modo = when {
            notif != null -> if (notifAberta) Modo.NOTIF_X else Modo.NOTIF
            musicVisible && m != null -> if (expanded) Modo.MUSIC_X else Modo.MUSIC_C
            else -> Modo.IDLE
        }
        if (modo == Modo.IDLE) expanded = false
        val largo = screenWidthDp() - 2f * cfg.margem
        // O conteúdo expandido começa abaixo da câmera
        // Faixa do topo: folga acima da câmera + altura da câmera + espaço até o conteúdo
        val topo = cfg.acima + cfg.height + cfg.respiro
        val base = MARGEM_INTERNA
        val (w, h, r) = when (modo) {
            Modo.IDLE -> Triple(cfg.width, cfg.height, cfg.height / 2f)
            Modo.MUSIC_C -> Triple(cfg.width + cfg.musicaExtra, cfg.height, cfg.height / 2f)
            Modo.MUSIC_X -> Triple(largo, topo + 142f + base, 44f)
            Modo.NOTIF -> Triple(largo, topo + 52f + base, 40f)
            Modo.NOTIF_X -> Triple(largo, topo + 52f + 16f + 44f + base, 44f)
        }
        return UiState(modo, w, h, r, m, notif, ui.value.cx, topo, ui.value.y)
    }

    private fun refresh() {
        ui.value = computeUi()
        applyLayout()
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

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val vis = if (cfg.enabled && !landscape) View.VISIBLE else View.GONE
        v.visibility = vis
        t.visibility = vis

        val metrics = manager.currentWindowMetrics
        val bounds = metrics.bounds
        val insets = metrics.windowInsets
        val hole = insets.displayCutout?.boundingRects
            ?.firstOrNull { it.top < bounds.height() / 4 }
        val statusTop = insets.getInsets(WindowInsets.Type.statusBars()).top

        val cx = (hole?.exactCenterX() ?: (bounds.width() / 2f)) + dp(cfg.offsetX)
        val cy = (hole?.exactCenterY() ?: (statusTop / 2f)) + dp(cfg.offsetY)
        val top = (cy - dp(cfg.height) / 2f).roundToInt()

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

        // Toque: exatamente onde a ilha vai ficar
        val w = dp(s.w)
        tp.width = w.roundToInt()
        tp.height = dp(s.h).roundToInt()
        tp.x = (cx - w / 2f).roundToInt()
        tp.y = y
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Etapa 5: detectar apps em tela cheia para esconder a ilha.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        view?.let { v -> if (v.isAttachedToWindow) wm?.removeView(v) }
        touch?.let { v -> if (v.isAttachedToWindow) wm?.removeView(v) }
        view = null
        touch = null
        IslandBus.serviceOn.value = false
        owner.stop()
        super.onDestroy()
    }
}
