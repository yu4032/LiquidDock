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
                "仅针对 OS3.0.310.0.WAOCNXM 的 MIUIX 开关（SlidingButton / SlidingSwitch / SlidingSwitchCompat）。其他 ROM 与 Compose SPA 页面保持原状。",
            )
        }
        item { SmallTitle("控件视觉") }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.SystemSettings.PRISMAL_SWITCH,
                    "使用 Prismal 开关",
                    "实验功能，默认关闭。保留系统原生开关业务逻辑，打开后重启「系统设置」作用域。无变化时在「关于」开启诊断日志，并检查 [DC][SettingsSwitch]。",
                    enabled = masterEnabled,
                )
            }
        }
    }
}
