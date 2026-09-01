package com.hellovoid.liquiddock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun WidgetComponentsPage(
    padding: PaddingValues,
    activity: ComposeSettingsActivity,
    prefs: SharedPreferences,
) {
    val catalogPrefs = remember(activity) {
        activity.getSharedPreferences(WidgetComponentStore.CATALOG_PREFS, Context.MODE_PRIVATE)
    }
    var catalogRevision by remember { mutableIntStateOf(0) }
    var selectionRevision by remember { mutableIntStateOf(0) }

    DisposableEffect(catalogPrefs, prefs) {
        val catalogListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == WidgetComponentStore.CATALOG_KEY) catalogRevision++
        }
        val selectionListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == WidgetComponentStore.SELECTION_KEY) selectionRevision++
        }
        catalogPrefs.registerOnSharedPreferenceChangeListener(catalogListener)
        prefs.registerOnSharedPreferenceChangeListener(selectionListener)
        onDispose {
            catalogPrefs.unregisterOnSharedPreferenceChangeListener(catalogListener)
            prefs.unregisterOnSharedPreferenceChangeListener(selectionListener)
        }
    }

    val descriptors = remember(catalogRevision) { loadWidgetCatalog(catalogPrefs) }
    val selected = remember(selectionRevision) {
        prefs.getStringSet(WidgetComponentStore.SELECTION_KEY, emptySet())?.toSet().orEmpty()
    }
    val groups = descriptors
        .groupBy(::widgetGroupKey)
        .entries
        .sortedBy { it.value.firstOrNull()?.displayOwner().orEmpty() }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("小组件组件隐藏", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "仅在手动载入时扫描当前桌面小组件；普通桌面重启不会扫描内部组件。",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                ArrowPreference(
                    title = "载入当前小组件",
                    summary = "仅下一次桌面启动执行一轮扫描，完成后自动关闭扫描",
                    onClick = {
                        val request = UUID.randomUUID().toString()
                        val stored = prefs.edit()
                            .putString(WidgetComponentStore.DISCOVERY_REQUEST_KEY, request)
                            .commit()
                        val synced = stored && LiquidDockApp.syncToRemote(prefs)
                        if (!synced) {
                            prefs.edit().remove(WidgetComponentStore.DISCOVERY_REQUEST_KEY).commit()
                            Toast.makeText(activity, "Xposed 服务未连接，无法载入小组件", Toast.LENGTH_SHORT).show()
                            return@ArrowPreference
                        }
                        catalogPrefs.edit().remove(WidgetComponentStore.CATALOG_KEY).commit()
                        catalogRevision++
                        activity.restartLauncher()
                    },
                )
            }
        }

        item { SmallTitle("隐藏规则备份") }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Column {
                    ArrowPreference(
                        title = "导出隐藏规则",
                        summary = "仅保存已选择的小组件隐藏规则，不包含扫描目录",
                        onClick = { activity.launchWidgetHiddenExport() },
                    )
                    ArrowPreference(
                        title = "导入隐藏规则",
                        summary = "覆盖当前隐藏规则并重启桌面，不重新扫描小组件",
                        onClick = { activity.launchWidgetHiddenImport() },
                    )
                }
            }
        }

        if (groups.isEmpty()) {
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("尚未载入小组件")
                        Text(
                            "点击上方“载入当前小组件”；LiquidDock 只会在这次手动触发后扫描一次。",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        } else {
            item { SmallTitle("已载入小组件") }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column {
                        groups.forEach { (key, components) ->
                            val first = components.first()
                            val selectedCount = components.count { it.selectorKey() in selected }
                            val source = if (first.isMaml()) "MAML" else "RemoteViews"
                            val likelyCount = components.count(WidgetComponentRanking::isLikelyBackground)
                            ArrowPreference(
                                title = first.displayOwner(),
                                summary = buildString {
                                    append("$source · 已隐藏 $selectedCount / ${components.size}")
                                    if (likelyCount > 0) append(" · 疑似背景 $likelyCount")
                                },
                                onClick = {
                                    activity.startActivity(
                                        Intent(activity, WidgetComponentDetailActivity::class.java)
                                            .putExtra(WidgetComponentDetailActivity.EXTRA_WIDGET_KEY, key)
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun loadWidgetCatalog(catalogPrefs: SharedPreferences): List<WidgetComponentStore.Descriptor> =
    catalogPrefs.getStringSet(WidgetComponentStore.CATALOG_KEY, emptySet())
        .orEmpty()
        .mapNotNull(WidgetComponentStore::parseCatalog)
        .distinctBy { it.selectorKey() }
        .sortedWith { left, right ->
            val ownerOrder = left.displayOwner().compareTo(right.displayOwner())
            if (ownerOrder != 0) ownerOrder else WidgetComponentRanking.compare(left, right)
        }

internal fun widgetGroupKey(descriptor: WidgetComponentStore.Descriptor): String =
    (if (descriptor.isMaml()) "M" else descriptor.source) + "\t" + descriptor.owner

internal fun defaultWidgetComponentVisible(descriptor: WidgetComponentStore.Descriptor): Boolean {
    if (!descriptor.isMaml()) return true
    val simple = descriptor.className.substringAfterLast('.')
    return !simple.contains("VariableElement")
}

internal fun widgetComponentSummary(descriptor: WidgetComponentStore.Descriptor): String {
    val source = if (descriptor.isMaml()) "MAML" else "RemoteViews"
    return "$source · ${descriptor.className.substringAfterLast('.')}"
}
