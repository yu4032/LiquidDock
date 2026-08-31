package com.hellovoid.liquiddock

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.BackHandler
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
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
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
    var showAdvancedRemote by rememberSaveable { mutableStateOf(false) }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }

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
    val categoryVisible = when {
        isMaml && !showAllMaml -> components.filter { it.componentType != WidgetComponentStore.TYPE_INTERNAL }
        !isMaml && !showAdvancedRemote -> components.filter {
            it.componentType == WidgetComponentStore.TYPE_BACKGROUND ||
                    it.componentType == WidgetComponentStore.TYPE_IMAGE
        }
        else -> components
    }
    val typeGroups = categoryVisible.groupBy { it.componentType }
    val currentTypeComponents = selectedType?.let { type ->
        categoryVisible.filter { it.componentType == type }
    }.orEmpty()

    BackHandler(enabled = selectedType != null) { selectedType = null }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = if (selectedType == null) owner else "$owner · ${componentTypeTitle(selectedType!!)}",
                navigationIcon = {
                    TextButton(
                        text = "返回",
                        onClick = {
                            if (selectedType != null) selectedType = null else activity.finish()
                        },
                    )
                },
                actions = {
                    TextButton(text = "重启桌面", onClick = { activity.restartLauncher() })
                },
            )
        },
    ) { padding ->
        if (selectedType == null) {
            WidgetComponentTypePage(
                padding = padding,
                owner = owner,
                isMaml = isMaml,
                components = components,
                typeGroups = typeGroups,
                selected = selected,
                showAllMaml = showAllMaml,
                onShowAllMaml = { showAllMaml = it },
                showAdvancedRemote = showAdvancedRemote,
                onShowAdvancedRemote = { showAdvancedRemote = it },
                onOpenType = { selectedType = it },
            )
        } else {
            WidgetExactNodePage(
                padding = padding,
                type = selectedType!!,
                components = currentTypeComponents,
                selected = selected,
                onSelectionChanged = { descriptor, checked ->
                    val key = descriptor.selectorKey()
                    val next = selected.toMutableSet()
                    if (checked) next.add(key) else next.remove(key)
                    selected = next.toSet()
                    prefs.edit()
                        .putStringSet(WidgetComponentStore.SELECTION_KEY, HashSet(selected))
                        .apply()
                },
            )
        }
    }
}

@Composable
private fun WidgetComponentTypePage(
    padding: androidx.compose.foundation.layout.PaddingValues,
    owner: String,
    isMaml: Boolean,
    components: List<WidgetComponentStore.Descriptor>,
    typeGroups: Map<String, List<WidgetComponentStore.Descriptor>>,
    selected: Set<String>,
    showAllMaml: Boolean,
    onShowAllMaml: (Boolean) -> Unit,
    showAdvancedRemote: Boolean,
    onShowAdvancedRemote: (Boolean) -> Unit,
    onOpenType: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(owner, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "组件类型 · ${components.size} 个可发现操作",
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
                        onCheckedChange = onShowAllMaml,
                        title = "显示全部内部元素",
                        summary = "默认不显示 VariableElement 等内部状态",
                    )
                }
            }
        } else {
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    SwitchPreference(
                        checked = showAdvancedRemote,
                        onCheckedChange = onShowAdvancedRemote,
                        title = "高级整节点隐藏",
                        summary = "显示文本、容器、交互与其他节点；隐藏容器会连同其子内容一起隐藏",
                    )
                }
            }
        }

        if (components.isEmpty()) {
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("该小组件已不在当前载入目录中")
                        Text("返回上一页并重新载入当前小组件。", fontSize = 13.sp)
                    }
                }
            }
        } else if (typeGroups.isEmpty()) {
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("当前没有可安全直接操作的背景或图像层")
                        if (!isMaml) {
                            Text("可开启“高级整节点隐藏”查看文本、容器和其他节点。", fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            item { SmallTitle("组件类型") }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column {
                        componentTypeOrder.forEach { type ->
                            val group = typeGroups[type].orEmpty()
                            if (group.isEmpty()) return@forEach
                            val selectedCount = group.count { it.selectorKey() in selected }
                            ArrowPreference(
                                title = componentTypeTitle(type),
                                summary = "已选择 $selectedCount / ${group.size}",
                                onClick = { onOpenType(type) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetExactNodePage(
    padding: androidx.compose.foundation.layout.PaddingValues,
    type: String,
    components: List<WidgetComponentStore.Descriptor>,
    selected: Set<String>,
    onSelectionChanged: (WidgetComponentStore.Descriptor, Boolean) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(componentTypeTitle(type), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (type == WidgetComponentStore.TYPE_BACKGROUND) {
                        "仅移除 View.background，不隐藏 View 与子内容。"
                    } else if (type == WidgetComponentStore.TYPE_IMAGE) {
                        "仅移除 ImageView 图像 Drawable，不隐藏其他内容。"
                    } else {
                        "高级整节点隐藏：只命中当前精确路径；容器节点会同时隐藏其子内容。"
                    },
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        items(components, key = { it.selectorKey() }) { descriptor ->
            val key = descriptor.selectorKey()
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                SwitchPreference(
                    checked = key in selected,
                    onCheckedChange = { checked -> onSelectionChanged(descriptor, checked) },
                    title = exactNodeTitle(descriptor),
                    summary = exactNodeSummary(descriptor),
                )
            }
        }
    }
}

private val componentTypeOrder = listOf(
    WidgetComponentStore.TYPE_BACKGROUND,
    WidgetComponentStore.TYPE_IMAGE,
    WidgetComponentStore.TYPE_TEXT,
    WidgetComponentStore.TYPE_CONTAINER,
    WidgetComponentStore.TYPE_INTERACTIVE,
    WidgetComponentStore.TYPE_OTHER,
    WidgetComponentStore.TYPE_INTERNAL,
)

private fun componentTypeTitle(type: String): String = when (type) {
    WidgetComponentStore.TYPE_BACKGROUND -> "背景层"
    WidgetComponentStore.TYPE_IMAGE -> "图像层"
    WidgetComponentStore.TYPE_TEXT -> "文本"
    WidgetComponentStore.TYPE_CONTAINER -> "容器"
    WidgetComponentStore.TYPE_INTERACTIVE -> "交互"
    WidgetComponentStore.TYPE_INTERNAL -> "内部状态"
    else -> "其他"
}

private fun exactNodeTitle(descriptor: WidgetComponentStore.Descriptor): String {
    val name = descriptor.name.ifEmpty { "(无资源 ID)" }
    return when (descriptor.action) {
        WidgetComponentStore.ACTION_CLEAR_BACKGROUND -> "移除背景 · $name"
        WidgetComponentStore.ACTION_CLEAR_IMAGE -> "移除图像 · $name"
        else -> "隐藏节点 · $name"
    }
}

private fun exactNodeSummary(descriptor: WidgetComponentStore.Descriptor): String {
    val type = descriptor.className.substringAfterLast('.')
    return if (descriptor.isRemoteViews()) {
        "$type · 精确路径 ${descriptor.hierarchyPath}"
    } else {
        "MAML · $type · ${descriptor.hierarchyPath}"
    }
}
