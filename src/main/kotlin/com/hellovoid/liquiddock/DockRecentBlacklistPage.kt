package com.hellovoid.liquiddock

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.widget.Toast
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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import java.util.HashSet
import java.util.UUID

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
    activity: ComposeSettingsActivity,
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

    var blocked by remember {
        mutableStateOf(
            prefs.getStringSet(DockRecentAppStore.BLACKLIST_KEY, emptySet())?.toSet().orEmpty()
        )
    }
    val currentCandidates = remember(candidateRevision) {
        candidatePrefs.getStringSet(DockRecentAppStore.CANDIDATE_KEY, emptySet())
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
    }
    val availableCandidates = (currentCandidates - blocked)
        .sortedBy { appLabel(context.packageManager, it).lowercase() }
    val blockedPackages = blocked
        .sortedBy { appLabel(context.packageManager, it).lowercase() }

    fun setBlocked(packageName: String, checked: Boolean) {
        val next = HashSet(blocked)
        if (checked) next.add(packageName) else next.remove(packageName)
        blocked = next
        val stored = prefs.edit()
            .putStringSet(DockRecentAppStore.BLACKLIST_KEY, HashSet(next))
            .commit()
        if (!stored || !LiquidDockApp.syncToRemote(prefs)) {
            Toast.makeText(
                activity,
                activity.getString(R.string.dock_recent_blacklist_apply_failed),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val token = prefs.getString(WidgetComponentStore.DISCOVERY_TOKEN_KEY, "").orEmpty()
        if (!LauncherManualDiscoveryBridge.requestDockRefresh(activity, token)) {
            Toast.makeText(
                activity,
                activity.getString(R.string.dock_recent_blacklist_apply_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            PageHeader(
                stringResource(R.string.page_dock_recent_blacklist),
                stringResource(R.string.dock_recent_blacklist_header_summary),
            )
        }

        item {
            SettingsCard {
                ArrowPreference(
                    title = stringResource(R.string.dock_recent_blacklist_load_title),
                    summary = stringResource(R.string.dock_recent_blacklist_load_summary),
                    onClick = {
                        val request = UUID.randomUUID().toString()
                        val stored = prefs.edit()
                            .putString(DockRecentAppStore.DISCOVERY_REQUEST_KEY, request)
                            .commit()
                        val synced = stored && LiquidDockApp.syncToRemote(prefs)
                        if (!synced) {
                            prefs.edit().remove(DockRecentAppStore.DISCOVERY_REQUEST_KEY).commit()
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.dock_recent_blacklist_load_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@ArrowPreference
                        }
                        candidatePrefs.edit().remove(DockRecentAppStore.CANDIDATE_KEY).commit()
                        candidateRevision++
                        val token = prefs.getString(
                            WidgetComponentStore.DISCOVERY_TOKEN_KEY,
                            "",
                        ).orEmpty()
                        if (!LauncherManualDiscoveryBridge.requestDockRefresh(activity, token)) {
                            prefs.edit().remove(DockRecentAppStore.DISCOVERY_REQUEST_KEY).commit()
                            LiquidDockApp.syncToRemote(prefs)
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.dock_recent_blacklist_load_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }

        item { SmallTitle(stringResource(R.string.dock_recent_blacklist_current_candidates)) }
        if (availableCandidates.isEmpty()) {
            item {
                SettingsCard {
                    Text(stringResource(R.string.dock_recent_blacklist_empty))
                }
            }
        } else {
            items(availableCandidates, key = { "candidate:$it" }) { packageName ->
                val label = remember(packageName) { appLabel(context.packageManager, packageName) }
                SettingsCard {
                    SwitchPreference(
                        checked = false,
                        onCheckedChange = { checked -> setBlocked(packageName, checked) },
                        title = label,
                        summary = context.getString(
                            R.string.dock_recent_blacklist_candidate_item,
                            packageName,
                        ),
                        enabled = masterEnabled,
                    )
                }
            }
        }

        item { SmallTitle(stringResource(R.string.dock_recent_blacklist_blocked_apps)) }
        if (blockedPackages.isEmpty()) {
            item {
                SettingsCard {
                    Text(stringResource(R.string.dock_recent_blacklist_blocked_empty))
                }
            }
        } else {
            items(blockedPackages, key = { "blocked:$it" }) { packageName ->
                val label = remember(packageName) { appLabel(context.packageManager, packageName) }
                SettingsCard {
                    SwitchPreference(
                        checked = true,
                        onCheckedChange = { checked -> setBlocked(packageName, checked) },
                        title = label,
                        summary = if (currentCandidates.contains(packageName)) {
                            context.getString(
                                R.string.dock_recent_blacklist_blocked_active_item,
                                packageName,
                            )
                        } else {
                            context.getString(
                                R.string.dock_recent_blacklist_blocked_inactive_item,
                                packageName,
                            )
                        },
                        enabled = masterEnabled,
                    )
                }
            }
        }
    }
}
