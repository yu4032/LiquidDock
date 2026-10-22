package com.hellovoid.liquiddock.os4

import android.content.Context
import android.util.Log

/**
 * Writes the OS4 native snapshot through the Launcher's own mount namespace.
 *
 * The application process cannot write com.miui.home's private CE/DE
 * directories directly. nsenter avoids creating a misleading private copy in
 * the application namespace.
 */
internal object Os4RootConfWriter {
    private const val TAG = "LiquidDock-OS4Conf"
    const val PRIMARY =
        "/data/user/0/com.miui.home/files/liquiddock-native.conf"
    private const val EARLY =
        "/data/user_de/0/com.miui.home/files/liquiddock-native-early.conf"
    private const val STAGING = "/data/local/tmp/liquiddock-native.conf.staged"
    private const val HOME_DATA = "/data/user/0/com.miui.home"

    fun write(@Suppress("UNUSED_PARAMETER") context: Context, conf: String): Boolean {
        return try {
            val command = buildString {
                append("set -e\n")
                append("cat > ").append(STAGING).append('\n')
                append("chmod 600 ").append(STAGING).append('\n')
                append("P=\$(pidof com.miui.home)\n")
                append("[ -n \"\$P\" ] || exit 20\n")
                append("nsenter -t \"\$P\" -m -- sh -c '")
                append("cat ").append(STAGING).append(" > ").append(PRIMARY)
                append(" && U=\$(stat -c %u ").append(HOME_DATA).append(")")
                append(" && chown \"\$U:\$U\" ").append(PRIMARY)
                append(" && chmod 600 ").append(PRIMARY)
                append(" && cat ").append(STAGING).append(" > ").append(EARLY)
                append(" && chown \"\$U:\$U\" ").append(EARLY)
                append(" && chmod 600 ").append(EARLY)
                append(" && test -f ").append(PRIMARY)
                append(" && test -f ").append(EARLY)
                append(" && echo RESULT_OK'\n")
            }
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            process.outputStream.use { it.write(conf.toByteArray(Charsets.UTF_8)) }
            val output = process.inputStream.bufferedReader().readText().trim()
            val success = process.waitFor() == 0 && output.contains("RESULT_OK")
            Log.i(TAG, "publish success=$success output=[$output] bytes=${conf.length}")
            success
        } catch (error: Throwable) {
            Log.e(TAG, "publish failed", error)
            false
        }
    }
}
