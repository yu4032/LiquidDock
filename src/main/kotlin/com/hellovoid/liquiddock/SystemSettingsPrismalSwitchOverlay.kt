package com.hellovoid.liquiddock

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
 * Settings-only Prismal switch presentation pilot.
 *
 * A MIUIX Preference's widget_frame is a LinearLayout in this ROM, not a FrameLayout.
 * Keep the stock widget in its exact original parent; host a small ComposeView over its
 * screen geometry on the existing Activity content FrameLayout. Never change stock
 * listeners, layout parameters, checked state, or preference persistence.
 */
object SystemSettingsPrismalSwitchOverlay {
    private const val MAX_ACTIVE = 6
    private const val STOCK_VISUAL_ALPHA = 0.02f
    private val main = Handler(Looper.getMainLooper())
    private val active = WeakHashMap<CompoundButton, Binding>()

    @JvmStatic
    @Synchronized
    fun attach(original: CompoundButton): Boolean {
        if (active.containsKey(original)) return true
        // The verified MIUIX CheckBoxPreference layout uses android.R.id.checkbox
        // below android.R.id.widget_frame. Reject other switch types/contexts.
        if (original.id != android.R.id.checkbox) return true
        if (!isPreferenceWidget(original)) return true
        if (!original.isAttachedToWindow || original.width <= 0 || original.height <= 0) return true
        if (active.size >= MAX_ACTIVE) return true
        val content = original.rootView.findViewById<FrameLayout>(android.R.id.content) ?: return true
        if (!content.isAttachedToWindow) return true

        val binding = Binding(original, content)
        return try {
            binding.install()
            active[original] = binding
            Api101Bridge.log("[DC][SettingsSwitch] Prismal checkbox visual attached")
            true
        } catch (error: Throwable) {
            binding.dispose()
            Api101Bridge.log("[DC][SettingsSwitch] overlay unavailable; stock restored", error)
            false
        }
    }

    private fun isPreferenceWidget(stock: View): Boolean {
        var ancestor = stock.parent as? View
        repeat(5) {
            if (ancestor?.id == android.R.id.widget_frame) return true
            ancestor = ancestor?.parent as? View
        }
        return false
    }

    private class Binding(
        private val stock: CompoundButton,
        private val content: FrameLayout,
    ) : View.OnAttachStateChangeListener {
        private val stockAlpha = stock.alpha
        private val selected = mutableStateOf(stock.isChecked)
        private val allowed = mutableStateOf(stock.isEnabled)
        private val visibleRect = Rect()
        private val stockPosition = IntArray(2)
        private val contentPosition = IntArray(2)
        private var disposed = false
        private var installed = false
        private var overlay: ComposeView? = null
        private var drawObserver: ViewTreeObserver? = null
        private var preDraw: ViewTreeObserver.OnPreDrawListener? = null

        fun install() {
            val view = ComposeView(stock.context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    val backdrop = rememberPrismalGlassLayer()
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Local Prismal chrome only; full native Settings backdrop sampling
                        // remains unsupported, and no screenshot/bitmap path is used.
                        Box(Modifier.fillMaxSize().prismalGlassLayer(backdrop))
                        GuiOnTouchPrismalToggle(
                            selected = { selected.value },
                            onSelect = { next ->
                                if (!disposed && stock.isEnabled && next != stock.isChecked) {
                                    try {
                                        stock.performClick()
                                    } catch (error: Throwable) {
                                        Api101Bridge.log(
                                            "[DC][SettingsSwitch] native click failed; restoring stock",
                                            error,
                                        )
                                        main.post { dispose() }
                                    }
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
            val width = visualWidth()
            val height = visualHeight()
            content.addView(
                view,
                FrameLayout.LayoutParams(width, height, Gravity.TOP or Gravity.LEFT),
            )
            installed = true
            stock.addOnAttachStateChangeListener(this)
            val observer = content.viewTreeObserver
            val watcher = ViewTreeObserver.OnPreDrawListener {
                if (!disposed) syncPresentation(view)
                true
            }
            if (observer.isAlive) {
                observer.addOnPreDrawListener(watcher)
                drawObserver = observer
                preDraw = watcher
            }
            syncPresentation(view)
        }

        private fun visualWidth(): Int =
            maxOf(stock.width, (64f * stock.resources.displayMetrics.density + 0.5f).toInt())

        private fun visualHeight(): Int =
            maxOf(stock.height, (32f * stock.resources.displayMetrics.density + 0.5f).toInt())

        private fun syncPresentation(view: ComposeView) {
            if (!stock.isAttachedToWindow || stock.rootView !== content.rootView) {
                main.post { dispose() }
                return
            }

            if (selected.value != stock.isChecked) selected.value = stock.isChecked
            if (allowed.value != stock.isEnabled) allowed.value = stock.isEnabled

            val fullyVisible = stock.isShown &&
                stock.getGlobalVisibleRect(visibleRect) &&
                visibleRect.width() >= stock.width - 1 &&
                visibleRect.height() >= stock.height - 1 &&
                stock.width > 0 && stock.height > 0

            if (!fullyVisible) {
                if (view.visibility != View.INVISIBLE) view.visibility = View.INVISIBLE
                if (stock.alpha == STOCK_VISUAL_ALPHA) stock.alpha = stockAlpha
                return
            }

            stock.getLocationOnScreen(stockPosition)
            content.getLocationOnScreen(contentPosition)
            // PrismalAGSL's track is 64dp wide. Centre it on the stock switch instead
            // of clipping the native Prismal capsule to MIUIX's smaller measured width.
            val width = visualWidth()
            val height = visualHeight()
            val dx = (stockPosition[0] - contentPosition[0] - (width - stock.width) / 2f)
            val dy = (stockPosition[1] - contentPosition[1] - (height - stock.height) / 2f)
            if (view.translationX != dx) view.translationX = dx
            if (view.translationY != dy) view.translationY = dy
            val lp = view.layoutParams as? FrameLayout.LayoutParams
            if (lp != null && (lp.width != width || lp.height != height)) {
                lp.width = width
                lp.height = height
                view.layoutParams = lp
            }
            if (view.visibility != View.VISIBLE) view.visibility = View.VISIBLE
            // Keep the actual native switch clickable and accessible in its original row.
            // Only reduce the native drawing alpha after the overlay has valid geometry.
            if (view.width > 0 && view.height > 0 && stock.alpha == stockAlpha) {
                stock.alpha = STOCK_VISUAL_ALPHA
            }
        }

        override fun onViewAttachedToWindow(v: View) = Unit

        override fun onViewDetachedFromWindow(v: View) {
            // Never change a ViewGroup's child array during detach dispatch.
            main.post {
                if (!stock.isAttachedToWindow || stock.rootView !== content.rootView) dispose()
            }
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
                if (view.parent === content) content.removeView(view)
                view.disposeComposition()
            }
            overlay = null
            synchronized(SystemSettingsPrismalSwitchOverlay) { active.remove(stock) }
            SystemSettingsPrismalSwitchHook.onDetached(stock)
        }
    }
}
