package com.hellovoid.liquiddock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hellovoid.liquiddock.os4.Os4ConfigSync
import java.util.concurrent.Executors

class LauncherPackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action
        val pkg = intent.data?.schemeSpecificPart
        val shouldSync = pkg == "com.miui.home"
                || action == Intent.ACTION_BOOT_COMPLETED
                || action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (shouldSync) {
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
