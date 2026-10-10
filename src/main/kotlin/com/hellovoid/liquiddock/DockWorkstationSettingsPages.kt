package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.basic.Text

// Dock, Divider, Workstation and Recents settings leaf pages only.
// Navigation, scoped restarts and Prismal controls remain owned by the existing UI shell.
@Composable
internal fun DockPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    var dockEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Dock.ENABLED.name(),
                ConfigSchema.Dock.ENABLED.uiDefault(),
            ),
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader(
                stringResource(R.string.page_dock),
                "调整 Dock 行为、尺寸、位置与外观。",
            )
        }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Dock.ENABLED,
                    stringResource(R.string.dock_customization),
                    stringResource(R.string.dock_customization_summary),
                    masterEnabled,
                ) { dockEnabled = it }
            }
        }
        dockEntries.forEach { entry ->
            item {
                ModernFeatureCard(
                    title = stringResource(entry.titleRes),
                    summary = entry.summary,
                    onClick = { open(entry.page) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        item {
            ModernFeatureCard(
                title = stringResource(R.string.page_stroke),
                summary = "描边样式与颜色",
                onClick = { open(Page.Stroke) },
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
        item {
            ModernFeatureCard(
                title = stringResource(R.string.page_shadow),
                summary = "Dock 与描边阴影",
                onClick = { open(Page.Shadow) },
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
        if (!dockEnabled) {
            item {
                ModernSurface(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text("开启 Dock 自定义后应用下方设置。", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
internal fun DockBehaviorPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val dockEnabled = prefs.getBoolean(
        ConfigSchema.Dock.ENABLED.name(),
        ConfigSchema.Dock.ENABLED.uiDefault(),
    )
    var resizeAnimation by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Dock.RESIZE_ANIMATION.name(),
                ConfigSchema.Dock.RESIZE_ANIMATION.uiDefault(),
            ),
        )
    }
    SettingsList(
        padding,
        stringResource(R.string.page_dock_behavior),
        "管理 Dock 功能入口与尺寸变化动画。",
    ) {
        BooleanSetting(
            prefs,
            ConfigSchema.Dock.HIDE_MIRROR_SHORTCUT,
            "隐藏手机互联图标",
            "仅隐藏 Dock 入口，不修改系统互联开关或设备连接状态",
            masterEnabled,
        )
        BooleanSetting(
            prefs,
            ConfigSchema.Dock.RESIZE_ANIMATION,
            stringResource(R.string.dock_resize_animation),
            stringResource(R.string.dock_resize_animation_summary),
            masterEnabled && dockEnabled,
        ) { resizeAnimation = it }
        BooleanSetting(
            prefs,
            ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION,
            stringResource(R.string.dock_smooth_resize_animation),
            stringResource(R.string.dock_smooth_resize_animation_summary),
            masterEnabled && dockEnabled && !resizeAnimation,
        )
    }
}

@Composable
internal fun DockGeometryPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val dockEnabled = prefs.getBoolean(
        ConfigSchema.Dock.ENABLED.name(),
        ConfigSchema.Dock.ENABLED.uiDefault(),
    )
    DenseSettingsList(
        padding,
        stringResource(R.string.page_dock_geometry),
        "调整 Dock 的尺寸、位置、圆角与图标间距。",
    ) {
        groupedIntSettings(dockSpecs, prefs, masterEnabled && dockEnabled)
    }
}

@Composable
internal fun DividerPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    val legacyDefault = remember { hasLegacyDividerConfig(prefs) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Divider.ENABLED.name(), legacyDefault)) }
    DenseSettingsList(padding, stringResource(R.string.page_divider)) {
        item(key = "divider-enabled") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Divider.ENABLED, "自定义 Dock 分隔线", "独立于 Dock 尺寸、模糊和单位开关；宽度与偏移固定使用 dp", masterEnabled, default = legacyDefault) {
                    enabled = it
                    if (it) ensureDividerDefaults(prefs)
                }
            }
        }
        items(dividerSpecs, key = { it.key }) { spec ->
            SettingsCard {
                IntSetting(prefs, spec, masterEnabled && enabled)
            }
        }
    }
}

@Composable
internal fun WorkstationPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    var enabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Workstation.DOCK_CUSTOMIZATION.name(),
                ConfigSchema.Workstation.DOCK_CUSTOMIZATION.uiDefault(),
            ),
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader(
                stringResource(R.string.page_workstation),
                "调整工作台桌面、Dock 与所有应用布局。",
            )
        }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Workstation.DOCK_CUSTOMIZATION,
                    stringResource(R.string.workstation_customization),
                    stringResource(R.string.workstation_customization_summary),
                    masterEnabled,
                ) { enabled = it }
            }
        }
        workstationEntries.forEach { entry ->
            item {
                ModernFeatureCard(
                    title = stringResource(entry.titleRes),
                    summary = entry.summary,
                    onClick = { open(entry.page) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        if (!enabled) {
            item {
                ModernSurface(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text("开启工作台自定义后应用下方设置。", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
internal fun WorkstationSpecPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    specs: List<IntSpec>,
    summary: String,
) {
    val enabled = prefs.getBoolean(
        ConfigSchema.Workstation.DOCK_CUSTOMIZATION.name(),
        ConfigSchema.Workstation.DOCK_CUSTOMIZATION.uiDefault(),
    )
    DenseSettingsList(
        padding = padding,
        title = "",
        summary = summary,
    ) {
        items(specs, key = { it.key }) { spec ->
            SettingsCard {
                IntSetting(prefs, spec, masterEnabled && enabled)
            }
        }
    }
}

@Composable
internal fun RecentsPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    SettingsList(
        padding,
        stringResource(R.string.page_recents),
        stringResource(R.string.recents_header_summary),
    ) {
        IntSetting(prefs, recentsBlurSpec, masterEnabled)
        BooleanSetting(
            prefs,
            ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING,
            "取消壁纸压暗",
            "进入多任务时保留系统背景模糊与过渡动画，仅移除壁纸黑色压暗；已加载的桌面 Hook 即时生效",
            masterEnabled,
        )
    }
}

