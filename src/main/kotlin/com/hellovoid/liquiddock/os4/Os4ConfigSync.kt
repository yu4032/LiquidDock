package com.hellovoid.liquiddock.os4

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
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
    @Volatile private var lastLauncherStamp: String? = null
    @Volatile private var suppressAutoRestartUntilMs: Long = 0L

    @JvmStatic
    fun install(context: Context) {
        lastLauncherStamp = readLauncherStamp(context)
        request(context.applicationContext, "install", 0L)
    }

    @JvmStatic
    fun syncIfLauncherChanged(context: Context) {
        val app = context.applicationContext
        val current = readLauncherStamp(app) ?: return
        val persisted = app.getSharedPreferences("os4_sync_state", Context.MODE_PRIVATE)
            .getString("last_synced_launcher_stamp", null)
        if (current != lastLauncherStamp || current != persisted) {
            lastLauncherStamp = current
            request(app, "launcher_changed", 0L)
        }
    }

    /** Synchronously waits for any pending/needed launcher-switch sync (safe for BroadcastReceiver.goAsync). */
    @JvmStatic
    fun syncIfLauncherChangedBlocking(context: Context) {
        syncIfLauncherChangedBlocking(context, requestRestart = true)
    }

    /**
     * Prepares native config before a manual launcher restart (e.g. from Settings UI).
     * Publishes the latest preferences even when the Launcher version is unchanged.
     */
    @JvmStatic
    fun prepareForManualLauncherRestart(context: Context) {
        suppressAutoRestartUntilMs = System.currentTimeMillis() + 15_000L
        syncIfLauncherChangedBlocking(context, requestRestart = false, forcePublish = true)
    }

    private fun syncIfLauncherChangedBlocking(
        context: Context,
        requestRestart: Boolean,
        forcePublish: Boolean = false,
    ) {
        val app = context.applicationContext
        if (!supportsPublisher(app)) return
        val current = readLauncherStamp(app) ?: return
        val future = worker.submit {
            val persisted = app.getSharedPreferences("os4_sync_state", Context.MODE_PRIVATE)
                .getString("last_synced_launcher_stamp", null)
            if (forcePublish || current != persisted) {
                lastLauncherStamp = current
                val reason = if (forcePublish) "manual_restart" else "launcher_changed"
                runCatching { publishNow(app, reason, requestRestart) }
                    .onFailure { Log.e(TAG, "[$reason] blocking publish failed", it) }
            }
        }
        runCatching { future.get(8L, java.util.concurrent.TimeUnit.SECONDS) }
            .onFailure { Log.w(TAG, "syncIfLauncherChangedBlocking timed out or failed", it) }
    }

    private fun readLauncherStamp(context: Context): String? = runCatching {
        val pkg = context.packageManager.getPackageInfo("com.miui.home", 0)
        val selfPkg = context.packageManager.getPackageInfo(context.packageName, 0)
        val src = pkg.applicationInfo?.sourceDir.orEmpty()
        "$src:${pkg.lastUpdateTime}:${selfPkg.lastUpdateTime}"
    }.getOrNull()

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

    private fun publishNow(context: Context, reason: String, requestRestart: Boolean = true) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val syncPrefs = context.getSharedPreferences("os4_sync_state", Context.MODE_PRIVATE)
        val currentStamp = readLauncherStamp(context)
        val prevStamp = syncPrefs.getString("last_synced_launcher_stamp", null)
        val launcherChanged = currentStamp != null && currentStamp != prevStamp
        val launcherSwitched = prevStamp != null && launcherChanged
        val previousWidgetGlass = Os4WidgetMaterials.lastReloadRequestedGlass(context)
        if (reason == "install" || launcherChanged) {
            Os4WidgetMaterials.scan(context)
        }
        var widgetPublished = Os4WidgetMaterials.publish(context, prefs)
        if (!widgetPublished) {
            Log.w(TAG, "[$reason] MAML widget override publish returned false; scanning inventory...")
            Os4WidgetMaterials.scan(context)
            widgetPublished = Os4WidgetMaterials.publish(context, prefs, force = true)
        }
        if (!widgetPublished) Log.e(TAG, "[$reason] MAML widget override publish failed")
        if (reason == "install" || launcherChanged) {
            runCatching {
                Os4RemoteWidgetCatalog.scan(context)
                val rvOk = Os4RemoteWidgetCatalog.deployHiddenRules(context, prefs)
                Log.i(TAG, "[$reason] RemoteViews catalog synced & deployed=$rvOk")
            }.onFailure { Log.w(TAG, "[$reason] RemoteViews auto-sync failed", it) }
        }
        val conf = Os4MainScreenOptions.fromPreferences(prefs.all)
        if (conf == null) Log.e(TAG, "[$reason] invalid options; native snapshot cleared")
        // The vendor's named Dart AOT symbols live in XZ .gnu_debugdata.
        // Resolve the installed build; native validation runs when each hook is installed.
        val resolved = if (!conf.isNullOrEmpty()) {
            val resolvedTargets = Os4GlassSymbols.fromInstalledLauncherResolved(context)
            if (resolvedTargets == null) conf
            else {
                val parts = resolvedTargets.glass.split('|', limit = 5)
                val gates = parts[0]
                val probes = parts.getOrElse(1) { "" }
                val passBlur = parts.getOrElse(2) { "" }
                val renderGates = parts.getOrElse(3) { "" }
                val recentsBlur = parts.getOrElse(4) { "" }
                val lines = buildString {
                    if (gates.isNotEmpty()) append("glass_targets=").append(gates).append('\n')
                    if (probes.isNotEmpty()) append("glass_observers=").append(probes).append('\n')
                    if (passBlur.isNotEmpty()) append("passblur_target=").append(passBlur).append('\n')
                    if (renderGates.isNotEmpty()) append("render_targets=").append(renderGates).append('\n')
                    if (recentsBlur.isNotEmpty()) append("recents_blur_target=").append(recentsBlur).append('\n')
                    if (resolvedTargets.layoutTargets.isNotEmpty()) {
                        append("layout_targets=").append(resolvedTargets.layoutTargets).append('\n')
                    }
                    if (resolvedTargets.homeMarginTarget.isNotEmpty()) {
                        append("home_margin_target=").append(resolvedTargets.homeMarginTarget).append('\n')
                    }
                    if (resolvedTargets.rvTargets.isNotEmpty()) {
                        append("rv_targets=").append(resolvedTargets.rvTargets).append('\n')
                    }
                    if (resolvedTargets.folderTarget.isNotEmpty()) {
                        append("folder_target=").append(resolvedTargets.folderTarget).append('\n')
                    }
                    if (resolvedTargets.folderPadTargets.isNotEmpty()) {
                        append("folder_pad_targets=").append(resolvedTargets.folderPadTargets).append('\n')
                    }
                    if (resolvedTargets.folderSizeTargets.isNotEmpty()) {
                        append("folder_size_targets=").append(resolvedTargets.folderSizeTargets).append('\n')
                    }
                    if (resolvedTargets.widgetResizeTargets.isNotEmpty()) {
                        append("widget_resize_targets=").append(resolvedTargets.widgetResizeTargets).append('\n')
                    }
                    if (resolvedTargets.folderGridTargets.isNotEmpty()) {
                        append("folder_grid_targets=").append(resolvedTargets.folderGridTargets).append('\n')
                    }
                    if (resolvedTargets.getStreamAddTarget.isNotEmpty()) {
                        append("getstream_add_target=").append(resolvedTargets.getStreamAddTarget).append('\n')
                    }
                    if (resolvedTargets.glassScopeTargets.isNotEmpty()) {
                        append("glass_scope_targets=").append(resolvedTargets.glassScopeTargets).append('\n')
                    }
                    if (resolvedTargets.widgetGlassTargets.isNotEmpty()) {
                        append("widget_glass_targets=").append(resolvedTargets.widgetGlassTargets).append('\n')
                    }
                    if (resolvedTargets.iconGlassTargets.isNotEmpty()) {
                        append("icon_glass_targets=").append(resolvedTargets.iconGlassTargets).append('\n')
                    }
                    if (resolvedTargets.dividerTargets.isNotEmpty()) {
                        append("divider_targets=").append(resolvedTargets.dividerTargets).append('\n')
                    }
                    if (resolvedTargets.homeOptTargets.isNotEmpty()) {
                        append("home_opt_targets=").append(resolvedTargets.homeOptTargets).append('\n')
                    }
                    if (resolvedTargets.layoutExtraTargets.isNotEmpty()) {
                        append("layout_extra_targets=").append(resolvedTargets.layoutExtraTargets).append('\n')
                    }
                    if (resolvedTargets.widgetResizeClearTarget.isNotEmpty()) {
                        append("widget_resize_clear_target=").append(resolvedTargets.widgetResizeClearTarget).append('\n')
                    }
                    if (resolvedTargets.workstationTargets.isNotEmpty()) {
                        append("workstation_targets=").append(resolvedTargets.workstationTargets).append('\n')
                    }
                    if (resolvedTargets.workstationAllAppsIndTarget.isNotEmpty()) {
                        append("workstation_allapps_ind_target=").append(resolvedTargets.workstationAllAppsIndTarget).append('\n')
                    }
                    if (resolvedTargets.workstationHotseatBottomTarget.isNotEmpty()) {
                        append("workstation_hotseat_bottom_target=").append(resolvedTargets.workstationHotseatBottomTarget).append('\n')
                    }
                    if (resolvedTargets.workstationCalOffsetTarget.isNotEmpty()) {
                        append("workstation_cal_offset_target=").append(resolvedTargets.workstationCalOffsetTarget).append('\n')
                    }
                    if (resolvedTargets.workstationAllAppsStateTarget.isNotEmpty()) {
                        append("workstation_allapps_state_target=").append(resolvedTargets.workstationAllAppsStateTarget).append('\n')
                    }
                    if (resolvedTargets.workstationAllAppsUpdateStateTarget.isNotEmpty()) {
                        append("workstation_allapps_update_state_target=").append(resolvedTargets.workstationAllAppsUpdateStateTarget).append('\n')
                    }
                    if (resolvedTargets.workstationPageChangedTarget.isNotEmpty()) {
                        append("workstation_page_changed_target=").append(resolvedTargets.workstationPageChangedTarget).append('\n')
                    }
                    if (resolvedTargets.workstationJumpToPageTarget.isNotEmpty()) {
                        append("workstation_jump_to_page_target=").append(resolvedTargets.workstationJumpToPageTarget).append('\n')
                    }
                    if (resolvedTargets.workstationPageFieldTargets.isNotEmpty()) {
                        append("workstation_page_field_targets=").append(resolvedTargets.workstationPageFieldTargets).append('\n')
                    }
                    if (resolvedTargets.allAppsBlurTarget.isNotEmpty()) {
                        append("allapps_blur_target=").append(resolvedTargets.allAppsBlurTarget).append('\n')
                    }
                    if (resolvedTargets.widgetGeometryTargets.isNotEmpty()) {
                        append("widget_geometry_targets=").append(resolvedTargets.widgetGeometryTargets).append('\n')
                    }
                    if (resolvedTargets.blurDegradeTarget.isNotEmpty()) {
                        append("blur_degrade_target=").append(resolvedTargets.blurDegradeTarget).append('\n')
                    }
                    if (resolvedTargets.blurBetterDownTarget.isNotEmpty()) {
                        append("blur_better_down_target=").append(resolvedTargets.blurBetterDownTarget).append('\n')
                    }
                    if (resolvedTargets.folderLayoutSyncTargets.isNotEmpty()) {
                        append("folder_layout_sync_targets=").append(resolvedTargets.folderLayoutSyncTargets).append('\n')
                    }
                    if (resolvedTargets.folderOpenFixTargets.isNotEmpty()) {
                        append("folder_open_fix_targets=").append(resolvedTargets.folderOpenFixTargets).append('\n')
                    }
                    if (resolvedTargets.dockIconSizeTarget.isNotEmpty()) {
                        append("dock_icon_size_target=").append(resolvedTargets.dockIconSizeTarget).append('\n')
                    }
                    if (resolvedTargets.folderPreviewDimTargets.isNotEmpty()) {
                        append("folder_preview_dim_targets=").append(resolvedTargets.folderPreviewDimTargets).append('\n')
                    }
                    if (resolvedTargets.widgetCornerTarget.isNotEmpty()) {
                        append("widget_corner_target=").append(resolvedTargets.widgetCornerTarget).append('\n')
                    }
                    if (resolvedTargets.folderBlurTarget.isNotEmpty()) {
                        append("folder_blur_target=").append(resolvedTargets.folderBlurTarget).append('\n')
                    }
                    if (resolvedTargets.shortcutBlurTargets.isNotEmpty()) {
                        append("shortcut_blur_targets=").append(resolvedTargets.shortcutBlurTargets).append('\n')
                    }
                    if (resolvedTargets.wsAllAppsBlurTarget.isNotEmpty()) {
                        append("ws_allapps_blur_target=").append(resolvedTargets.wsAllAppsBlurTarget).append('\n')
                    }
                    if (resolvedTargets.folderTitleAnimTargets.isNotEmpty()) {
                        append("folder_title_anim_targets=").append(resolvedTargets.folderTitleAnimTargets).append('\n')
                    }
                }
                conf.replace("end=1\n",lines + "end=1\n")
            }
        } else conf
        val published = Os4RootConfWriter.write(context, resolved ?: "")
        Log.i(TAG, "[$reason] options published=$published cleared=${conf.isNullOrEmpty()}")
        if (published && currentStamp != null && currentStamp != prevStamp) {
            syncPrefs.edit().putString("last_synced_launcher_stamp", currentStamp).apply()
        }
        if (launcherSwitched && published) {
            if (!requestRestart || System.currentTimeMillis() < suppressAutoRestartUntilMs) {
                Log.i(TAG, "[$reason] skipped auto restart after version switch (manual restart pending)")
            } else if (LiquidDockRestart.request()) {
                Log.i(TAG, "[$reason] requested Launcher restart after version switch")
            } else {
                Log.w(TAG, "[$reason] Launcher version switched but restart request failed")
            }
        }
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
