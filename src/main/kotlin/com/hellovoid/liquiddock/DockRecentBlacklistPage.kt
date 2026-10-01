package com.hellovoid.liquiddock

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import java.util.HashSet

private fun appLabel(packageManager: PackageManager, packageName: String): String {
    return try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString().takeIf { it.isNotBlank() } ?: packageName
    } catch (_: Throwable) {
        packageName
    }
}

@Composable
internal fun DockRecentBlacklistPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val candidatePrefs = remember {
        context.getSharedPreferences(DockRecentAppStore.CANDIDATE_PREFS, Context.MODE_PRIVATE)
    }
    var candidateRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(candidatePrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == DockRecentAppStore.CANDIDATE_KEY) candidateRevision++
        }
        candidatePrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { candidatePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val packages = remember(candidateRevision) {
        candidatePrefs.getStringSet(DockRecentAppStore.CANDIDATE_KEY, emptySet())
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.sortedBy { appLabel(context.packageManager, it).lowercase() }
            .orEmpty()
    }
    var blocked by remember {
        mutableStateOf(
            prefs.getStringSet(DockRecentAppStore.BLACKLIST_KEY, emptySet())?.toSet().orEmpty()
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            PageHeader(
                stringResource(R.string.page_dock_recent_blacklist),
                stringResource(R.string.dock_recent_blacklist_header_summary),
            )
        }
        item { SmallTitle(stringResource(R.string.dock_recent_blacklist_current_candidates)) }
        if (packages.isEmpty()) {
            item {
                SettingsCard {
                    Text(stringResource(R.string.dock_recent_blacklist_empty))
                }
            }
        } else {
            items(packages, key = { it }) { packageName ->
                val label = remember(packageName) { appLabel(context.packageManager, packageName) }
                SettingsCard {
                    SwitchPreference(
                        checked = blocked.contains(packageName),
                        onCheckedChange = { checked ->
                            val next = HashSet(blocked)
                            if (checked) next.add(packageName) else next.remove(packageName)
                            blocked = next
                            prefs.edit()
                                .putStringSet(DockRecentAppStore.BLACKLIST_KEY, HashSet(next))
                                .apply()
                        },
                        title = label,
                        summary = packageName,
                        enabled = masterEnabled,
                    )
                }
            }
        }
    }
}
