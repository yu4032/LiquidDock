package com.hellovoid.liquiddock.os4

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.hellovoid.liquiddock.config.ConfigSchema
import com.hellovoid.liquiddock.LiquidDockRestart
import java.util.concurrent.Executors

/** Publishes main-screen preferences to the OS4 native backend. */
object Os4ConfigSync {
    private const val TAG = "LiquidDock-OS4Sync"
    private const val PREF_DEBOUNCE_MS = 120L
    private val worker = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "os4-grid-sync").apply { isDaemon = true }
    }
    private val lock = Any()
    private var pendingFuture: java.util.concurrent.ScheduledFuture<*>? = null
    private var latestReason: String? = null
    private val watched = Os4MainScreenOptions.watchedKeys

    @JvmStatic
    fun install(context: Context) {
        request(context.applicationContext, "install", 0L)
    }

    @JvmStatic
    fun onPreferenceChanged(context: Context, key: String?) {
        if (key in watched) request(context.applicationContext, "pref:$key", PREF_DEBOUNCE_MS)
    }

    private fun request(context: Context, reason: String, delayMs: Long) {
        // Root publishing must never run on legacy or unknown OS4 launchers.
        if (!supportsPublisher(context)) return
        synchronized(lock) {
            latestReason = reason
            pendingFuture?.cancel(false)
            pendingFuture = worker.schedule({
                val runReason = synchronized(lock) {
                    pendingFuture = null
                    latestReason ?: reason
                }
                runCatching { publishNow(context, runReason) }
                    .onFailure { Log.e(TAG, "[$runReason] publish failed", it) }
            }, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    private fun supportsPublisher(context: Context): Boolean {
        if (!Build.SUPPORTED_ABIS.contains("arm64-v8a")) return false
        return runCatching {
            val info = context.packageManager.getApplicationInfo("com.miui.home", 0)
            val hasCode = (info.flags and ApplicationInfo.FLAG_HAS_CODE) != 0
            Os4LauncherIdentityPolicy.matchesAotPublisher("com.miui.home", hasCode, true)
        }.getOrDefault(false)
    }

    private fun publishNow(context: Context, reason: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val previousWidgetGlass = Os4WidgetMaterials.lastReloadRequestedGlass(context)
        if (reason == "install") {
            Os4WidgetMaterials.scan(context)
        }
        var widgetPublished = Os4WidgetMaterials.publish(context, prefs)
        if (!widgetPublished) {
            Log.w(TAG, "[$reason] MAML widget override publish returned false; scanning inventory...")
            Os4WidgetMaterials.scan(context)
            widgetPublished = Os4WidgetMaterials.publish(context, prefs, force = true)
        }
        if (!widgetPublished) Log.e(TAG, "[$reason] MAML widget override publish failed")
        val conf = Os4MainScreenOptions.fromPreferences(prefs.all)
        if (conf == null) Log.e(TAG, "[$reason] invalid options; native snapshot cleared")
        // The vendor's named Dart AOT symbols live in XZ .gnu_debugdata.
        // Extract off the Launcher process; the safety switch controls exact-byte checks.
        val resolved = if (!conf.isNullOrEmpty()) {
            val safetyEnabled = prefs.getBoolean(ConfigSchema.Grid.NATIVE_HOOK_SAFETY.name(),
                ConfigSchema.Grid.NATIVE_HOOK_SAFETY.uiDefault())
            val addresses = Os4GlassSymbols.fromInstalledLauncher(context, safetyEnabled)
            if (addresses == null) conf
            else {
                val parts = addresses.split('|', limit = 5)
                val gates = parts[0]
                val probes = parts.getOrElse(1) { "" }
                val passBlur = parts.getOrElse(2) { "" }
                val renderGates = parts.getOrElse(3) { "" }
                val recentsBlur = parts.getOrElse(4) { "" }
                val lines = buildString {
                    append("glass_targets=").append(gates).append('\n')
                    if (probes.isNotEmpty()) append("glass_observers=").append(probes).append('\n')
                    if (passBlur.isNotEmpty()) append("passblur_target=").append(passBlur).append('\n')
                    if (renderGates.isNotEmpty()) append("render_targets=").append(renderGates).append('\n')
                    if (recentsBlur.isNotEmpty()) append("recents_blur_target=").append(recentsBlur).append('\n')
                }
                conf.replace("end=1\n",lines + "end=1\n")
            }
        } else conf
        val published = Os4RootConfWriter.write(context, resolved ?: "")
        Log.i(TAG, "[$reason] options published=$published cleared=${conf.isNullOrEmpty()}")
        /* init_maml only redirects new loads; existing renderers retain the
         * previous manifest. Reload on either transition, but only after BOTH
         * snapshots have been published. Failed writes/requests are retriable. */
        val requestedWidgetGlass = Os4WidgetMaterials.isGlassRequested(prefs)
        if (widgetPublished && published && previousWidgetGlass != requestedWidgetGlass) {
            if (LiquidDockRestart.request()) {
                Os4WidgetMaterials.recordReloadRequested(context, requestedWidgetGlass)
                LiquidDockRestart.pullHomeForward(context)
                Log.i(TAG, "[$reason] requested Launcher reload for MAML widget glass=$requestedWidgetGlass")
            } else {
                Log.e(TAG, "[$reason] widget resources published but Launcher reload request failed")
            }
        }
    }
}
