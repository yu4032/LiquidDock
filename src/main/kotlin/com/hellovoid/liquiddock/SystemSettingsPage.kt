package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.basic.SmallTitle

/** Dedicated Settings app integration; no coupling to LiquidDock's own GUI theme toggle. */
@Composable
internal fun SystemSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            PageHeader(
                "系统设置",
                "仅针对 OS3.0.310.0.WAOCNXM。由真正的 Prismal 组件接管可见开关与触摸，GPU 局部采样原生设置内容；系统控件仍负责开关校验与保存。",
            )
        }
        item { SmallTitle("控件视觉") }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.SystemSettings.PRISMAL_SWITCH,
                    "使用 Prismal 开关",
                    "试验功能，默认关闭。用 PrismalAGSL 原组件替换原生开关的外观和手势；后台原控件负责校验及状态持久化。背景经 GPU RenderNode 采样，兼容性待真机验证。修改后重启「系统设置」。",
                    enabled = masterEnabled,
                )
            }
        }
    }
}
