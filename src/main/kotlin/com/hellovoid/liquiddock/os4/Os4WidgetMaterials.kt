package com.hellovoid.liquiddock.os4

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.hellovoid.liquiddock.WidgetComponentStore
import com.hellovoid.liquiddock.config.ConfigSchema
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Owns the OS4-only MAML catalog and immutable Launcher-side shadow archives. */
object Os4WidgetMaterials {
    private const val TAG = "LiquidDock-OS4Widget"
    private const val PREFS = "os4_widget_materials"
    private const val SOURCES = "sources_v1"
    private const val PUBLISH_KEY = "publish_key"
    private const val CACHE_PREFIX = "shadow_"
    private const val TRANSFORM_VERSION = "maml-glass-support-background-blur-v2"
    private const val CE_PREFIX = "/data/user/0/com.miui.home/files/maml/res/"
    private const val DE_PREFIX = "/data/user_de/0/com.miui.home/files/maml/res/"
    private const val SHADOW_DIR = "/data/user_de/0/com.miui.home/files/liquiddock-widget-shadows"
    private const val MAX_PATH = 511
    private const val MAX_WIDGETS = 64

    private data class Source(val path: String, val sha: String, val product: String, val basename: String, val file: File)
    private data class Published(val source: Source, val shadowSha: String, val shadow: File)

    /** Manual discovery only. The inventory is populated by the Launcher after native widget load. */
    @JvmStatic
    @Synchronized
    fun scan(context: Context): Result<Int> = runCatching {
        val app = context.applicationContext
        val cache = File(app.filesDir, "os4-widget-sources").apply { mkdirs() }
        val entries = readInventory(cache)
        require(entries.sumOf { it.second } <= 256L * 1024 * 1024) { "Loaded MAML sources exceed the scan budget" }
        val sources = ArrayList<Source>()
        for ((path, size, expectedSha) in entries) {
            val sha = expectedSha
            val product = Os4MamlArchive.productIdFromPath(path)
                ?: error("Could not determine MAML product id from source path")
            val base = path.substringAfterLast('/').removeSuffix(".zip")
            val local = File(cache, "$sha.zip")
            require(local.isFile && local.length() == size && sha256(local) == sha) { "Launcher MAML source changed during scan" }
            val source = Source(path, sha, product, base, local)
            sources += source
        }
        val catalog = LinkedHashSet<String>()
        for (source in sources) {
            val owner = owner(source)
            Os4MamlArchive.inspect(Os4MamlArchive.FileLike(source.file), owner).nodes
                .forEach { catalog += it.catalog(owner) }
        }
        saveCatalog(app, catalog)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(SOURCES, encodeSources(sources)).remove(PUBLISH_KEY).apply()
        val ok = publish(app, PreferenceManager.getDefaultSharedPreferences(app), force = true)
        if (!ok) error("Could not publish MAML component overrides")
        catalog.size
    }.onFailure { error ->
        Log.e(TAG, "Manual MAML scan failed; clearing stale mapping", error)
        clearMapping()
    }

    /** Rebuilds the active mapping from cached scan results and current exact M2 selections. */
    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun publish(context: Context, preferences: SharedPreferences, force: Boolean = false): Boolean {
        val app = context.applicationContext
        val state = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val selected = try {
            (preferences.all[WidgetComponentStore.SELECTION_KEY] as? Set<*>)
                ?.filterIsInstance<String>()?.toSet().orEmpty()
        } catch (_: Throwable) { emptySet() }
        val globalGlass = preferences.getBoolean(ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault())
        val widgetGlass = globalGlass && preferences.getBoolean(ConfigSchema.Glass.WIDGET_GLASS.name(), ConfigSchema.Glass.WIDGET_GLASS.uiDefault())
        val active = preferences.getBoolean(ConfigSchema.Core.ENABLED.name(), ConfigSchema.Core.ENABLED.uiDefault())
        val glass = active && widgetGlass
        val sourceState = state.getString(SOURCES, "").orEmpty()
        val fingerprint = sha256(("permissions-v2\ntransform=" + TRANSFORM_VERSION + "\n" + sourceState + "\nselected=" + selected.sorted().joinToString("\n") +
            "\nglass=$glass\nactive=$active").toByteArray())
        if (!force && state.getString(PUBLISH_KEY, null) == fingerprint) return true
        val catalog = app.getSharedPreferences(WidgetComponentStore.CATALOG_PREFS, Context.MODE_PRIVATE)
            .getStringSet(WidgetComponentStore.CATALOG_KEY, emptySet()).orEmpty()
        val sourceList = try { decodeSources(sourceState, app.filesDir) }
            catch (error: Throwable) { Log.e(TAG, "Invalid cached MAML source list", error); return clearMapping() }
        val knownSelectors = catalog.mapNotNull(WidgetComponentStore::parseCatalog)
            .filter { it.isExactMamlRender() }.associateBy { it.selectorKey() }
        val selectedPaths = LinkedHashMap<String, MutableSet<String>>()
        for (encoded in selected) {
            val descriptor = WidgetComponentStore.parseSelector(encoded) ?: continue
            if (!descriptor.isExactMamlRender() || !descriptor.owner.startsWith("os4:")) continue
            val known = knownSelectors[descriptor.selectorKey()] ?: continue
            val match = sourceList.firstOrNull { owner(it) == known.owner } ?: continue
            selectedPaths.getOrPut(match.sha) { linkedSetOf() } += known.hierarchyPath
        }
        val rulesExist = selectedPaths.isNotEmpty() || glass
        if (!active || !rulesExist) {
            val cleared = clearMapping()
            if (cleared) state.edit().putString(PUBLISH_KEY, fingerprint).apply()
            return cleared
        }
        return try {
            val cache = File(app.filesDir, "os4-widget-shadows").apply { mkdirs() }
            val published = ArrayList<Published>()
            for (source in sourceList) {
                val paths = selectedPaths[source.sha].orEmpty()
                if (paths.isEmpty() && !glass) continue
                val key = sha256((TRANSFORM_VERSION + "\n" + source.sha + "\n" + paths.sorted().joinToString("\n") + "\nglass=$glass").toByteArray())
                val shadow = File(cache, "$key.zip")
                val outputSha = state.getString(CACHE_PREFIX + key, null)
                if (!shadow.exists() || outputSha == null || sha256(shadow.readBytes()) != outputSha) {
                    val changed = Os4MamlArchive.rewrite(
                        Os4MamlArchive.FileLike(source.file), Os4MamlArchive.FileLike(shadow), paths, glass,
                    )
                    if (!changed) {
                        shadow.delete()
                        continue
                    }
                    state.edit().putString(CACHE_PREFIX + key, sha256(shadow.readBytes())).apply()
                }
                val outSha = sha256(shadow.readBytes())
                published += Published(source, outSha, shadow)
            }
            if (published.isEmpty()) {
                val cleared = clearMapping()
                if (cleared) state.edit().putString(PUBLISH_KEY, fingerprint).apply()
                cleared
            } else {
                val result = deploy(published)
                if (result) state.edit().putString(PUBLISH_KEY, fingerprint).apply()
                if (!result) clearMapping() else true
            }
        } catch (error: Throwable) {
            Log.e(TAG, "MAML override generation failed; clearing stale mapping", error)
            clearMapping()
        }
    }

    private fun saveCatalog(context: Context, os4Rows: Set<String>) {
        val prefs = context.getSharedPreferences(WidgetComponentStore.CATALOG_PREFS, Context.MODE_PRIVATE)
        val kept = prefs.getStringSet(WidgetComponentStore.CATALOG_KEY, emptySet()).orEmpty()
            .filterNot { row -> WidgetComponentStore.parseCatalog(row)?.owner?.startsWith("os4:") == true }
            .toMutableSet()
        kept.addAll(os4Rows)
        prefs.edit().putStringSet(WidgetComponentStore.CATALOG_KEY, kept).apply()
    }

    private fun owner(source: Source) = "os4:${source.product}:${source.basename}"

    private fun readInventory(cache: File): List<Triple<String, Long, String>> {
        val command = """
            set -eu
            P="${'$'}(pidof -s com.miui.home || true)"
            case "${'$'}P" in ""|*[!0-9]*) exit 20 ;; esac
            nsenter -t "${'$'}P" -m -- sh -eu -c '
              f=/data/user_de/0/com.miui.home/files/liquiddock-widget-sources.txt
              [ -f "${'$'}f" ] && [ ! -L "${'$'}f" ] || exit 21
              printf "WIDGETS1\n"
              n=0
              while IFS= read -r p || [ -n "${'$'}p" ]; do
                [ -n "${'$'}p" ] || continue
                n=${'$'}((n+1)); [ "${'$'}n" -le 64 ] || exit 22
                case "${'$'}p" in /data/user/0/com.miui.home/files/maml/res/*|/data/user_de/0/com.miui.home/files/maml/res/*) ;; *) exit 23 ;; esac
                case "${'$'}p" in *widget_[1-9]x[1-9]) ;; *) exit 23 ;; esac
                case "${'$'}p" in *..*|*//*|*[!a-zA-Z0-9_./-]*) exit 23 ;; esac
                [ "${'$'}{#p}" -le 511 ] && [ -f "${'$'}p" ] && [ ! -L "${'$'}p" ] || exit 24
                size="${'$'}(stat -c %s "${'$'}p")"
                [ "${'$'}size" -le 134217728 ] || exit 25
                hash="${'$'}(sha256sum "${'$'}p" | cut -d " " -f 1)"
                printf "FILE\t%s\t%s\t%s\n" "${'$'}p" "${'$'}size" "${'$'}hash"
                cat "${'$'}p"
                printf "\n"
              done < "${'$'}f"
              printf "DONE\n"
            '
        """.trimIndent()
        val process = ProcessBuilder("su", "-c", command).start()
        val stdout = process.inputStream.buffered()
        val stderrThread = Thread { runCatching { process.errorStream.use { it.copyTo(ByteArrayOutputStream()) } } }.apply { start() }
        try {
            val watchdog = Thread({
                if (!process.waitFor(60, TimeUnit.SECONDS)) process.destroyForcibly()
            }, "os4-widget-inventory-timeout").apply { isDaemon = true; start() }
            val first = readLineBounded(stdout, 64)
            require(first == "WIDGETS1") { "Invalid Launcher MAML inventory" }
            val rows = ArrayList<Triple<String, Long, String>>()
            var totalBytes = 0L
            while (true) {
                val line = readLineBounded(stdout, 1024)
                if (line == "DONE") break
                val fields = line.split('\t')
                require(fields.size == 4 && fields[0] == "FILE") { "Invalid MAML inventory row" }
                val path = fields[1]
                validateSourcePath(path)
                val size = fields[2].toLong().also { require(it in 1..Os4MamlArchive.MAX_ARCHIVE_BYTES) }
                totalBytes += size
                require(totalBytes <= 256L * 1024 * 1024) { "Loaded MAML sources exceed the scan budget" }
                require(fields[3].matches(Regex("[a-f0-9]{64}")))
                val bytes = readExactly(stdout, size.toInt())
                require(stdout.read() == '\n'.code) { "Bad MAML inventory framing" }
                val sha = sha256(bytes)
                require(sha == fields[3]) { "Launcher MAML inventory hash mismatch" }
                val sink = File(cache, "$sha.zip")
                if (!sink.isFile || sha256(sink) != sha) sink.writeBytes(bytes)
                rows += Triple(path, size, sha)
                require(rows.size <= MAX_WIDGETS)
            }
            require(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "Launcher MAML inventory read failed" }
            watchdog.join(100)
            stderrThread.join(1000)
            return rows
        } catch (error: Throwable) {
            process.destroyForcibly()
            throw error
        }
    }

    private fun deploy(items: List<Published>): Boolean {
        val valid = items.take(MAX_WIDGETS)
        val total = valid.sumOf { it.shadow.length() }
        if (valid.size != items.size || total > 256L * 1024 * 1024) return false
        val command = """
            set -eu
            P="${'$'}(pidof -s com.miui.home || true)"
            case "${'$'}P" in ""|*[!0-9]*) exit 20 ;; esac
            nsenter -t "${'$'}P" -m -- sh -eu -c '
              umask 077
              DE=/data/user_de/0/com.miui.home/files
              [ -d "${'$'}DE" ] || exit 21
              D="${'$'}DE/liquiddock-widget-shadows"
              [ ! -L "${'$'}D" ] || exit 21
              mkdir -p "${'$'}D"
              U="${'$'}(stat -c %u "${'$'}DE")"
              chown "${'$'}U:${'$'}U" "${'$'}D"; chmod 700 "${'$'}D"
              restorecon -F "${'$'}D"
              IFS= read -r head; [ "${'$'}head" = "WIDGETS1" ] || exit 22
              ROWS="${'$'}(mktemp "${'$'}DE/.liquiddock-widget-rows.XXXXXX")"
              trap "rm -f ${'$'}ROWS" EXIT
              n=0
              while IFS= read -r line; do
                [ "${'$'}line" = DONE ] && break
                IFS="$(printf "\t")" read -r srcsha outsha src dst size <<EOF
${'$'}line
EOF
                case "${'$'}srcsha" in *[!a-f0-9]*) exit 23 ;; esac
                case "${'$'}outsha" in *[!a-f0-9]*) exit 23 ;; esac
                [ "${'$'}{#srcsha}" -eq 64 ] && [ "${'$'}{#outsha}" -eq 64 ] || exit 23
                case "${'$'}src" in /data/user/0/com.miui.home/files/maml/res/*|/data/user_de/0/com.miui.home/files/maml/res/*) ;; *) exit 23 ;; esac
                case "${'$'}src" in *widget_[1-9]x[1-9]) ;; *) exit 23 ;; esac
                [ "${'$'}dst" = "${'$'}D/${'$'}outsha.zip" ] || exit 23
                case "${'$'}src" in *..*|*//*|*[!a-zA-Z0-9_./-]*) exit 23 ;; esac
                [ "${'$'}{#src}" -le 511 ] && [ "${'$'}size" -le 134217728 ] || exit 24
                [ -f "${'$'}src" ] && [ ! -L "${'$'}src" ] || exit 24
                [ "${'$'}(sha256sum "${'$'}src" | cut -d " " -f 1)" = "${'$'}srcsha" ] || exit 25
                t="${'$'}(mktemp "${'$'}D/.shadow.XXXXXX")"
                head -c "${'$'}size" > "${'$'}t"
                [ "${'$'}(sha256sum "${'$'}t" | cut -d " " -f 1)" = "${'$'}outsha" ] || exit 26
                chown "${'$'}U:${'$'}U" "${'$'}t"; chmod 600 "${'$'}t"; restorecon -F "${'$'}t"; mv -f "${'$'}t" "${'$'}dst"
                printf "%s\t%s\t%s\t%s\n" "${'$'}srcsha" "${'$'}outsha" "${'$'}src" "${'$'}dst" >> "${'$'}ROWS"
                n=${'$'}((n+1)); [ "${'$'}n" -le 64 ] || exit 27
              done
              [ "${'$'}n" -le 64 ] || exit 27
              t="${'$'}(mktemp "${'$'}DE/.liquiddock-widget-map.XXXXXX")"
              { printf "version=1\n"; cat "${'$'}ROWS"; printf "end=1\n"; } > "${'$'}t"
              [ "${'$'}(stat -c %s "${'$'}t")" -le 65536 ] || exit 28
              chown "${'$'}U:${'$'}U" "${'$'}t"; chmod 600 "${'$'}t"; restorecon -F "${'$'}t"
              mv -f "${'$'}t" "${'$'}DE/liquiddock-widget-overrides.conf"
              echo RESULT_OK
            '
        """.trimIndent()
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        BufferedOutputStream(process.outputStream).use { out ->
            out.write("WIDGETS1\n".toByteArray())
            for (item in valid) {
                val dest = "$SHADOW_DIR/${item.shadowSha}.zip"
                val size = item.shadow.length()
                out.write("${item.source.sha}\t${item.shadowSha}\t${item.source.path}\t$dest\t$size\n".toByteArray())
                item.shadow.inputStream().use { it.copyTo(out) }
            }
            out.write("DONE\n".toByteArray())
        }
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor(90, TimeUnit.SECONDS) && process.exitValue() == 0 && output.lineSequence().any { it == "RESULT_OK" }
    }

    private fun clearMapping(): Boolean {
        val command = """
            set -eu
            P="${'$'}(pidof -s com.miui.home || true)"
            case "${'$'}P" in ""|*[!0-9]*) exit 20 ;; esac
            nsenter -t "${'$'}P" -m -- sh -eu -c '
              D=/data/user_de/0/com.miui.home/files
              [ -d "${'$'}D" ] || exit 21
              U="${'$'}(stat -c %u "${'$'}D")"; umask 077
              t="${'$'}(mktemp "${'$'}D/.liquiddock-widget-clear.XXXXXX")"
              printf "version=1\nend=1\n" > "${'$'}t"
              chown "${'$'}U:${'$'}U" "${'$'}t"; chmod 600 "${'$'}t"; restorecon -F "${'$'}t"
              mv -f "${'$'}t" "${'$'}D/liquiddock-widget-overrides.conf"
              echo RESULT_OK
            '
        """.trimIndent()
        return runCatching {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0 && output.lineSequence().any { it == "RESULT_OK" }
        }.getOrDefault(false)
    }

    private fun encodeSources(sources: List<Source>): String = sources.joinToString("\n") {
        listOf(it.sha, it.path, it.product, it.basename).joinToString("\t")
    }

    private fun decodeSources(value: String, filesDir: File): List<Source> {
        if (value.isBlank()) return emptyList()
        val rows = value.lineSequence().filter(String::isNotBlank).map { line ->
            val f = line.split('\t'); require(f.size == 4 && f[0].matches(Regex("[a-f0-9]{64}")))
            validateSourcePath(f[1]); require(f[2].isNotBlank() && f[3].matches(Regex("widget_[1-9]x[1-9]")))
            val file = File(filesDir, "os4-widget-sources/${f[0]}.zip")
            require(file.isFile && sha256(file) == f[0]) { "Cached MAML source missing or changed" }
            Source(f[1], f[0], f[2], f[3], file)
        }.toList()
        require(rows.size <= MAX_WIDGETS)
        return rows
    }

    private fun validateSourcePath(path: String) {
        require(path.length <= MAX_PATH && !path.contains("..") && !path.contains("//") && '\t' !in path && '\n' !in path)
        require((path.startsWith(CE_PREFIX) || path.startsWith(DE_PREFIX)) &&
            path.substringAfterLast('/').matches(Regex("widget_[1-9]x[1-9]"))) { "Unsafe MAML source path" }
    }

    private fun readLineBounded(input: InputStream, limit: Int): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val byte = input.read(); require(byte >= 0) { "Truncated Launcher inventory" }
            if (byte == '\n'.code) break
            require(out.size() < limit) { "Inventory row too long" }; out.write(byte)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val n = input.read(out, offset, length - offset); require(n > 0) { "Truncated MAML archive" }; offset += n
        }
        return out
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
