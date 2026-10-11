package com.hellovoid.liquiddock

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Real Activity-callback-backed owner for a small ComposeView inserted into the
 * vendor Settings Activity, which may not use Jetpack's ComponentActivity.
 *
 * This is scoped only to the injected ComposeView; it never modifies owners
 * on Settings' decorView, Preference row or root content.
 */
internal class SystemSettingsComposeLifecycleBridge(
    private val activity: Activity,
    private val onActivityDestroyed: (Activity, SystemSettingsComposeLifecycleBridge) -> Unit,
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, Application.ActivityLifecycleCallbacks {
    private val registry = LifecycleRegistry(this)
    private val stateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = stateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    private var closed = false

    init {
        // Saved state must be attached/restored before advancing out of INITIALIZED.
        stateController.performAttach()
        stateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        // An onDraw candidate belongs to a live attached Settings window.
        // STARTED is sufficient for Compose's WindowRecomposer to begin work;
        // never claim RESUMED merely from an attached View.
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        activity.application.registerActivityLifecycleCallbacks(this)
        Api101Bridge.log("[DC][SettingsSwitch] lifecycle bridge registered activity=" +
            activity.javaClass.name + " state=" + registry.currentState)
    }

    override fun onActivityCreated(candidate: Activity, state: Bundle?) = Unit

    override fun onActivityStarted(candidate: Activity) {
        if (candidate === activity && !closed) registry.currentState = Lifecycle.State.STARTED
    }

    override fun onActivityResumed(candidate: Activity) {
        if (candidate === activity && !closed) registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onActivityPaused(candidate: Activity) {
        if (candidate === activity && !closed) registry.currentState = Lifecycle.State.STARTED
    }

    override fun onActivityStopped(candidate: Activity) {
        if (candidate === activity && !closed) registry.currentState = Lifecycle.State.CREATED
    }

    override fun onActivitySaveInstanceState(candidate: Activity, outState: Bundle) {
        // This owner holds ephemeral switch chrome, not Settings preferences.
        // Do not add transient Prismal state to the system's saved instance state.
    }

    override fun onActivityDestroyed(candidate: Activity) {
        if (candidate !== activity) return
        close()
        onActivityDestroyed(activity, this)
    }

    fun close() {
        if (closed) return
        closed = true
        activity.application.unregisterActivityLifecycleCallbacks(this)
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        Api101Bridge.log("[DC][SettingsSwitch] lifecycle bridge closed activity=" +
            activity.javaClass.name)
    }
}
