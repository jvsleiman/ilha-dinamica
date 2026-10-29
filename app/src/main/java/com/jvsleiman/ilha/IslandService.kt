package com.jvsleiman.ilha

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
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

enum class Modo { IDLE, MUSIC_C, MUSIC_X, NOTIF }

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
)

/** Altura da maior forma da ilha (música expandida), em dp. */
const val MAX_H = 176f

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
                )
            }
        }
        val t = View(this).apply {
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE) {
                    if (expanded) {
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
        notifJob?.cancel()
        notifJob = owner.lifecycleScope.launch {
            delay(NOTIF_MS)
            notif = null
            refresh()
        }
        refresh()
    }

    private fun tocar() {
        when (ui.value.modo) {
            Modo.NOTIF -> {
                abrir(notif?.intent)
                notifJob?.cancel()
                notif = null
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
        val t = music?.controller?.transportControls ?: return
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
            notif != null -> Modo.NOTIF
            musicVisible && m != null -> if (expanded) Modo.MUSIC_X else Modo.MUSIC_C
            else -> Modo.IDLE
        }
        if (modo == Modo.IDLE || modo == Modo.NOTIF) expanded = false
        val largo = screenWidthDp() - 24f
        val (w, h, r) = when (modo) {
            Modo.IDLE -> Triple(cfg.width, cfg.height, cfg.height / 2f)
            Modo.MUSIC_C -> Triple(cfg.width + 120f, cfg.height, cfg.height / 2f)
            Modo.MUSIC_X -> Triple(largo, MAX_H, 42f)
            Modo.NOTIF -> Triple(largo, 88f, 36f)
        }
        return UiState(modo, w, h, r, m, notif, ui.value.cx)
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

        // Desenho: largura da tela, altura suficiente para a maior forma (+ folga da mola)
        p.x = 0
        p.y = top
        p.width = bounds.width()
        p.height = dp(MAX_H + 24f).roundToInt()
        if (ui.value.cx != cx) ui.value = ui.value.copy(cx = cx)

        // Toque: exatamente onde a ilha vai ficar
        val w = dp(s.w)
        tp.width = w.roundToInt()
        tp.height = dp(s.h).roundToInt()
        tp.x = (cx - w / 2f).roundToInt()
        tp.y = top
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
