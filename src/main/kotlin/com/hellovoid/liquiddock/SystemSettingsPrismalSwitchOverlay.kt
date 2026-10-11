package com.hellovoid.liquiddock

import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import java.util.WeakHashMap

/**
 * Experimental Compose-backed Prismal switch. Only the UI of a MIUIX widget_frame is replaced.
 *
 * No original listener or checked state is overwritten. The stock CompoundButton continues
 * to perform all preference validation, persistence, accessibility and business side effects.
 */
object SystemSettingsPrismalSwitchOverlay {
    private const val MAX_ACTIVE = 6
    private val active = WeakHashMap<CompoundButton, Binding>()

    @JvmStatic
    @Synchronized
    fun attach(original: CompoundButton): Boolean {
        if (active.containsKey(original)) return true
        // MIUIX Preference widget frames are FrameLayouts. Never reparent or rewrite arbitrary
        // Android/SPA controls, and never touch a SlidingButton in an unexpected host.
        val frame = original.parent as? FrameLayout ?: return true
        if (frame.id != android.R.id.widget_frame) return true
        if (!original.isAttachedToWindow || active.size >= MAX_ACTIVE) return true
        if (frame.indexOfChild(original) < 0) return true

        val binding = Binding(original, frame)
        return try {
            binding.install()
            active[original] = binding
            true
        } catch (error: Throwable) {
            binding.dispose()
            Api101Bridge.log("[DC][SettingsSwitch] Compose overlay unavailable; stock restored", error)
            true
        }
    }

    private class Binding(
        private val stock: CompoundButton,
        private val frame: FrameLayout,
    ) : View.OnAttachStateChangeListener {
        private val stockAlpha = stock.alpha
        private val selected = mutableStateOf(stock.isChecked)
        private val allowed = mutableStateOf(stock.isEnabled)
        private var disposed = false
        private var installed = false
        private var overlay: ComposeView? = null
        private var drawObserver: android.view.ViewTreeObserver? = null
        private var preDraw: android.view.ViewTreeObserver.OnPreDrawListener? = null

        fun install() {
            val view = ComposeView(stock.context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    val backdrop = rememberPrismalGlassLayer()
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // This local source supports Prismal's switch material, without doing
                        // any forbidden Settings bitmap/screen capture. Full page refraction
                        // requires a later native-to-Compose backdrop bridge.
                        Box(Modifier.fillMaxSize().prismalGlassLayer(backdrop))
                        GuiOnTouchPrismalToggle(
                            selected = { selected.value },
                            onSelect = { next ->
                                if (!disposed && stock.isEnabled && next != stock.isChecked) {
                                    // The native widget remains the only click and state authority.
                                    stock.performClick()
                                }
                                selected.value = stock.isChecked
                            },
                            backdrop = backdrop,
                            enabled = allowed.value,
                        )
                    }
                }
            }
            overlay = view
            // Only an existing Preference widget frame is extended; original native view stays
            // mounted in-place. The overlay does not intercept the preference row outside frame.
            frame.addView(view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            installed = true
            stock.addOnAttachStateChangeListener(this)
            val observer = frame.viewTreeObserver
            val watcher = android.view.ViewTreeObserver.OnPreDrawListener {
                if (!disposed) {
                    if (selected.value != stock.isChecked) selected.value = stock.isChecked
                    if (allowed.value != stock.isEnabled) allowed.value = stock.isEnabled
                    if (view.width > 0 && view.height > 0 && stock.alpha == stockAlpha) {
                        // Keep an almost-transparent native control for accessibility. Never
                        // set GONE/INVISIBLE or discard the system's listener/checked state.
                        stock.alpha = 0.02f
                    }
                }
                true
            }
            if (observer.isAlive) {
                observer.addOnPreDrawListener(watcher)
                drawObserver = observer
                preDraw = watcher
            }
        }

        override fun onViewAttachedToWindow(v: View) = Unit

        override fun onViewDetachedFromWindow(v: View) {
            dispose()
        }

        fun dispose() {
            if (disposed) return
            disposed = true
            if (installed) stock.removeOnAttachStateChangeListener(this)
            preDraw?.let { listener ->
                val observer = drawObserver
                if (observer != null && observer.isAlive) {
                    try { observer.removeOnPreDrawListener(listener) } catch (_: Throwable) {}
                }
            }
            preDraw = null
            drawObserver = null
            stock.alpha = stockAlpha
            overlay?.let { view ->
                if (view.parent === frame) frame.removeView(view)
                view.disposeComposition()
            }
            overlay = null
            synchronized(SystemSettingsPrismalSwitchOverlay) {
                active.remove(stock)
            }
            SystemSettingsPrismalSwitchHook.onDetached(stock)
        }
    }
}
