package com.hellovoid.liquiddock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.IdentityHashMap

/**
 * Complete VISUAL + INPUT replacement using LiquidDock's genuine PrismalAGSL
 * GuiOnTouchPrismalToggle. The original Settings CompoundButton remains as a
 * non-drawing model/controller in its exact position and parent: removing the
 * Java instance would break Preference references, validation, accessibility
 * and persistence. Only the original business widget makes state decisions.
 *
 * Each Settings Activity has one GPU-only NativeBackdrop session shared by
 * its switch visuals; no bitmaps, screenshots or additional windows.
 */
object SystemSettingsPrismalSwitchHost {
    private const val TAG = "[DC][SettingsSwitch]"
    private const val MAX_BINDINGS = 8
    private val main = Handler(Looper.getMainLooper())
    private val attached = IdentityHashMap<CompoundButton, Binding>()
    private val sessions = IdentityHashMap<FrameLayout, Session>()
    private val failedKinds = HashSet<String>()

    @JvmStatic
    @Synchronized
    fun isAttached(stock: CompoundButton): Boolean = attached.containsKey(stock)

    @JvmStatic
    @Synchronized
    fun attach(stock: CompoundButton): Boolean {
        if (attached.containsKey(stock)) return true
        if (failedKinds.contains(stock.javaClass.name)) return false
        if (!stock.isAttachedToWindow || stock.width <= 0 || stock.height <= 0) return false
        if (stock.id != android.R.id.checkbox &&
            stock.id != android.R.id.switch_widget) return false
        if (attached.size >= MAX_BINDINGS) return false
        val root = stock.rootView.findViewById<View>(android.R.id.content)
            as? FrameLayout ?: return false
        if (!root.isAttachedToWindow) return false
        // Must run before ComposeView is attached or any Prismal modifier draws.
        if (!SystemSettingsPrismalBootstrap.ensure(stock.context)) return false

        var session = sessions[root]
        val newSession = session == null
        var pending: Binding? = null
        try {
            if (session == null) {
                session = Session(root)
                sessions[root] = session
            }
            val binding = Binding(stock, session)
            pending = binding
            binding.install()
            attached[stock] = binding
            session.register(binding)
            Api101Bridge.log("$TAG Prismal component installed type=${stock.javaClass.name} " +
                "GPU-sampled=true native-controller-retained=true")
            return true
        } catch (error: Throwable) {
            failedKinds.add(stock.javaClass.name)
            Api101Bridge.log("$TAG Prismal component install rejected " +
                "type=${stock.javaClass.name} cause=${error.javaClass.name}: ${error.message}")
            try { pending?.dispose() } catch (cleanup: Throwable) {
                Api101Bridge.log("$TAG Prismal rollback failed " +
                    cleanup.javaClass.name + ": " + cleanup.message)
            }
            if (newSession) sessions.remove(root)?.dispose()
            return false
        }
    }

    @JvmStatic
    @Synchronized
    fun onCheckedChanged(stock: CompoundButton) {
        attached[stock]?.updateChecked()
    }

    private fun findActivity(context: Context): Activity? {
        var current = context
        repeat(16) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext ?: return null
        }
        return null
    }

    private class Session(val content: FrameLayout) {
        val backdrop = SystemSettingsNativeBackdrop(content)
        val bindings = LinkedHashSet<Binding>()
        private val activity = findActivity(content.context)
            ?: throw IllegalStateException("Cannot locate native Settings Activity")
        private val suppliedLifecycle = content.findViewTreeLifecycleOwner()
        private val suppliedSaved = content.findViewTreeSavedStateRegistryOwner()
        private val bridge =
            if (suppliedLifecycle == null || suppliedSaved == null) {
                SystemSettingsComposeLifecycleBridge(activity) { _, _ ->
                    main.post { dispose() }
                }
            } else null
        val lifecycle: LifecycleOwner = suppliedLifecycle ?: bridge
            ?: throw IllegalStateException("missing lifecycle owner")
        val saved: SavedStateRegistryOwner = suppliedSaved ?: bridge
            ?: throw IllegalStateException("missing saved-state owner")
        val model: ViewModelStoreOwner? = content.findViewTreeViewModelStoreOwner()
            ?: bridge ?: (activity as? ViewModelStoreOwner)
        private val rootPosition = IntArray(2)
        private val stockPosition = IntArray(2)
        private val preDraw = ViewTreeObserver.OnPreDrawListener {
            if (!disposing && !backdrop.capturing) {
                try {
                    bindings.toList().forEach { it.sync(rootPosition, stockPosition) }
                    backdrop.refreshIfNeeded()
                } catch (error: Throwable) {
                    Api101Bridge.log("$TAG native sample/sync suppressed " +
                        error.javaClass.name + ": " + error.message)
                }
            }
            true
        }
        private val onScroll = ViewTreeObserver.OnScrollChangedListener {
            if (!disposing) backdrop.requestCapture()
        }
        private val observer = content.viewTreeObserver
        private var disposing = false

        init {
            if (!observer.isAlive) throw IllegalStateException("Settings ViewTreeObserver dead")
            observer.addOnPreDrawListener(preDraw)
            observer.addOnScrollChangedListener(onScroll)
            Api101Bridge.log("$TAG native Settings GPU scene created activity=" +
                activity.javaClass.name + " owner=" +
                if (bridge == null) "existing" else "activity-callback-bridge")
        }

        fun register(binding: Binding) {
            bindings.add(binding)
            backdrop.add(binding.stock, binding.visual ?: return)
            backdrop.requestCapture()
        }

        fun unregister(binding: Binding) {
            bindings.remove(binding)
            backdrop.remove(binding.stock)
            if (bindings.isEmpty()) {
                sessions.remove(content)
                dispose()
            }
        }

        fun dispose() {
            if (disposing) return
            disposing = true
            if (observer.isAlive) {
                try { observer.removeOnPreDrawListener(preDraw) } catch (_: Throwable) {}
                try { observer.removeOnScrollChangedListener(onScroll) } catch (_: Throwable) {}
            }
            // Activity destruction can dispatch window detaches. Never mutate
            // the root child array synchronously during ViewGroup's detach loop.
            bindings.toList().forEach { binding ->
                main.post { binding.dispose() }
            }
            bindings.clear()
            backdrop.release()
            bridge?.close()
            sessions.remove(content)
        }
    }

    private class MirrorComposeView(
        context: Context,
        private val scene: SystemSettingsNativeBackdrop,
    ) : ComposeView(context) {
        override fun draw(canvas: Canvas) {
            // Critical: native backdrop recording traverses the same root.
            // Excluding the glass makes feedback recursion mathematically
            // impossible and preserves a real scene sample.
            if (!scene.capturing) super.draw(canvas)
        }
    }

    private class Binding(
        val stock: CompoundButton,
        private val session: Session,
    ) : View.OnAttachStateChangeListener {
        val checked = mutableStateOf(stock.isChecked)
        val enabled = mutableStateOf(stock.isEnabled)
        private val stockAlpha = stock.alpha
        private val visibleRect = Rect()
        private var disposed = false
        var visual: MirrorComposeView? = null
            private set

        fun install() {
            val scene = session.backdrop
            val compose = MirrorComposeView(stock.context, scene).apply {
                id = View.generateViewId()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                // No fake local GlassLayer. The real native scene is the
                // actual PrismalBackdrop read by the original Prismal toggle.
                setViewTreeLifecycleOwner(session.lifecycle)
                setViewTreeSavedStateRegistryOwner(session.saved)
                session.model?.let { setViewTreeViewModelStoreOwner(it) }
                setContent {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GuiOnTouchPrismalToggle(
                            selected = { checked.value },
                            onSelect = { desired ->
                                if (stock.isEnabled && !disposed &&
                                    desired != stock.isChecked) {
                                    try { nativeCommit() } catch (error: Throwable) {
                                        Api101Bridge.log("$TAG native preference rejected click " +
                                            error.javaClass.name + ": " + error.message)
                                    }
                                }
                                updateChecked()
                            },
                            backdrop = scene,
                            enabled = enabled.value,
                        )
                    }
                }
            }
            visual = compose
            val d = stock.resources.displayMetrics.density
            val width = maxOf(stock.width, (64f*d + 0.5f).toInt())
            val height = maxOf(stock.height, (48f*d + 0.5f).toInt())
            session.content.addView(compose,
                FrameLayout.LayoutParams(width, height, Gravity.TOP or Gravity.LEFT))
            stock.addOnAttachStateChangeListener(this)
            // The original is still the Preference's live controller and
            // accessibility node. Only its pixels disappear.
            stock.alpha = 0f
        }

        private fun nativeCommit() {
            // Some MIUIX Preference XML sets native clickable=false on purpose.
            // If the row is the actual click owner, dispatch through that row
            // so Settings' veto/confirmation/persistence path is preserved.
            var parent = stock.parent
            for (depth in 0 until 12) {
                val owner = parent as? View ?: break
                if (owner.id == android.R.id.content) break
                if (owner.isEnabled && owner.isClickable) {
                    owner.performClick()
                    return
                }
                parent = owner.parent
            }
            if (stock.isEnabled) stock.performClick()
        }

        fun updateChecked() {
            val native = stock.isChecked
            if (checked.value != native) checked.value = native
        }

        fun sync(rootPosition: IntArray, stockPosition: IntArray) {
            val view = visual ?: return
            if (disposed) return
            if (!stock.isAttachedToWindow || stock.rootView !== session.content.rootView) {
                main.post { dispose() }
                return
            }
            updateChecked()
            if (enabled.value != stock.isEnabled) enabled.value = stock.isEnabled
            val isVisible = stock.visibility == View.VISIBLE &&
                stock.getGlobalVisibleRect(visibleRect) &&
                visibleRect.width() >= stock.width - 1 &&
                visibleRect.height() >= stock.height - 1
            val targetVisibility = if (isVisible) View.VISIBLE else View.INVISIBLE
            if (view.visibility != targetVisibility) {
                view.visibility = targetVisibility
                session.backdrop.requestCapture()
            }
            if (!isVisible) return
            stock.getLocationOnScreen(stockPosition)
            session.content.getLocationOnScreen(rootPosition)
            val d = stock.resources.displayMetrics.density
            val width = maxOf(stock.width, (64f*d + 0.5f).toInt())
            val height = maxOf(stock.height, (48f*d + 0.5f).toInt())
            val tx = (stockPosition[0] - rootPosition[0]) - (width-stock.width)*0.5f
            val ty = (stockPosition[1] - rootPosition[1]) - (height-stock.height)*0.5f
            if (view.translationX != tx || view.translationY != ty) {
                view.translationX = tx
                view.translationY = ty
                session.backdrop.requestCapture()
            }
            val lp = view.layoutParams as? FrameLayout.LayoutParams
            if (lp != null && (lp.width != width || lp.height != height)) {
                lp.width = width
                lp.height = height
                view.layoutParams = lp
                session.backdrop.requestCapture()
            }
        }

        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            main.post {
                if (!stock.isAttachedToWindow) dispose()
            }
        }

        fun dispose() {
            if (disposed) return
            disposed = true
            stock.removeOnAttachStateChangeListener(this)
            stock.alpha = stockAlpha
            visual?.let { view ->
                if (view.parent === session.content) session.content.removeView(view)
                view.disposeComposition()
            }
            visual = null
            attached.remove(stock)
            session.unregister(this)
        }
    }
}
