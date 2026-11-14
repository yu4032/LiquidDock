package com.hellovoid.liquiddock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hellovoid.liquiddock.os4.Os4ConfigSync
import java.util.concurrent.Executors

class LauncherPackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val pkg = intent.data?.schemeSpecificPart
        if (pkg == "com.miui.home") {
            val appContext = context.applicationContext
            val pending = goAsync()
            executor.execute {
                try {
                    Os4ConfigSync.syncIfLauncherChangedBlocking(appContext)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private companion object {
        val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "ld-pkg-sync").apply { isDaemon = true }
        }
    }
}
