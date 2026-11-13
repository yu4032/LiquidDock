package com.hellovoid.liquiddock.os4

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.hellovoid.liquiddock.WidgetComponentStore
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Scans and deploys RemoteViews component hiding rules for OS4 Launcher's Dart AppWidgetHost. */
object Os4RemoteWidgetCatalog {
    private const val TAG = "LiquidDock-OS4Remote"
    private const val PREFS = "os4_remote_widgets"
    private const val ID_MAP_KEY = "id_map_v1"
    const val OWNER_PREFIX = "os4rv:"

    data class ObservedView(val appWidgetId: Int, val viewId: Int, val hasBg: Boolean)

    @JvmStatic
    fun scan(context: Context): Set<String> {
        val app = context.applicationContext
        val raw = runSu("""
            echo "===VIEWS==="
            nsenter -t "${'$'}(pidof -s com.miui.home || true)" -m -- cat /data/user_de/0/com.miui.home/files/liquiddock-remote-views.txt 2>/dev/null || true
            echo "===WIDGETS==="
            dumpsys appwidget 2>/dev/null | grep -E "id=|host=|provider=" || true
        """.trimIndent()) ?: return emptySet()

        val views = ArrayList<ObservedView>()
        val idToProvider = LinkedHashMap<Int, ComponentName>()
        var section = ""
        var curId = -1
        var curHostHome = false
        for (line in raw.lineSequence()) {
            when (line.trim()) {
                "===VIEWS===" -> { section = "views"; continue }
                "===WIDGETS===" -> { section = "widgets"; continue }
            }
            if (section == "views") {
                val p = line.split('\t')
                if (p.size >= 3) {
                    val wid = p[0].toIntOrNull() ?: continue
                    val vid = p[1].toIntOrNull() ?: continue
                    val hasBg = p[2] == "1"
                    if (wid > 0 && vid != 0) views += ObservedView(wid, vid, hasBg)
                }
            } else if (section == "widgets") {
                val t = line.trim()
                val idMatch = Regex("""\[\d+\]\s+id=(\d+)""").find(t)
                if (idMatch != null) {
                    curId = idMatch.groupValues[1].toIntOrNull() ?: -1
                    curHostHome = false
                } else if (t.startsWith("host=")) {
                    curHostHome = t.contains("pkg:com.miui.home")
                } else if (t.startsWith("provider=") && curId > 0 && curHostHome) {
                    val cmp = Regex("""ComponentInfo\{([^}]+)\}""").find(t)?.groupValues?.get(1)
                    val cn = cmp?.let(ComponentName::unflattenFromString)
                    if (cn != null) idToProvider[curId] = cn
                }
            }
        }

        if (idToProvider.isNotEmpty()) {
            val encoded = idToProvider.entries.joinToString("\n") { "${it.key}\t${it.value.flattenToString()}" }
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ID_MAP_KEY, encoded).apply()
        }

        val pm = app.packageManager
        val resCache = HashMap<String, android.content.res.Resources?>()
        val labelCache = HashMap<String, String>()
        val catalog = LinkedHashSet<String>()
        for (v in views) {
            val cn = idToProvider[v.appWidgetId] ?: continue
            val pkg = cn.packageName
            val owner = "$OWNER_PREFIX$pkg"
            val appLabel = labelCache.getOrPut(pkg) {
                runCatching {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString()
                }.getOrDefault(pkg)
            }
            val res = resCache.getOrPut(pkg) {
                runCatching { pm.getResourcesForApplication(pkg) }.getOrNull()
            }
            val resName = if (res != null) {
                runCatching { res.getResourceEntryName(v.viewId) }.getOrDefault("0x${Integer.toHexString(v.viewId)}")
            } else {
                "0x${Integer.toHexString(v.viewId)}"
            }
            val type = if (v.hasBg) WidgetComponentStore.TYPE_BACKGROUND else WidgetComponentStore.TYPE_OTHER
            val desc = WidgetComponentStore.remoteDescriptor(
                owner,
                WidgetComponentStore.ACTION_CLEAR_BACKGROUND,
                resName,
                "FlutterView",
                v.viewId.toString(),
                type,
                appLabel,
            ) ?: continue
            catalog += desc.encodeCatalog()
        }
        return catalog
    }

    @JvmStatic
    fun deployHiddenRules(context: Context, preferences: SharedPreferences): Boolean {
        val app = context.applicationContext
        val selected = try {
            (preferences.all[WidgetComponentStore.SELECTION_KEY] as? Set<*>)
                ?.filterIsInstance<String>()?.toSet().orEmpty()
        } catch (_: Throwable) { emptySet() }

        val mapRaw = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ID_MAP_KEY, "").orEmpty()
        val pkgToIds = LinkedHashMap<String, MutableList<Int>>()
        for (line in mapRaw.lineSequence()) {
            val p = line.split('\t')
            if (p.size != 2) continue
            val wid = p[0].toIntOrNull() ?: continue
            val cn = ComponentName.unflattenFromString(p[1]) ?: continue
            pkgToIds.getOrPut(cn.packageName) { ArrayList() } += wid
        }

        val lines = LinkedHashSet<String>()
        for (encoded in selected) {
            val d = WidgetComponentStore.parseSelector(encoded) ?: continue
            if (!d.isRemoteViews || !d.owner.startsWith(OWNER_PREFIX)) continue
            val pkg = d.owner.removePrefix(OWNER_PREFIX)
            val viewId = d.hierarchyPath.toIntOrNull() ?: continue
            for (wid in pkgToIds[pkg].orEmpty()) {
                lines += "$wid\t$viewId"
            }
        }
        val body = lines.joinToString("\n") + if (lines.isEmpty()) "" else "\n"
        val ok = writeLauncherFile(body)
        if (!ok) Log.w(TAG, "Failed to deploy OS4 RemoteViews hidden rules")
        return ok
    }

    private fun writeLauncherFile(content: String): Boolean {
        val b64 = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val cmd = """
            set -eu
            P="${'$'}(pidof -s com.miui.home || true)"
            case "${'$'}P" in ""|*[!0-9]*) exit 20 ;; esac
            nsenter -t "${'$'}P" -m -- sh -eu -c '
              D=/data/user_de/0/com.miui.home/files
              [ -d "${'$'}D" ] || exit 21
              U="${'$'}(stat -c %u "${'$'}D")"; umask 077
              t="${'$'}(mktemp "${'$'}D/.liquiddock-rv.XXXXXX")"
              printf "%s" "$b64" | base64 -d > "${'$'}t"
              chown "${'$'}U:${'$'}U" "${'$'}t"; chmod 600 "${'$'}t"; restorecon -F "${'$'}t"
              mv -f "${'$'}t" "${'$'}D/liquiddock-remote-hidden.conf"
            '
        """.trimIndent()
        return runSu(cmd) != null
    }

    private fun runSu(command: String): String? = runCatching {
        val process = ProcessBuilder("su", "-c", command).start()
        val err = Thread { runCatching { process.errorStream.use { it.copyTo(ByteArrayOutputStream()) } } }.apply { start() }
        val out = process.inputStream.bufferedReader().readText()
        val ok = process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0
        err.join(500)
        if (ok) out else null
    }.getOrNull()
}
