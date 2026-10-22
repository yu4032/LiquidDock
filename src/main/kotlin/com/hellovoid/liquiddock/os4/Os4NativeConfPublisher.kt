package com.hellovoid.liquiddock.os4

import android.content.Context
import android.util.Log

/** Builds and publishes the versioned fail-closed OS4 native snapshot. */
object Os4NativeConfPublisher {
    private const val TAG = "LiquidDock-OS4Conf"

    @JvmStatic
    fun buildConf(gridCols: Int, gridRows: Int): String? {
        if (gridCols !in 2..10 || gridRows !in 2..10) return null
        return buildString {
            append("grid_cols=").append(gridCols).append('\n')
            append("grid_rows=").append(gridRows).append('\n')
            append("end=1").append('\n')
        }
    }

    @JvmStatic
    fun publish(context: Context, gridCols: Int, gridRows: Int): Boolean {
        val conf = buildConf(gridCols, gridRows)
        if (conf == null) {
            Log.e(TAG, "reject invalid grid ${gridCols}x$gridRows")
            return false
        }
        return Os4RootConfWriter.write(context, conf)
    }
}
