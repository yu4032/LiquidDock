package com.hellovoid.liquiddock.os4

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import java.util.concurrent.Executors

/** Publishes main-screen preferences to the attested OS4 native backend. */
object Os4ConfigSync {
    private const val TAG = "LiquidDock-OS4Sync"
    private val worker = Executors.newSingleThreadExecutor {
        Thread(it, "os4-grid-sync").apply { isDaemon = true }
    }
    private val watched = Os4MainScreenOptions.watchedKeys

    @JvmStatic
    fun install(context: Context) {
        request(context.applicationContext, "install")
    }

    @JvmStatic
    fun onPreferenceChanged(context: Context, key: String?) {
        if (key in watched) request(context.applicationContext, "pref:$key")
    }

    private fun request(context: Context, reason: String) {
        // Root publishing must never run on legacy or unknown OS4 launchers.
        if (!supportsPublisher(context)) return
        worker.execute {
            runCatching { publishNow(context, reason) }
                .onFailure { Log.e(TAG, "[$reason] publish failed", it) }
        }
    }

    private fun supportsPublisher(context: Context): Boolean {
        if (Build.VERSION.SDK_INT != Os4LauncherIdentityPolicy.TARGET_SDK) return false
        if (!Build.SUPPORTED_ABIS.contains("arm64-v8a")) return false
        return runCatching {
            val info = context.packageManager.getApplicationInfo("com.miui.home", 0)
            val version = context.packageManager
                .getPackageInfo("com.miui.home", 0).versionName
            Os4LauncherIdentityPolicy.matches(
                "com.miui.home", version, Build.VERSION.SDK_INT,
                (info.flags and ApplicationInfo.FLAG_HAS_CODE) != 0, true,
            )
        }.getOrDefault(false)
    }

    private fun publishNow(context: Context, reason: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val widgetPublished = Os4WidgetMaterials.publish(context, prefs)
        if (!widgetPublished) Log.e(TAG, "[$reason] MAML widget override publish failed")
        val conf = Os4MainScreenOptions.fromPreferences(prefs.all)
        if (conf == null) Log.e(TAG, "[$reason] invalid options; native snapshot cleared")
        // The vendor's named Dart AOT symbols live in XZ .gnu_debugdata.
        // Extract off the Launcher process, and let Native authenticate bytes.
        val resolved = if (conf?.contains("glass_enabled=1\n") == true) {
            val addresses = Os4GlassSymbols.fromInstalledLauncher(context)
            if (addresses == null) conf
            else {
                val gates = addresses.substringBefore('|')
                val probes = addresses.substringAfter('|', "")
                val lines = "glass_targets=" + gates + "\n" +
                    (if (probes.isEmpty()) "" else "glass_observers=" + probes + "\n")
                conf.replace("end=1\n",lines + "end=1\n")
            }
        } else conf
        val published = Os4RootConfWriter.write(context, resolved ?: "")
        Log.i(TAG, "[$reason] options published=$published cleared=${conf.isNullOrEmpty()}")
    }
}
