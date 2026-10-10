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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import java.util.HashSet
import java.util.Locale
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class WidgetComponentDetailActivity : SettingsActivity() {
    companion object {
        const val EXTRA_WIDGET_KEY = "widget_key"
    }

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
    val uiPrefs = remember(activity) {
        activity.getSharedPreferences(SETTINGS_UI_PREFS, Context.MODE_PRIVATE)
    }
    val glassEnabled = uiPrefs.getBoolean(SETTINGS_UI_GLASS_ENABLED, true)
    val catalogPrefs = remember(activity) {
        activity.getSharedPreferences(WidgetComponentStore.CATALOG_PREFS, Context.MODE_PRIVATE)
    }
    var catalogRevision by remember { mutableIntStateOf(0) }
    var selected by remember {
        mutableStateOf(
            prefs.getStringSet(WidgetComponentStore.SELECTION_KEY, emptySet())?.toSet().orEmpty()
        )
    }
    var whitened by remember {
        mutableStateOf(
            prefs.getStringSet(WidgetComponentStore.WHITE_SELECTION_KEY, emptySet())?.toSet().orEmpty()
        )
    }
    var whiteMode by rememberSaveable { mutableStateOf(false) }
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
        whiteMode -> components.filter { WidgetComponentWhiteningPolicy.supports(it) }
        isMaml && !showAllMaml -> components.filter { it.componentType != WidgetComponentStore.TYPE_INTERNAL }
        !isMaml && !showAdvancedRemote -> components.filter {
            it.componentType == WidgetComponentStore.TYPE_BACKGROUND ||
                    it.componentType == WidgetComponentStore.TYPE_IMAGE
        }
        else -> components
    }
    val typeGroups = categoryVisible.groupBy { it.componentType }
    val likelyBackgrounds = WidgetComponentRanking.sorted(
        categoryVisible.filter { WidgetComponentRanking.isLikelyBackground(it) }
    )
    val currentTypeComponents = selectedType?.let { type ->
        WidgetComponentRanking.sorted(categoryVisible.filter { it.componentType == type })
    }.orEmpty()

    BackHandler(enabled = selectedType != null) { selectedType = null }

    ModernSettingsScaffold(
        title = if (selectedType == null) owner else "$owner · ${componentTypeTitle(selectedType!!)}",
        glassEnabled = glassEnabled,
        showBack = true,
        backLabel = "返回",
        onBack = {
            if (selectedType != null) selectedType = null else activity.finish()
        },
        actions = {
            ModernTopActionButton(
                text = "重启桌面",
                onClick = { activity.restartLauncher() },
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
                likelyBackgrounds = likelyBackgrounds,
                selected = if (whiteMode) whitened else selected,
                whiteMode = whiteMode,
                onWhiteModeChanged = { whiteMode = it; selectedType = null },
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
                selected = if (whiteMode) whitened else selected,
                whiteMode = whiteMode,
                onSelectionChanged = { descriptor, checked ->
                    val key = descriptor.selectorKey()
                    val changed = (if (whiteMode) whitened else selected).toMutableSet()
                    val other = (if (whiteMode) selected else whitened).toMutableSet()
                    if (checked) {
                        changed.add(key)
                        // An image node may have different selector actions for hiding
                        // and whitening; clear the opposing action on the same exact node.
                        other.removeAll { encoded ->
                            WidgetComponentWhiteningPolicy.sameNode(
                                WidgetComponentStore.parseSelector(encoded), descriptor,
                            )
                        }
                    } else {
                        changed.remove(key)
                    }
                    if (whiteMode) {
                        whitened = changed.toSet()
                        selected = other.toSet()
                    } else {
                        selected = changed.toSet()
                        whitened = other.toSet()
                    }
                    prefs.edit()
                        .putStringSet(WidgetComponentStore.SELECTION_KEY, HashSet(selected))
                        .putStringSet(WidgetComponentStore.WHITE_SELECTION_KEY, HashSet(whitened))
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
    likelyBackgrounds: List<WidgetComponentStore.Descriptor>,
    selected: Set<String>,
    whiteMode: Boolean,
    onWhiteModeChanged: (Boolean) -> Unit,
    showAllMaml: Boolean,
    onShowAllMaml: (Boolean) -> Unit,
    showAdvancedRemote: Boolean,
    onShowAdvancedRemote: (Boolean) -> Unit,
    onOpenType: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            ModernSurface(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
            ) {
                SwitchPreference(
                    checked = whiteMode,
                    onCheckedChange = onWhiteModeChanged,
                    title = "部件白化 · 深色适配",
                    summary = if (isMaml) {
                        "MAML 使用脚本绘制，暂不支持按精确节点白化"
                    } else {
                        "仅在小组件玻璃启用后对白色文字和图像生效；独立于隐藏规则，重启桌面生效"
                    },
                    enabled = !isMaml,
                )
            }
        }
        item {
            Text(
                "组件类型 · ${components.size} 个可发现操作",
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        if (isMaml && !whiteMode) {
            item {
                ModernSurface(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                ) {
                    SwitchPreference(
                        checked = showAllMaml,
                        onCheckedChange = onShowAllMaml,
                        title = "显示全部内部元素",
                        summary = "默认不显示 VariableElement 等内部状态",
                    )
                }
            }
        } else if (!whiteMode) {
            item {
                ModernSurface(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                ) {
                    SwitchPreference(
                        checked = showAdvancedRemote,
                        onCheckedChange = onShowAdvancedRemote,
                        title = "高级整节点隐藏",
                        summary = "显示文本、容器、交互与其他节点；隐藏容器会连同其子内容一起隐藏",
                    )
                }
            }
        }

        if (!whiteMode && likelyBackgrounds.isNotEmpty()) {
            item { SmallTitle("疑似底层背景") }
            item {
                ModernSurface(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                ) {
                    Column {
                        likelyBackgrounds.forEachIndexed { index, descriptor ->
                            ArrowPreference(
                                title = exactNodeTitle(descriptor),
                                summary = buildString {
                                    append(exactNodeSummary(descriptor))
                                    if (descriptor.selectorKey() in selected) append(" · 已选择")
                                },
                                onClick = { onOpenType(descriptor.componentType) },
                            )
                            if (index != likelyBackgrounds.lastIndex) {
                                ModernListDivider()
                            }
                        }
                    }
                }
            }
        }

        if (components.isEmpty()) {
            item {
                ModernSurface(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("该小组件已不在当前载入目录中")
                        Text("返回上一页并重新载入当前小组件。", fontSize = 13.sp)
                    }
                }
            }
        } else if (typeGroups.isEmpty()) {
            item {
                ModernSurface(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(if (whiteMode) "没有支持精确白化的文本或图片部件"
                            else "当前没有可安全直接操作的背景或图像层")
                        if (!isMaml) {
                            Text("可开启“高级整节点隐藏”查看文本、容器和其他节点。", fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            item { SmallTitle("组件类型") }
            item {
                ModernSurface(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                ) {
                    Column {
                        val visibleTypes = componentTypeOrder
                            .mapNotNull { type ->
                                val group = WidgetComponentRanking.sorted(typeGroups[type].orEmpty())
                                group.takeIf { it.isNotEmpty() }?.let { type to it }
                            }
                        visibleTypes.forEachIndexed { index, (type, group) ->
                            val selectedCount = group.count { it.selectorKey() in selected }
                            val likelyCount = group.count(WidgetComponentRanking::isLikelyBackground)
                            ArrowPreference(
                                title = componentTypeTitle(type),
                                summary = buildString {
                                    append("${if (whiteMode) "已白化" else "已选择"} $selectedCount / ${group.size}")
                                    if (likelyCount > 0) append(" · 疑似背景 $likelyCount")
                                },
                                onClick = { onOpenType(type) },
                            )
                            if (index != visibleTypes.lastIndex) {
                                ModernListDivider()
                            }
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
    whiteMode: Boolean,
    onSelectionChanged: (WidgetComponentStore.Descriptor, Boolean) -> Unit,
) {
    val isMaml = components.firstOrNull()?.isMaml() == true
    val rankedComponents = WidgetComponentRanking.sorted(components)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            Text(
                when {
                    whiteMode -> "白化只改变当前精确路径文字或图片的前景色；不改变背景与其他节点。"
                    isMaml -> "MAML 元素按名称或精确渲染路径隐藏；路径或类型变化时不会回退误命中。"
                    type == WidgetComponentStore.TYPE_BACKGROUND ->
                        "仅移除 View.background，不隐藏 View 与子内容。"
                    type == WidgetComponentStore.TYPE_IMAGE ->
                        "仅移除 ImageView 图像 Drawable，不隐藏其他内容。"
                    else ->
                        "高级整节点隐藏：只命中当前精确路径；容器节点会同时隐藏其子内容。"
                },
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        rankedComponents.chunked(10).forEach { group ->
            item(key = "node-group:${group.first().selectorKey()}") {
                ModernSurface(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                ) {
                    Column {
                        group.forEachIndexed { index, descriptor ->
                            val key = descriptor.selectorKey()
                            SwitchPreference(
                                checked = key in selected,
                                onCheckedChange = { checked -> onSelectionChanged(descriptor, checked) },
                                title = if (whiteMode) "白化 · ${descriptor.name.ifEmpty { "(无资源 ID)" }}"
                                    else exactNodeTitle(descriptor),
                                summary = exactNodeSummary(descriptor),
                            )
                            if (index != group.lastIndex) {
                                ModernListDivider()
                            }
                        }
                    }
                }
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
    val name = descriptor.name.ifEmpty {
        if (descriptor.isMaml()) "(匿名元素)" else "(无资源 ID)"
    }
    val actionTitle = when (descriptor.action) {
        WidgetComponentStore.ACTION_CLEAR_BACKGROUND -> "移除背景 · $name"
        WidgetComponentStore.ACTION_CLEAR_IMAGE -> "移除图像 · $name"
        else -> "隐藏节点 · $name"
    }
    return if (WidgetComponentRanking.isLikelyBackground(descriptor)) {
        "疑似背景 · $actionTitle"
    } else {
        actionTitle
    }
}

private fun exactNodeSummary(descriptor: WidgetComponentStore.Descriptor): String {
    val type = descriptor.className.substringAfterLast('.')
    val source = if (descriptor.isRemoteViews()) {
        "$type · 精确路径 ${descriptor.hierarchyPath}"
    } else {
        "MAML · $type · ${descriptor.hierarchyPath}"
    }
    return "$source · ${renderMetadataSummary(descriptor)}"
}

private fun renderMetadataSummary(descriptor: WidgetComponentStore.Descriptor): String {
    val render = if (descriptor.renderOrdinal >= 0) "Render #${descriptor.renderOrdinal}" else "Render —"
    val depth = if (descriptor.depth >= 0) "Depth ${descriptor.depth}" else "Depth —"
    val area = if (descriptor.areaRatio.isNaN()) {
        "Area —"
    } else {
        "Area ${String.format(Locale.ROOT, "%.0f%%", descriptor.areaRatio * 100f)}"
    }
    val z = if (descriptor.effectiveZ.isNaN()) {
        "Z —"
    } else {
        "Z ${String.format(Locale.ROOT, "%.1f", descriptor.effectiveZ)}"
    }
    return "$render · $depth · $area · $z"
}
