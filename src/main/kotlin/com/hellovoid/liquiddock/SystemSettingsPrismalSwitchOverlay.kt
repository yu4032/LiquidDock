package com.hellovoid.liquiddock

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner

import androidx.lifecycle.ViewModelStoreOwner

import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

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
    private val loggedSkips = HashSet<String>()
    // One Activity-linked lifecycle per Settings window; removed on onActivityDestroyed.
    private val activityBridges = HashMap<android.app.Activity, SystemSettingsComposeLifecycleBridge>()
    private var lastFailedStage: String? = null

    /** Keep exception and actionable frame in one grep-able logcat line. */
    private fun failureSummary(stage: String, error: Throwable): String {
        val cause = generateSequence(error) { it.cause?.takeIf { next -> next !== it } }
            .take(6).last()
        val frame = cause.stackTrace.firstOrNull {
            it.className.startsWith("androidx.compose.") ||
                it.className.startsWith("androidx.lifecycle.") ||
                it.className.startsWith("com.styropyr0.") ||
                it.className.startsWith("com.hellovoid.liquiddock.")
        } ?: cause.stackTrace.firstOrNull()
        return "stage=$stage type=${error.javaClass.name} message=${error.message} " +
            "cause=${cause.javaClass.name}: ${cause.message} at=${frame?.toString()}"
    }

    /** Locate the actual vendor Activity, even behind MIUIX ContextThemeWrappers. */
    private fun findHostActivity(context: Context): android.app.Activity? {
        var current = context
        repeat(12) {
            if (current is android.app.Activity) return current
            current = (current as? ContextWrapper)?.baseContext ?: return null
        }
        return null
    }

    private fun bindComposeOwners(view: ComposeView, host: FrameLayout) {
        // Modern AndroidX Settings pages already provide a usable ViewTree.
        val existingLifecycle = host.findViewTreeLifecycleOwner()
        val existingSavedState = host.findViewTreeSavedStateRegistryOwner()
        val activity = findHostActivity(host.context)
        val owner: SystemSettingsComposeLifecycleBridge?
        val lifecycle: LifecycleOwner
        val saved: SavedStateRegistryOwner
        if (existingLifecycle != null && existingSavedState != null) {
            lifecycle = existingLifecycle
            saved = existingSavedState
            owner = null
        } else {
            // Canonical MIUIX Settings uses an ordinary Activity without these
            // Jetpack owners. Bind a REAL ActivityLifecycleCallbacks-backed owner
            // to only this new ComposeView, never Settings' original View tree.
            val target = activity ?: throw IllegalStateException(
                "Cannot resolve native Settings Activity from host Context"
            )
            check(!target.isDestroyed) { "Settings Activity already destroyed" }
            owner = activityBridges.getOrPut(target) {
                SystemSettingsComposeLifecycleBridge(target) { destroyed, bridge ->
                    if (activityBridges[destroyed] === bridge) activityBridges.remove(destroyed)
                }
            }
            lifecycle = owner
            saved = owner
        }
        view.setViewTreeLifecycleOwner(lifecycle)
        view.setViewTreeSavedStateRegistryOwner(saved)
        val model = owner ?: host.findViewTreeViewModelStoreOwner()
            ?: (activity as? ViewModelStoreOwner)
        if (model != null) view.setViewTreeViewModelStoreOwner(model)
        Api101Bridge.log(
            "[DC][SettingsSwitch] compose owners mode=${if (owner != null) "activity-bridge" else "existing"}" +
                " lifecycle=true savedState=true viewModel=${model != null}" +
                " activity=${activity?.javaClass?.name}"
        )
    }

    private fun skipOnce(reason: String): Boolean {
        if (loggedSkips.add(reason)) {
            Api101Bridge.log("[DC][SettingsSwitch] skipped: $reason")
        }
        return true
    }

    @JvmStatic
    @Synchronized
    fun isAttached(original: CompoundButton): Boolean = active.containsKey(original)

    @JvmStatic
    @Synchronized
    fun attach(original: CompoundButton): Boolean {
        if (active.containsKey(original)) return true
        // Canonical Settings XML uses checkbox on legacy MIUIX rows and
        // switch_widget on switch preferences AND MainSwitchBar. Many of the
        // latter have no widget_frame ancestor.
        if (original.id != android.R.id.checkbox &&
            original.id != android.R.id.switch_widget) {
            return skipOnce("unsupported id=${original.id} type=${original.javaClass.name}")
        }
        if (!original.isAttachedToWindow || original.width <= 0 || original.height <= 0) {
            return skipOnce("not attached/measured type=${original.javaClass.name}")
        }
        if (active.size >= MAX_ACTIVE) return skipOnce("active overlay cap reached")
        val host = original.rootView.findViewById<View>(android.R.id.content)
        val content = host as? FrameLayout
            ?: return skipOnce("android.R.id.content not a FrameLayout")
        if (!content.isAttachedToWindow) return skipOnce("content not attached")

        val binding = Binding(original, content)
        return try {
            binding.install()
            active[original] = binding
            Api101Bridge.log("[DC][SettingsSwitch] Prismal visual attached: ${original.javaClass.name} id=${original.id}")
            true
        } catch (error: Throwable) {
            // A Settings hook failure should never mask the original error.
            val summary = failureSummary(binding.stage, error)
            if (lastFailedStage != summary) {
                lastFailedStage = summary
                Api101Bridge.log("[DC][SettingsSwitch] overlay failure " + summary)
            }
            try {
                binding.dispose()
            } catch (cleanup: Throwable) {
                Api101Bridge.log("[DC][SettingsSwitch] cleanup failure " +
                    failureSummary("dispose", cleanup))
            }
            false
        }
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
        var stage: String = "binding-created"
            private set
        private var installed = false
        private var overlay: ComposeView? = null
        private var drawObserver: ViewTreeObserver? = null
        private var preDraw: ViewTreeObserver.OnPreDrawListener? = null

        /**
         * Some native MIUIX widgets have android:clickable="false" intentionally:
         * Settings' preference row or MainSwitchBar owns the validation and
         * persistence path. Dispatch to that owner, never force setChecked().
         */
        private fun performNativePreferenceClick() {
            var parent = stock.parent
            repeat(8) {
                val owner = parent as? View ?: return@repeat
                if (owner.id == android.R.id.content) return
                if (owner.isClickable && owner.isEnabled) {
                    owner.performClick()
                    return
                }
                parent = owner.parent
            }
            if (stock.isClickable) {
                stock.performClick()
            } else {
                Api101Bridge.log("[DC][SettingsSwitch] no native clickable row; ignoring overlay gesture")
            }
        }

        fun install() {
            stage = "create-compose-view"
            val view = ComposeView(stock.context).apply {
                // Multiple overlays share one Activity SavedStateRegistry; each
                // ComposeView needs a unique saveable-state key.
                id = View.generateViewId()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            }
            overlay = view
            stage = "resolve-compose-owners"
            bindComposeOwners(view, content)
            stage = "set-compose-content"
            view.setContent {
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
                                        performNativePreferenceClick()
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
            stage = "add-compose-to-host"
            val width = visualWidth()
            val height = visualHeight()
            content.addView(
                view,
                FrameLayout.LayoutParams(width, height, Gravity.TOP or Gravity.LEFT),
            )
            stage = "register-geometry-listener"
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
            stage = "initial-geometry-sync"
            syncPresentation(view)
            stage = "attached"
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
