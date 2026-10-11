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
                "HyperOS 3.0.310.0 · Prismal 开关试验。仅匹配原生 MIUIX SlidingButton；不修改系统设置中的 Compose 页面。",
            )
        }
        item { SmallTitle("控件视觉") }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.SystemSettings.PRISMAL_SWITCH,
                    "使用 Prismal 开关",
                    "实验功能，默认关闭。保留系统开关的状态与点击回调；启用或关闭后需重启「系统设置」作用域。未匹配的开关继续使用原生外观。",
                    enabled = masterEnabled,
                )
            }
        }
    }
}
