package com.hellovoid.liquiddock

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

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
    var showAllMaml by rememberSaveable { mutableStateOf(false) }
    var selected by remember {
        mutableStateOf(
            prefs.getStringSet(WidgetComponentStore.SELECTION_KEY, emptySet())
                ?.toSet().orEmpty()
        )
    }

    DisposableEffect(catalogPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == WidgetComponentStore.CATALOG_KEY) catalogRevision++
        }
        catalogPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { catalogPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val descriptors = remember(catalogRevision) {
        catalogPrefs.getStringSet(WidgetComponentStore.CATALOG_KEY, emptySet())
            .orEmpty()
            .mapNotNull(WidgetComponentStore::parseCatalog)
            .distinctBy { it.selectorKey() }
            .sortedWith(
                compareBy<WidgetComponentStore.Descriptor>(
                    { it.displayOwner() }, { it.name }, { it.className }
                )
            )
    }
    val visible = if (showAllMaml) descriptors else descriptors.filter(::defaultVisible)
    val groups = visible.groupBy { it.displayOwner() }.toSortedMap()

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("小组件组件隐藏", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "扫描当前桌面已加载的小组件，并选择要隐藏的内部视觉组件。选择后重启桌面生效。",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Column {
                    SwitchPreference(
                        checked = showAllMaml,
                        onCheckedChange = { showAllMaml = it },
                        title = "显示全部内部元素",
                        summary = "默认隐藏 MAML VariableElement 等非视觉内部状态",
                    )
                    ArrowPreference(
                        title = "刷新列表",
                        summary = "重新读取已扫描到的组件目录",
                        onClick = { catalogRevision++ },
                    )
                    ArrowPreference(
                        title = "重新扫描桌面",
                        summary = "清空发现目录并重启桌面；桌面重新加载后会重新扫描",
                        onClick = {
                            catalogPrefs.edit().remove(WidgetComponentStore.CATALOG_KEY).apply()
                            catalogRevision++
                            activity.restartLauncher()
                        },
                    )
                    ArrowPreference(
                        title = "应用并重启桌面",
                        summary = "让当前勾选的隐藏规则立即进入新的桌面进程",
                        onClick = activity::restartLauncher,
                    )
                }
            }
        }

        if (groups.isEmpty()) {
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("尚未发现小组件")
                        Text(
                            "首次安装后先打开一次 LiquidDock，再重启桌面；返回此页后点击刷新列表。",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        } else {
            groups.forEach { (owner, components) ->
                item { SmallTitle(owner) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column {
                            components.forEach { descriptor ->
                                val key = descriptor.selectorKey()
                                SwitchPreference(
                                    checked = key in selected,
                                    onCheckedChange = { checked ->
                                        val next = selected.toMutableSet()
                                        if (checked) next.add(key) else next.remove(key)
                                        selected = next.toSet()
                                        prefs.edit()
                                            .putStringSet(
                                                WidgetComponentStore.SELECTION_KEY,
                                                HashSet(selected),
                                            )
                                            .apply()
                                    },
                                    title = descriptor.name,
                                    summary = buildSummary(descriptor),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun defaultVisible(descriptor: WidgetComponentStore.Descriptor): Boolean {
    if (!descriptor.isMaml()) return true
    val simple = descriptor.className.substringAfterLast('.')
    return !simple.contains("VariableElement")
}

private fun buildSummary(descriptor: WidgetComponentStore.Descriptor): String {
    val source = if (descriptor.isMaml()) "MAML" else "RemoteViews"
    return "$source · ${descriptor.className.substringAfterLast('.')}"
}
