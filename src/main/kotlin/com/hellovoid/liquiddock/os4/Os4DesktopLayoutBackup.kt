package com.hellovoid.liquiddock.os4

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.hellovoid.liquiddock.config.ConfigSchema
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup and restore OS4 launcher desktop/workstation layout databases together
 * with LiquidDock's custom grid settings in a single ZIP archive.
 */
object Os4DesktopLayoutBackup {
    private const val TAG = "LiquidDock-LayoutBackup"
    private const val FORMAT = "liquiddock-desktop-layout"
    private const val VERSION = 1
    private const val MAX_ARCHIVE_BYTES = 32L * 1024L * 1024L

    private val ALLOWED_BIN_FILES = setOf(
        "liquiddock-layout-v3.bin",
        "liquiddock-ws-desk-v3.bin",
        "liquiddock-ws-layout-v3.bin",
    )
    private val ALLOWED_DB_REGEX = Regex("^launcher_rotatable[0-9]+x[0-9]+\\.db(-shm|-wal)?$")

    data class RestoreSummary(
        val fileCount: Int,
        val gridRestored: Boolean,
    )

    @JvmStatic
    @Throws(IOException::class)
    fun exportLayout(context: Context, prefs: SharedPreferences, destUri: Uri): Int {
        // Stream files directly over su stdout with a simple framed header:
        //   "FILE <relpath> <size>\n<raw bytes>" ... "END\n"
        // Avoids writing root-owned / wrong-SELinux-context files into app cacheDir.
        val cmd = """
            set -eu
            HOME_DE="/data/user_de/0/com.miui.home"
            emit_file() {
                REL="${'$'}1"
                SRC="${'$'}2"
                [ -f "${'$'}SRC" ] || return 0
                SZ="${'$'}(wc -c < "${'$'}SRC" | tr -d ' \t\r\n')"
                printf 'FILE %s %s\n' "${'$'}REL" "${'$'}SZ"
                cat "${'$'}SRC"
            }
            for f in liquiddock-layout-v3.bin liquiddock-ws-desk-v3.bin liquiddock-ws-layout-v3.bin; do
                emit_file "files/${'$'}f" "${'$'}HOME_DE/files/${'$'}f"
            done
            for f in "${'$'}HOME_DE/databases/"launcher_rotatable*.db "${'$'}HOME_DE/databases/"launcher_rotatable*.db-shm "${'$'}HOME_DE/databases/"launcher_rotatable*.db-wal; do
                [ -f "${'$'}f" ] || continue
                emit_file "databases/${'$'}(basename "${'$'}f")" "${'$'}f"
            done
            for f in "${'$'}HOME_DE/databases/laptop/"launcher_rotatable*.db "${'$'}HOME_DE/databases/laptop/"launcher_rotatable*.db-shm "${'$'}HOME_DE/databases/laptop/"launcher_rotatable*.db-wal; do
                [ -f "${'$'}f" ] || continue
                emit_file "databases/laptop/${'$'}(basename "${'$'}f")" "${'$'}f"
            done
            printf 'END\n'
        """.trimIndent()

        val process = ProcessBuilder("su", "-c", "nsenter -t 1 -m -- sh -eu").start()
        process.outputStream.use { it.write((cmd + "\n").toByteArray(Charsets.UTF_8)) }
        val entries = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0L
        var sawEnd = false
        try {
            val dis = DataInputStream(BufferedInputStream(process.inputStream))
            while (true) {
                val line = readAsciiLine(dis) ?: break
                if (line == "END") {
                    sawEnd = true
                    break
                }
                if (!line.startsWith("FILE ")) {
                    throw IOException("读取布局数据流格式异常: $line")
                }
                val parts = line.split(' ')
                if (parts.size != 3) throw IOException("读取布局数据头异常: $line")
                val rel = parts[1]
                val size = parts[2].toLongOrNull() ?: throw IOException("文件大小无效: $line")
                if (size < 0L || totalBytes + size > MAX_ARCHIVE_BYTES) {
                    throw IOException("备份数据超过 32 MiB 限制")
                }
                val data = ByteArray(size.toInt())
                dis.readFully(data)
                if (isAllowedEntryName(rel)) {
                    entries[rel] = data
                    totalBytes += size
                }
            }
        } finally {
            val err = runCatching { process.errorStream.bufferedReader().readText().trim() }.getOrDefault("")
            val exitCode = runCatching { process.waitFor() }.getOrDefault(-1)
            if (!sawEnd || exitCode != 0) {
                Log.e(TAG, "exportLayout su failed (exit=$exitCode, sawEnd=$sawEnd): $err")
                throw IOException(
                    if (err.isNotEmpty()) "Root 读取桌面布局失败: $err"
                    else "Root 读取桌面布局失败（请检查 Root 权限）"
                )
            }
        }

        if (entries.isEmpty()) {
            throw IOException("未找到可备份的桌面布局数据库文件")
        }

        val manifest = buildManifest(prefs, entries.keys.toList())
        val out = context.contentResolver.openOutputStream(destUri, "wt")
            ?: throw IOException("无法打开目标文件")
        out.use { rawOut ->
            ZipOutputStream(rawOut).use { zos ->
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write((manifest.toString(2) + "\n").toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                for ((entryName, bytes) in entries) {
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
        return entries.size
    }

    @JvmStatic
    @Throws(IOException::class)
    fun importLayout(context: Context, prefs: SharedPreferences, srcUri: Uri): RestoreSummary {
        val stageDir = File(context.cacheDir, "layout_restore_stage_${System.nanoTime()}")
        try {
            if (!stageDir.mkdirs()) throw IOException("无法创建临时恢复目录")
            var manifestJson: JSONObject? = null
            var totalBytes = 0L
            var restoredFiles = 0

            val input = context.contentResolver.openInputStream(srcUri)
                ?: throw IOException("无法读取所选备份文件")
            input.use { rawIn ->
                ZipInputStream(rawIn).use { zis ->
                    var entry = zis.nextEntry
                    val buf = ByteArray(8192)
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name
                            if (name == "manifest.json") {
                                val baos = ByteArrayOutputStream()
                                var n: Int
                                while (zis.read(buf).also { n = it } != -1) {
                                    totalBytes += n
                                    if (totalBytes > MAX_ARCHIVE_BYTES) {
                                        throw IOException("备份文件超过 32 MiB 限制")
                                    }
                                    baos.write(buf, 0, n)
                                }
                                manifestJson = JSONObject(baos.toString(Charsets.UTF_8.name()))
                            } else if (isAllowedEntryName(name)) {
                                val target = File(stageDir, name)
                                target.parentFile?.mkdirs()
                                target.outputStream().use { fos ->
                                    var n: Int
                                    while (zis.read(buf).also { n = it } != -1) {
                                        totalBytes += n
                                        if (totalBytes > MAX_ARCHIVE_BYTES) {
                                            throw IOException("备份文件超过 32 MiB 限制")
                                        }
                                        fos.write(buf, 0, n)
                                    }
                                }
                                restoredFiles++
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            val manifest = manifestJson ?: throw IOException("无效的桌面布局备份（缺少 manifest.json）")
            if (manifest.optString("_format") != FORMAT) {
                throw IOException("不是 LiquidDock 桌面布局备份文件")
            }
            if (manifest.optInt("_version", -1) != VERSION) {
                throw IOException("不支持的桌面布局备份版本")
            }
            if (restoredFiles == 0) {
                throw IOException("备份文件中未包含有效的桌面布局数据")
            }

            val gridRestored = restoreGridPreferences(prefs, manifest.optJSONObject("grid"))
            // Publish updated grid preferences into native conf BEFORE stopping com.miui.home.
            Os4ConfigSync.prepareForManualLauncherRestart(context)

            val stagePath = stageDir.absolutePath
            val hasBins = File(stageDir, "files").listFiles()?.isNotEmpty() == true
            val hasMainDb = File(stageDir, "databases").listFiles()?.any { it.isFile } == true
            val hasLaptopDb = File(stageDir, "databases/laptop").listFiles()?.any { it.isFile } == true
            val cmd = buildString {
                appendLine("set -eu")
                appendLine("STAGE=\"$stagePath\"")
                appendLine("HOME_DE=\"/data/user_de/0/com.miui.home\"")
                appendLine("U=\"\$(stat -c %u \"\$HOME_DE/files\")\"")
                appendLine("install_file() {")
                appendLine("    cp -f \"\$1\" \"\$2\"")
                appendLine("    chown \"\$U:\$U\" \"\$2\"")
                appendLine("    chmod 600 \"\$2\"")
                appendLine("    chcon --reference=\"\$HOME_DE/files\" \"\$2\" 2>/dev/null || true")
                appendLine("}")
                if (hasBins) {
                    appendLine("for f in liquiddock-layout-v3.bin liquiddock-ws-desk-v3.bin liquiddock-ws-layout-v3.bin; do")
                    appendLine("    [ -f \"\$STAGE/files/\$f\" ] && install_file \"\$STAGE/files/\$f\" \"\$HOME_DE/files/\$f\"")
                    appendLine("done")
                }
                if (hasMainDb) {
                    appendLine("rm -f \"\$HOME_DE/databases/\"launcher_rotatable*.db \"\$HOME_DE/databases/\"launcher_rotatable*.db-shm \"\$HOME_DE/databases/\"launcher_rotatable*.db-wal")
                    appendLine("for f in \"\$STAGE/databases/\"launcher_rotatable*; do")
                    appendLine("    [ -f \"\$f\" ] || continue")
                    appendLine("    install_file \"\$f\" \"\$HOME_DE/databases/\$(basename \"\$f\")\"")
                    appendLine("done")
                }
                if (hasLaptopDb) {
                    appendLine("mkdir -p \"\$HOME_DE/databases/laptop\"")
                    appendLine("chown \"\$U:\$U\" \"\$HOME_DE/databases/laptop\"")
                    appendLine("chmod 700 \"\$HOME_DE/databases/laptop\"")
                    appendLine("chcon --reference=\"\$HOME_DE/databases\" \"\$HOME_DE/databases/laptop\" 2>/dev/null || true")
                    appendLine("rm -f \"\$HOME_DE/databases/laptop/\"launcher_rotatable*.db \"\$HOME_DE/databases/laptop/\"launcher_rotatable*.db-shm \"\$HOME_DE/databases/laptop/\"launcher_rotatable*.db-wal")
                    appendLine("for f in \"\$STAGE/databases/laptop/\"launcher_rotatable*; do")
                    appendLine("    [ -f \"\$f\" ] || continue")
                    appendLine("    install_file \"\$f\" \"\$HOME_DE/databases/laptop/\$(basename \"\$f\")\"")
                    appendLine("done")
                }
                appendLine("echo RESULT_OK")
            }
            runRootCommand(cmd, "恢复桌面布局")
            return RestoreSummary(restoredFiles, gridRestored)
        } finally {
            stageDir.deleteRecursively()
        }
    }

    private fun readAsciiLine(dis: DataInputStream): String? {
        val baos = ByteArrayOutputStream()
        while (true) {
            val b = dis.read()
            if (b == -1) {
                return if (baos.size() == 0) null else baos.toString(Charsets.UTF_8.name())
            }
            if (b == '\n'.code) {
                return baos.toString(Charsets.UTF_8.name()).trimEnd('\r')
            }
            baos.write(b)
        }
    }

    private fun buildManifest(prefs: SharedPreferences, files: List<String>): JSONObject {
        val grid = JSONObject().apply {
            put(ConfigSchema.Grid.ENABLED.name(), prefs.getBoolean(ConfigSchema.Grid.ENABLED.name(), ConfigSchema.Grid.ENABLED.uiDefault()))
            put(ConfigSchema.Grid.COLUMNS.name(), prefs.getInt(ConfigSchema.Grid.COLUMNS.name(), ConfigSchema.Grid.COLUMNS.uiDefault()))
            put(ConfigSchema.Grid.ROWS.name(), prefs.getInt(ConfigSchema.Grid.ROWS.name(), ConfigSchema.Grid.ROWS.uiDefault()))
            put(ConfigSchema.Grid.FOLDER_GRID_ENABLED.name(), prefs.getBoolean(ConfigSchema.Grid.FOLDER_GRID_ENABLED.name(), ConfigSchema.Grid.FOLDER_GRID_ENABLED.uiDefault()))
            put(ConfigSchema.Grid.FOLDER_GRID_COLUMNS.name(), prefs.getInt(ConfigSchema.Grid.FOLDER_GRID_COLUMNS.name(), ConfigSchema.Grid.FOLDER_GRID_COLUMNS.uiDefault()))
            put(ConfigSchema.Grid.FOLDER_GRID_ROWS.name(), prefs.getInt(ConfigSchema.Grid.FOLDER_GRID_ROWS.name(), ConfigSchema.Grid.FOLDER_GRID_ROWS.uiDefault()))
        }
        val fileArr = JSONArray()
        files.forEach { fileArr.put(it) }
        return JSONObject().apply {
            put("_format", FORMAT)
            put("_version", VERSION)
            put("exported_at", System.currentTimeMillis())
            put("grid", grid)
            put("files", fileArr)
        }
    }

    private fun restoreGridPreferences(prefs: SharedPreferences, grid: JSONObject?): Boolean {
        if (grid == null) return false
        val editor = prefs.edit()
        var changed = false
        if (grid.has(ConfigSchema.Grid.ENABLED.name())) {
            editor.putBoolean(ConfigSchema.Grid.ENABLED.name(), grid.optBoolean(ConfigSchema.Grid.ENABLED.name(), false))
            changed = true
        }
        if (grid.has(ConfigSchema.Grid.COLUMNS.name())) {
            val cols = grid.optInt(ConfigSchema.Grid.COLUMNS.name(), ConfigSchema.Grid.COLUMNS.uiDefault())
                .coerceIn(ConfigSchema.Grid.COLUMNS.minInt()!!, ConfigSchema.Grid.COLUMNS.maxInt()!!)
            editor.putInt(ConfigSchema.Grid.COLUMNS.name(), cols)
            changed = true
        }
        if (grid.has(ConfigSchema.Grid.ROWS.name())) {
            val rows = grid.optInt(ConfigSchema.Grid.ROWS.name(), ConfigSchema.Grid.ROWS.uiDefault())
                .coerceIn(ConfigSchema.Grid.ROWS.minInt()!!, ConfigSchema.Grid.ROWS.maxInt()!!)
            editor.putInt(ConfigSchema.Grid.ROWS.name(), rows)
            changed = true
        }
        if (grid.has(ConfigSchema.Grid.FOLDER_GRID_ENABLED.name())) {
            editor.putBoolean(ConfigSchema.Grid.FOLDER_GRID_ENABLED.name(), grid.optBoolean(ConfigSchema.Grid.FOLDER_GRID_ENABLED.name(), false))
            changed = true
        }
        if (grid.has(ConfigSchema.Grid.FOLDER_GRID_COLUMNS.name())) {
            val fcols = grid.optInt(ConfigSchema.Grid.FOLDER_GRID_COLUMNS.name(), ConfigSchema.Grid.FOLDER_GRID_COLUMNS.uiDefault())
                .coerceIn(ConfigSchema.Grid.FOLDER_GRID_COLUMNS.minInt()!!, ConfigSchema.Grid.FOLDER_GRID_COLUMNS.maxInt()!!)
            editor.putInt(ConfigSchema.Grid.FOLDER_GRID_COLUMNS.name(), fcols)
            changed = true
        }
        if (grid.has(ConfigSchema.Grid.FOLDER_GRID_ROWS.name())) {
            val frows = grid.optInt(ConfigSchema.Grid.FOLDER_GRID_ROWS.name(), ConfigSchema.Grid.FOLDER_GRID_ROWS.uiDefault())
                .coerceIn(ConfigSchema.Grid.FOLDER_GRID_ROWS.minInt()!!, ConfigSchema.Grid.FOLDER_GRID_ROWS.maxInt()!!)
            editor.putInt(ConfigSchema.Grid.FOLDER_GRID_ROWS.name(), frows)
            changed = true
        }
        if (changed && !editor.commit()) {
            throw IOException("保存自定义网格设置失败")
        }
        return changed
    }

    internal fun isAllowedEntryName(name: String): Boolean {
        if (name.contains("..") || name.startsWith("/")) return false
        return when {
            name.startsWith("files/") -> name.removePrefix("files/") in ALLOWED_BIN_FILES
            name.startsWith("databases/laptop/") -> ALLOWED_DB_REGEX.matches(name.removePrefix("databases/laptop/"))
            name.startsWith("databases/") -> ALLOWED_DB_REGEX.matches(name.removePrefix("databases/"))
            else -> false
        }
    }

    @Throws(IOException::class)
    private fun runRootCommand(command: String, action: String) {
        val process = ProcessBuilder("su", "-c", "nsenter -t 1 -m -- sh -eu")
            .redirectErrorStream(true)
            .start()
        process.outputStream.use { it.write((command + "\n").toByteArray(Charsets.UTF_8)) }
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        val ok = exitCode == 0 && output.lineSequence().any { it.trim() == "RESULT_OK" }
        if (!ok) {
            Log.e(TAG, "$action failed (exit=$exitCode): $output")
            throw IOException("${action}失败（请检查 Root 权限）")
        }
    }
}
