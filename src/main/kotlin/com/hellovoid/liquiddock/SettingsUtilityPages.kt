package com.hellovoid.liquiddock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.hellovoid.liquiddock.config.ConfigSchema
import com.hellovoid.liquiddock.config.PresetManager
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text

// Independent sidebar, data import/export and license pages. These share only
// the existing settings chrome; restoration still uses the original dialog and
// reset helper, and remains subject to the explicit user confirmation.
@Composable
internal fun SecurityCenterSidebarPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    val liquidGlassEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
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
                stringResource(R.string.page_security_center_sidebar),
                stringResource(R.string.security_center_sidebar_header_summary),
            )
        }
        item { SmallTitle(stringResource(R.string.security_center_sidebar_category_gesture)) }
        item { SettingsCard { SideSlideHoldSetting(prefs, masterEnabled) } }
        item { SmallTitle(stringResource(R.string.security_center_sidebar_category_appearance)) }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.SECURITY_CENTER_GLASS,
                    stringResource(R.string.liquid_security_center_glass_enable),
                    stringResource(R.string.liquid_security_center_glass_enable_summary),
                    masterEnabled && liquidGlassEnabled,
                )
            }
        }
        item { SmallTitle(stringResource(R.string.security_center_sidebar_category_scenes)) }
        sidebarSceneSettings.forEach { scene ->
            item(key = "sc-scene-entry:${scene.page.name}") {
                ModernFeatureCard(
                    title = stringResource(scene.page.titleRes),
                    summary = "独立玻璃开关、模糊度与颜色",
                    onClick = { open(scene.page) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
    }
}

@Composable
internal fun DataPage(padding: PaddingValues, activity: ComposeSettingsActivity) {
    var confirmDefaultReset by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item { PageHeader("预设", "默认配置、JSON 备份与恢复") }
        item { SmallTitle("预设") }
        item { SettingsCard { ArrowPreference("应用默认配置", summary = "恢复内置默认参数与开关（需确认）", onClick = { confirmDefaultReset = true }) } }
        item { SmallTitle("备份与应用") }
        item {
            SettingsCard {
                ArrowPreference("导出当前参数", summary = "保存为 LiquidDock JSON", onClick = activity::launchExport)
                ArrowPreference("导入参数", summary = "校验并恢复参数；完成后自动重启桌面", onClick = activity::launchImport)
            }
        }
    }
    AnimatedSettingsWindowDialog(
        show = confirmDefaultReset,
        title = "确认恢复默认配置",
        onDismissRequest = { confirmDefaultReset = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("将覆盖当前内置配置与开关并重启桌面。该操作不能直接撤销，建议先导出 JSON 备份。")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { confirmDefaultReset = false },
                    modifier = Modifier.weight(1f),
                    minHeight = 42.dp,
                    insideMargin = PaddingValues(horizontal = 26.dp, vertical = 8.dp),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        confirmDefaultReset = false
                        applyDefaultPreset(activity)
                    },
                    modifier = Modifier.weight(1f),
                    minHeight = 42.dp,
                    insideMargin = PaddingValues(horizontal = 26.dp, vertical = 8.dp),
                    destructive = true,
                ) {
                    Text("恢复默认", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
internal fun AboutPage(
    padding: PaddingValues,
    activity: ComposeSettingsActivity,
    prefs: SharedPreferences,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item { PageHeader("许可", "LiquidDock 使用的第三方开源项目与许可证") }
        item { SmallTitle("运行诊断") }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Debug.LOGGING,
                    "诊断日志",
                    "将 LiquidDock 运行诊断写入日志文件；修改后重启对应 Hook 作用域生效",
                )
            }
        }
        item { SmallTitle("界面与运行框架") }
        item {
            SettingsCard {
                ArrowPreference("Compose Miuix", summary = "MIUIX Compose 界面框架 · Apache-2.0", onClick = { openUrl(activity, "https://github.com/compose-miuix-ui/miuix") })
                ArrowPreference("PrismalAGSL", summary = "设置界面液态玻璃组件 · MIT", onClick = { openUrl(activity, "https://github.com/styropyr0/PrismalAGSL") })
                ArrowPreference("AndroidX / Jetpack", summary = "Activity、Preference、AppCompat · Apache-2.0", onClick = { openUrl(activity, "https://source.android.com/docs/setup/about/licenses") })
                ArrowPreference("LSPosed API", summary = "模块 Hook API · GPL-3.0", onClick = { openUrl(activity, "https://github.com/LSPosed/LSPosed") })
            }
        }
        item { SmallTitle("开源项目") }
        item {
            SettingsCard {
                ArrowPreference("HyperCeiler", summary = "开源模块项目 · GPL-3.0", onClick = { openUrl(activity, "https://github.com/ReChronoRain/HyperCeiler") })
                ArrowPreference("Prismal", summary = "液态玻璃光学模型 · MIT", onClick = { openUrl(activity, "https://github.com/styropyr0/Prismal") })
            }
        }
        item { SmallTitle("许可说明") }
        item {
            SettingsCard {
                ArrowPreference("第三方开源声明", summary = "依赖版本、用途与许可证文本链接", onClick = { openUrl(activity, "https://github.com/yu4032/LiquidDock/blob/main/THIRD_PARTY_NOTICES.md") })
            }
        }
    }
}

private fun applyDefaultPreset(activity: ComposeSettingsActivity) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    PresetManager.applyDefault(prefs.edit())
    Toast.makeText(activity, "默认配置已应用", Toast.LENGTH_LONG).show()
    activity.restartLauncher()
    // Destroy remembered page/slider state after applying a new full preset.
    activity.recreate()
}
