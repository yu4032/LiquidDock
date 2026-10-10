package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellovoid.liquiddock.config.ConfigSchema
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.window.WindowDialog

// Pure source relocation: keeps the widget 4×2 preflight, in-flight cancellation,
// stale reply rejection and large warning dialog inside their existing GridBasicsPage.
@Composable
internal fun GridPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    val customGridRevision = LocalSettingsPreferenceRevisions.current[ConfigSchema.Grid.ENABLED.name()] ?: 0
    var customGrid by remember(customGridRevision) {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Grid.ENABLED.name(),
                ConfigSchema.Grid.ENABLED.uiDefault(),
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
                stringResource(R.string.page_grid),
                "设置桌面网格与不同屏幕方向下的布局。",
            )
        }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Grid.ENABLED,
                    "自由主界面网格",
                    "允许 2×2 到 10×6 的工作区布局；全屏居中、分屏跟随系统 pane 对齐；重启桌面生效",
                    masterEnabled,
                ) { customGrid = it }
            }
        }
        gridEntries.forEach { entry ->
            item {
                ModernFeatureCard(
                    title = stringResource(entry.titleRes),
                    summary = entry.summary,
                    onClick = { open(entry.page) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        if (!customGrid) {
            item {
                ModernSurface(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        "开启自由主界面网格后应用下方布局参数。",
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun GridBasicsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val context = LocalContext.current
    var gridCheckPending by remember { mutableStateOf(false) }
    var gridWarning by remember { mutableStateOf<String?>(null) }
    // Cancel an in-flight request when the user leaves the page: late Launcher
    // replies must never write a grid value behind the user's back.
    val gridCheck = remember { arrayOfNulls<GridWidget4x2PreflightClient.Request>(1) }
    DisposableEffect(prefs) {
        onDispose {
            gridCheck[0]?.cancel()
            gridCheck[0] = null
        }
    }
    val customGridRevision = LocalSettingsPreferenceRevisions.current[ConfigSchema.Grid.ENABLED.name()] ?: 0
    var customGrid by remember(customGridRevision) {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Grid.ENABLED.name(),
                ConfigSchema.Grid.ENABLED.uiDefault(),
            ),
        )
    }
    var iconSizeEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Grid.ICON_SIZE_ENABLED.name(),
                ConfigSchema.Grid.ICON_SIZE_ENABLED.uiDefault(),
            ),
        )
    }
    SettingsList(
        padding,
        stringResource(R.string.page_grid_basics),
        "调整网格行列数、图标大小与小组件宽度。",
    ) {
        BooleanSetting(
            prefs,
            ConfigSchema.Grid.ICON_SIZE_ENABLED,
            "自定义图标大小",
            "工作区、Dock、小文件夹、文件夹内图标与工作台 App 页；重启桌面后生效",
            masterEnabled,
        ) { iconSizeEnabled = it }
        IntSetting(
            prefs,
            launcher450IconSizeSpec,
            masterEnabled && iconSizeEnabled,
        )
        BooleanSetting(
            prefs,
            ConfigSchema.Grid.ENABLED,
            "自由主界面网格",
            "控制自定义行列数是否参与布局；重启桌面后生效",
            masterEnabled,
        ) { customGrid = it }
        gridDimensionSpecs.forEach { spec ->
            IntSetting(
                prefs,
                spec,
                masterEnabled && customGrid && !gridCheckPending,
                steps = DiscreteSliderSteps.forIntegerRange(spec.min, spec.max(context)),
                // Safe grid stops persist as their label changes. A transition
                // below 4 must still wait for Launcher widget preflight on release.
                previewWriteAllowed = { proposed ->
                    !GridWidget4x2PreflightPolicy.needsCheck(
                        prefs.getInt(spec.key, spec.default), proposed.roundToInt(),
                    )
                },
                beforeSave = { proposed, commit ->
                    val current = prefs.getInt(spec.key, spec.default)
                    val target = proposed.roundToInt()
                    if (!GridWidget4x2PreflightPolicy.needsCheck(current, target)) {
                        commit()
                    } else if (!gridCheckPending) {
                        gridCheckPending = true
                        val token = prefs.getString(
                            WidgetComponentStore.DISCOVERY_TOKEN_KEY, "",
                        ).orEmpty()
                        gridCheck[0] = GridWidget4x2PreflightClient.start(
                            context, token,
                        ) { status ->
                            gridCheckPending = false
                            gridCheck[0] = null
                            when (status) {
                                GridWidget4x2PreflightClient.CLEAR -> {
                                    // A concurrent config change invalidates this check.
                                    if (prefs.getInt(spec.key, spec.default) == current &&
                                        prefs.getBoolean(
                                            ConfigSchema.Core.ENABLED.name(),
                                            ConfigSchema.Core.ENABLED.uiDefault(),
                                        ) && prefs.getBoolean(
                                            ConfigSchema.Grid.ENABLED.name(),
                                            ConfigSchema.Grid.ENABLED.uiDefault(),
                                        )
                                    ) {
                                        commit()
                                    }
                                }
                                GridWidget4x2PreflightClient.BLOCKED ->
                                    gridWarning = "检测到桌面存在 4×2 小组件。行数或列数不能降低到 4 以下，请先调整或移除对应小组件。本次修改未保存。"
                                else ->
                                    gridWarning = "无法确认桌面是否存在 4×2 小组件。请保持桌面进程运行，确认 LSPosed 服务可用后重试。本次修改未保存。"
                            }
                        }
                    }
                },
            )
        }
        if (gridCheckPending) {
            Text(
                "正在检查桌面 4×2 小组件，确认安全前不会保存行列数……",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                fontSize = 13.sp,
            )
        }
        BooleanSetting(
            prefs,
            ConfigSchema.Grid.WIDGET_HORIZONTAL_STRETCH,
            "小组件随水平边距拉伸",
            "多列小组件随水平距离偏移调整宽度；关闭时保持原尺寸并居中；1×1 始终不拉伸；重启桌面后生效",
            masterEnabled && customGrid,
        )
    }
    WindowDialog(
        show = gridWarning != null,
        title = "网格尺寸无法修改",
        onDismissRequest = { gridWarning = null },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(gridWarning.orEmpty(), fontSize = 15.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                // Same large action height/padding as the Restart Scopes dialog.
                Button(
                    onClick = { gridWarning = null },
                    minWidth = 164.dp,
                    minHeight = 42.dp,
                    insideMargin = PaddingValues(horizontal = 26.dp, vertical = 8.dp),
                ) {
                    Text("知道了")
                }
            }
        }
    }
}

@Composable
internal fun GridLandscapePage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val customGrid = prefs.getBoolean(
        ConfigSchema.Grid.ENABLED.name(),
        ConfigSchema.Grid.ENABLED.uiDefault(),
    )
    val landscapeSpecs = gridSpecs.filter {
        it.key.startsWith("grid_landscape") || it.key == "indicator_landscape_y"
    }
    DenseSettingsList(
        padding,
        stringResource(R.string.page_grid_landscape),
        "调整横屏桌面的间距与页面指示器位置。",
    ) {
        groupedIntSettings(landscapeSpecs, prefs, masterEnabled && customGrid)
    }
}

@Composable
internal fun GridPortraitPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val customGrid = prefs.getBoolean(
        ConfigSchema.Grid.ENABLED.name(),
        ConfigSchema.Grid.ENABLED.uiDefault(),
    )
    val portraitSpecs = gridSpecs.filter {
        it.key.startsWith("grid_portrait") || it.key == "indicator_portrait_y"
    }
    DenseSettingsList(
        padding,
        stringResource(R.string.page_grid_portrait),
        "调整竖屏桌面的间距与页面指示器位置。",
    ) {
        groupedIntSettings(portraitSpecs, prefs, masterEnabled && customGrid)
    }
}

@Composable
internal fun GridSplitPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val customGrid = prefs.getBoolean(
        ConfigSchema.Grid.ENABLED.name(),
        ConfigSchema.Grid.ENABLED.uiDefault(),
    )
    SettingsList(
        padding,
        stringResource(R.string.page_grid_split),
        "分屏使用系统 pane 作为边界，只在这里调整额外距离。",
    ) {
        splitGridSpecs.forEach { IntSetting(prefs, it, masterEnabled && customGrid) }
    }
}

