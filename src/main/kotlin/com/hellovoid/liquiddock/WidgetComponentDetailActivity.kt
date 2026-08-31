package com.hellovoid.liquiddock

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
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
import androidx.preference.PreferenceManager
import java.util.HashSet
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class WidgetComponentDetailActivity : SettingsActivity() {
    companion object {
        const val EXTRA_WIDGET_KEY = "widget_key"
    }

    override fun useLegacyPreferenceUi(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetKey = intent.getStringExtra(EXTRA_WIDGET_KEY)
        if (widgetKey.isNullOrEmpty()) {
            finish()
            return
        }
        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.MonetSystem) }
            MiuixTheme(controller = controller) {
                WidgetComponentDetailScreen(this, widgetKey)
            }
        }
    }
}

@Composable
private fun WidgetComponentDetailScreen(
    activity: WidgetComponentDetailActivity,
    widgetKey: String,
) {
    val prefs = remember(activity) { PreferenceManager.getDefaultSharedPreferences(activity) }
    val catalogPrefs = remember(activity) {
        activity.getSharedPreferences(WidgetComponentStore.CATALOG_PREFS, Context.MODE_PRIVATE)
    }
    var catalogRevision by remember { mutableIntStateOf(0) }
    var selected by remember {
        mutableStateOf(
            prefs.getStringSet(WidgetComponentStore.SELECTION_KEY, emptySet())?.toSet().orEmpty()
        )
    }
    var showAllMaml by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(catalogPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == WidgetComponentStore.CATALOG_KEY) catalogRevision++
        }
        catalogPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { catalogPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val components = remember(catalogRevision, widgetKey) {
        loadWidgetCatalog(catalogPrefs).filter { widgetGroupKey(it) == widgetKey }
    }
    val first = components.firstOrNull()
    val owner = first?.displayOwner() ?: "小组件组件"
    val isMaml = first?.isMaml() == true
    val visible = if (isMaml && !showAllMaml) {
        components.filter(::defaultWidgetComponentVisible)
    } else {
        components
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = owner,
                navigationIcon = {
                    TextButton(text = "返回", onClick = { activity.finish() })
                },
                actions = {
                    TextButton(text = "重启桌面", onClick = { activity.restartLauncher() })
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(owner, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isMaml) "MAML · ${components.size} 个内部组件"
                        else "RemoteViews · ${components.size} 个内部组件",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }

            if (isMaml) {
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        SwitchPreference(
                            checked = showAllMaml,
                            onCheckedChange = { showAllMaml = it },
                            title = "显示全部内部元素",
                            summary = "默认隐藏 VariableElement 等非视觉内部状态",
                        )
                    }
                }
            }

            if (components.isEmpty()) {
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("该小组件已不在当前载入目录中")
                            Text(
                                "返回上一页并重新载入当前小组件。",
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                }
            } else {
                items(visible, key = { it.selectorKey() }) { descriptor ->
                    val key = descriptor.selectorKey()
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        SwitchPreference(
                            checked = key in selected,
                            onCheckedChange = { checked ->
                                val next = selected.toMutableSet()
                                if (checked) next.add(key) else next.remove(key)
                                selected = next.toSet()
                                prefs.edit()
                                    .putStringSet(WidgetComponentStore.SELECTION_KEY, HashSet(selected))
                                    .apply()
                            },
                            title = descriptor.name,
                            summary = widgetComponentSummary(descriptor),
                        )
                    }
                }
            }
        }
    }
}
