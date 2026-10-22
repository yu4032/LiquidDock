package com.hellovoid.liquiddock.os4

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.hellovoid.liquiddock.config.ConfigSchema
import java.util.concurrent.Executors

/** Publishes the current GUI grid settings to the OS4 native backend. */
object Os4ConfigSync {
    private const val TAG = "LiquidDock-OS4Sync"
    private val worker = Executors.newSingleThreadExecutor {
        Thread(it, "os4-grid-sync").apply { isDaemon = true }
    }
    private val watched = setOf(
        ConfigSchema.Core.ENABLED.name(),
        ConfigSchema.Grid.ENABLED.name(),
        ConfigSchema.Grid.COLUMNS.name(),
        ConfigSchema.Grid.ROWS.name(),
    )

    @JvmStatic
    fun install(context: Context) {
        request(context.applicationContext, "install")
    }

    @JvmStatic
    fun onPreferenceChanged(context: Context, key: String?) {
        if (key in watched) request(context.applicationContext, "pref:$key")
    }

    private fun request(context: Context, reason: String) {
        worker.execute {
            runCatching { publishNow(context, reason) }
                .onFailure { Log.e(TAG, "[$reason] publish failed", it) }
        }
    }

    private fun publishNow(context: Context, reason: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val master = prefs.getBoolean(
            ConfigSchema.Core.ENABLED.name(),
            ConfigSchema.Core.ENABLED.uiDefault(),
        )
        val enabled = master && prefs.getBoolean(
            ConfigSchema.Grid.ENABLED.name(),
            ConfigSchema.Grid.ENABLED.uiDefault(),
        )
        if (!enabled) {
            Os4RootConfWriter.write(context, "")
            Log.i(TAG, "[$reason] grid disabled; native snapshot cleared")
            return
        }

        val cols = prefs.getInt(
            ConfigSchema.Grid.COLUMNS.name(),
            ConfigSchema.Grid.COLUMNS.uiDefault(),
        )
        val rows = prefs.getInt(
            ConfigSchema.Grid.ROWS.name(),
            ConfigSchema.Grid.ROWS.uiDefault(),
        )
        val published = Os4NativeConfPublisher.publish(context, cols, rows)
        Log.i(TAG, "[$reason] grid=${cols}x$rows published=$published")
    }
}
