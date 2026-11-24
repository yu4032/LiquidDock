package com.hellovoid.liquiddock

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellovoid.liquiddock.config.ConfigSchema
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Slider
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
    val availability = LocalSettingsAvailability.current
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
        dockEntries.filter { availability.allows(it.page) }.forEach { entry ->
            item {
                ModernFeatureCard(
                    title = stringResource(entry.titleRes),
                    summary = entry.summary,
                    onClick = { open(entry.page) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                    page = entry.page,
                )
            }
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
internal fun DividerPage(padding: PaddingValues, prefs: SharedPreferences,
                         masterEnabled: Boolean, searchFocus: SettingsFocusRequest? = null) {
    val legacyDefault = remember { hasLegacyDividerConfig(prefs) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Divider.ENABLED.name(), legacyDefault)) }
    DenseSettingsList(padding, stringResource(R.string.page_divider),
        focusItemIndex = searchFocus?.let { customDenseFocusIndex(Page.Divider, it.key) },
        focusSequence = searchFocus?.sequence) {
        item(key = "divider-enabled") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Divider.ENABLED, "自定义 Dock 分隔线", "独立于 Dock 几何开关；宽度与偏移固定使用 dp；实时生效", masterEnabled, default = legacyDefault) {
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isOs4 = LocalSettingsAvailability.current.isOs4Launcher()
    var multiWindowEnabled by remember { mutableStateOf(false) }
    var stageCount by remember { mutableStateOf(SystemMultiWindowProps.DEFAULT_STAGES) }
    var stageDraft by remember { mutableStateOf<Float?>(null) }
    var multiWindowBusy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val (enabledState, countState) = withContext(Dispatchers.IO) {
            SystemMultiWindowProps.isEnabled() to SystemMultiWindowProps.currentStageCount()
        }
        multiWindowEnabled = enabledState
        stageCount = countState
    }
    val availability = LocalSettingsAvailability.current
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
                "调整工作台 Dock、所有应用布局与多分屏能力。",
            )
        }
        item {
            SettingsCard {
                SwitchPreference(
                    checked = multiWindowEnabled,
                    onCheckedChange = { target ->
                        if (multiWindowBusy) return@SwitchPreference
                        multiWindowBusy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                SystemMultiWindowProps.setEnabled(prefs, target, stageCount)
                            }
                            if (ok) {
                                multiWindowEnabled = target
                                Toast.makeText(
                                    context,
                                    if (target) "已写入多分屏属性（上限 $stageCount 个），重启设备后生效"
                                    else "已恢复默认属性，重启设备后生效",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                Toast.makeText(context, "写入系统属性失败，请确认已授予 Root 权限", Toast.LENGTH_SHORT).show()
                            }
                            multiWindowBusy = false
                        }
                    },
                    title = "多分屏",
                    summary = "通过 Root 写入多分屏上限（最多 6 个分屏）与算力等级系统属性；开启、关闭或调整数量后需重启设备生效",
                    enabled = !multiWindowBusy,
                )
            }
        }
        item {
            SettingsCard {
                val displayCount = (stageDraft?.roundToInt() ?: stageCount)
                    .coerceIn(SystemMultiWindowProps.MIN_STAGES, SystemMultiWindowProps.MAX_STAGES)
                Column(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = "最大分屏数量",
                        summary = "控制同屏最多可展开的分屏数（3–6 个）；重启设备后生效",
                        enabled = multiWindowEnabled && !multiWindowBusy,
                        endActions = { Text("$displayCount 个", fontSize = 13.sp) },
                    )
                    Slider(
                        value = stageDraft ?: stageCount.toFloat(),
                        onValueChange = { stageDraft = it },
                        valueRange = SystemMultiWindowProps.MIN_STAGES.toFloat()..SystemMultiWindowProps.MAX_STAGES.toFloat(),
                        steps = SystemMultiWindowProps.MAX_STAGES - SystemMultiWindowProps.MIN_STAGES - 1,
                        enabled = multiWindowEnabled && !multiWindowBusy,
                        onValueChangeFinished = {
                            val next = (stageDraft?.roundToInt() ?: stageCount)
                                .coerceIn(SystemMultiWindowProps.MIN_STAGES, SystemMultiWindowProps.MAX_STAGES)
                            stageDraft = null
                            if (next == stageCount || multiWindowBusy) return@Slider
                            val prev = stageCount
                            stageCount = next
                            multiWindowBusy = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    SystemMultiWindowProps.updateMaxStages(prefs, next)
                                }
                                if (ok) {
                                    Toast.makeText(context, "已写入最多 $next 个分屏，重启设备后生效", Toast.LENGTH_SHORT).show()
                                } else {
                                    stageCount = prev
                                    Toast.makeText(context, "写入系统属性失败，请确认已授予 Root 权限", Toast.LENGTH_SHORT).show()
                                }
                                multiWindowBusy = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 14.dp),
                    )
                }
            }
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
