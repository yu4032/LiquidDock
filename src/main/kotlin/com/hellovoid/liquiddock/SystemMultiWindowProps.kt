package com.hellovoid.liquiddock

import android.content.SharedPreferences
import java.util.concurrent.TimeUnit

internal object SystemMultiWindowProps {
    const val MIN_STAGES = 3
    const val MAX_STAGES = 6
    const val DEFAULT_STAGES = 6

    private const val KEY_MAX_STAGES = "persist.sys.multiple.split.max_stages"
    private const val KEY_MULTIPLE_FEATURE = "persist.sys.split.multiplefeature"

    private data class PropSpec(val key: String, val targetValue: String)

    private val fixedSpecs = listOf(
        PropSpec(KEY_MULTIPLE_FEATURE, "true"),
        PropSpec("persist.sys.computilityV2.cpulevel", "3"),
        PropSpec("persist.sys.computilityV2.gpulevel", "3"),
        PropSpec("persist.sys.computilityV2.devicelevel", "3"),
        PropSpec("persist.sys.computilityV2.version", "2026"),
    )

    private val allKeys = listOf(KEY_MAX_STAGES) + fixedSpecs.map { it.key }

    private const val BACKUP_PREFIX = "sys_multi_window_orig_"
    private const val BACKUP_SAVED_KEY = "sys_multi_window_backup_saved"
    private const val ABSENT_SENTINEL = "__LD_PROP_ABSENT__"

    fun currentStageCountOrNull(): Int? = readProp(KEY_MAX_STAGES).toIntOrNull()?.takeIf {
        it in MIN_STAGES..MAX_STAGES
    }

    fun currentStageCount(): Int = currentStageCountOrNull() ?: DEFAULT_STAGES

    fun isEnabled(): Boolean =
        currentStageCountOrNull() != null && fixedSpecs.all { readProp(it.key) == it.targetValue }

    fun setEnabled(
        prefs: SharedPreferences,
        enable: Boolean,
        stageCount: Int = DEFAULT_STAGES,
    ): Boolean {
        val clamped = stageCount.coerceIn(MIN_STAGES, MAX_STAGES)
        if (enable) {
            saveOriginalBackupIfNeeded(prefs)
            val cmds = buildList {
                add(setPropCommand(KEY_MAX_STAGES, clamped.toString()))
                fixedSpecs.forEach { add(setPropCommand(it.key, it.targetValue)) }
            }.joinToString(" && ")
            if (!runSu(cmds)) return false
            return isEnabled()
        } else {
            val hasBackup = prefs.getBoolean(BACKUP_SAVED_KEY, false)
            val cmds = allKeys.joinToString(" && ") { key ->
                val orig = if (hasBackup) prefs.getString(BACKUP_PREFIX + key, ABSENT_SENTINEL) else ABSENT_SENTINEL
                if (orig.isNullOrEmpty() || orig == ABSENT_SENTINEL) {
                    clearPropCommand(key)
                } else {
                    setPropCommand(key, orig)
                }
            }
            if (!runSu(cmds)) return false
            if (hasBackup) {
                prefs.edit().apply {
                    remove(BACKUP_SAVED_KEY)
                    allKeys.forEach { remove(BACKUP_PREFIX + it) }
                }.apply()
            }
            return !isEnabled()
        }
    }

    fun updateMaxStages(prefs: SharedPreferences, stageCount: Int): Boolean {
        val clamped = stageCount.coerceIn(MIN_STAGES, MAX_STAGES)
        saveOriginalBackupIfNeeded(prefs)
        val cmds = buildList {
            add(setPropCommand(KEY_MAX_STAGES, clamped.toString()))
            fixedSpecs.forEach { add(setPropCommand(it.key, it.targetValue)) }
        }.joinToString(" && ")
        if (!runSu(cmds)) return false
        return currentStageCountOrNull() == clamped
    }

    private fun saveOriginalBackupIfNeeded(prefs: SharedPreferences) {
        if (prefs.getBoolean(BACKUP_SAVED_KEY, false)) return
        prefs.edit().apply {
            allKeys.forEach { key ->
                val cur = readProp(key)
                putString(BACKUP_PREFIX + key, if (cur.isEmpty()) ABSENT_SENTINEL else cur)
            }
            putBoolean(BACKUP_SAVED_KEY, true)
        }.apply()
    }

    private fun setPropCommand(key: String, value: String): String {
        val safeKey = sanitize(key)
        val safeVal = sanitize(value)
        return "(command -v resetprop >/dev/null 2>&1 && resetprop $safeKey $safeVal || setprop $safeKey $safeVal)"
    }

    private fun clearPropCommand(key: String): String {
        val safeKey = sanitize(key)
        return "(command -v resetprop >/dev/null 2>&1 && (resetprop --delete $safeKey >/dev/null 2>&1 || true) || setprop $safeKey '')"
    }

    private fun sanitize(input: String): String {
        require(input.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid prop token: $input" }
        return input
    }

    private fun readProp(key: String): String = runCatching {
        val process = ProcessBuilder("getprop", key)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(2, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@runCatching ""
        }
        process.inputStream.bufferedReader().use { it.readText() }.trim()
    }.getOrDefault("")

    private fun runSu(command: String): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(5, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@runCatching false
        }
        process.exitValue() == 0
    }.getOrDefault(false)
}
