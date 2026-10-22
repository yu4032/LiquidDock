package com.hellovoid.liquiddock.os4

import android.content.Context
import android.util.Log

/**
 * Replace complete CE and DE snapshots through the Launcher mount namespace.
 * Each destination is renamed atomically from a private staging file; CE
 * remains authoritative when accessible (the renames aren't transactional).
 */
internal object Os4RootConfWriter {
    private const val TAG = "LiquidDock-OS4Conf"
    const val PRIMARY = "/data/user/0/com.miui.home/files/liquiddock-native.conf"

    fun write(@Suppress("UNUSED_PARAMETER") context: Context, conf: String): Boolean {
        return try {
            // No preference value is shell-interpolated; config arrives on stdin.
            val command = """
                set -eu
                P="${'$'}(pidof -s com.miui.home || true)"
                case "${'$'}P" in ""|*[!0-9]*) exit 20 ;; esac
                nsenter -t "${'$'}P" -m -- sh -eu -c '
                    CE=/data/user/0/com.miui.home/files
                    DE=/data/user_de/0/com.miui.home/files
                    [ -d "${'$'}CE" ] && [ -d "${'$'}DE" ] || exit 21
                    umask 077
                    TDE="${'$'}(mktemp "${'$'}DE/.liquiddock-early.XXXXXX")"
                    TCE="${'$'}(mktemp "${'$'}CE/.liquiddock-main.XXXXXX")"
                    trap "rm -f ${'$'}TDE ${'$'}TCE" EXIT
                    cat > "${'$'}TDE"
                    cp "${'$'}TDE" "${'$'}TCE"
                    U="${'$'}(stat -c %u "${'$'}CE")"
                    chown "${'$'}U:${'$'}U" "${'$'}TDE" "${'$'}TCE"
                    chmod 600 "${'$'}TDE" "${'$'}TCE"
                    mv -f "${'$'}TDE" "${'$'}DE/liquiddock-native-early.conf"
                    mv -f "${'$'}TCE" "${'$'}CE/liquiddock-native.conf"
                    echo RESULT_OK
                '
            """.trimIndent()
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            process.outputStream.use { it.write(conf.toByteArray(Charsets.UTF_8)) }
            val output = process.inputStream.bufferedReader().readText().trim()
            val success = process.waitFor() == 0 &&
                output.lineSequence().any { it.trim() == "RESULT_OK" }
            if (!success) Log.e(TAG, "Native snapshot publish failed: $output")
            success
        } catch (error: Throwable) {
            Log.e(TAG, "Native snapshot publish failed", error)
            false
        }
    }
}
