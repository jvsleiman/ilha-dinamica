package com.jvsleiman.ilha

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

/** Altura da ilha expandida, em dp. */
const val EXPANDED_H = 84f

/**
 * Serviço de acessibilidade: é o que permite desenhar a ilha por cima da barra de status.
 * A janela tem o tamanho da ilha, então não atrapalha toques no resto da tela.
 */
class IslandService : AccessibilityService() {

    private var wm: WindowManager? = null
    private var view: ComposeView? = null
    private var lp: WindowManager.LayoutParams? = null
    private val owner = OverlayOwner()
    private lateinit var prefs: SharedPreferences

    private val cfg = mutableStateOf(Config())
    private val expanded = mutableStateOf(false)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { p, _ ->
        cfg.value = Prefs.read(p)
        applyLayout()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onServiceConnected() {
        super.onServiceConnected()
        val manager = getSystemService(WindowManager::class.java)
        wm = manager
        prefs = Prefs.get(this)
        cfg.value = Prefs.read(prefs)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        owner.start()
        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                Island(
                    config = cfg.value,
                    expanded = expanded.value,
                    onTap = { toggle() },
                    onCollapsed = { applyLayout() },
                )
            }
            // Toque fora da ilha expandida: recolhe
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE && expanded.value) {
                    expanded.value = false
                }
                false
            }
        }

        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "Ilha"
        }

        view = v
        lp = params
        computeLayout()
        manager.addView(v, params)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun toggle() {
        if (!expanded.value) {
            expanded.value = true
            applyLayout() // aumenta a janela antes de a animação começar
        } else {
            expanded.value = false // a janela diminui quando a animação termina (onCollapsed)
        }
    }

    /** Calcula tamanho e posição da janela a partir da câmera e dos ajustes. */
    private fun computeLayout() {
        val manager = wm ?: return
        val v = view ?: return
        val p = lp ?: return
        val c = cfg.value

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        v.visibility = if (c.enabled && !landscape) View.VISIBLE else View.GONE

        val metrics = manager.currentWindowMetrics
        val bounds = metrics.bounds
        val insets = metrics.windowInsets
        val hole = insets.displayCutout?.boundingRects
            ?.firstOrNull { it.top < bounds.height() / 4 }
        val statusTop = insets.getInsets(WindowInsets.Type.statusBars()).top

        val cx = (hole?.exactCenterX() ?: (bounds.width() / 2f)) + dp(c.offsetX)
        val cy = (hole?.exactCenterY() ?: (statusTop / 2f)) + dp(c.offsetY)

        val big = expanded.value
        val winW = if (big) bounds.width() else (dp(c.width) + dp(16f)).roundToInt()
        val winH = if (big) dp(EXPANDED_H + 16f).roundToInt() else (dp(c.height) + dp(8f)).roundToInt()

        p.width = winW
        p.height = winH
        p.x = (cx - winW / 2f).roundToInt()
        p.y = (cy - dp(c.height) / 2f).roundToInt()
    }

    private fun applyLayout() {
        computeLayout()
        val v = view ?: return
        val p = lp ?: return
        if (v.isAttachedToWindow) wm?.updateViewLayout(v, p)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        expanded.value = false
        applyLayout()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Etapa 5: detectar apps em tela cheia para esconder a ilha.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        view?.let { v -> if (v.isAttachedToWindow) wm?.removeView(v) }
        view = null
        owner.stop()
        super.onDestroy()
    }
}
