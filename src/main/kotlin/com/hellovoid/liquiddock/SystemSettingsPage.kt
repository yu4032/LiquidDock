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
                "仅针对 OS3.0.310.0.WAOCNXM 的 MIUIX 开关。原生 Canvas/AGSL 绘制，不加载 Prismal Compose 或 App Startup；没有真实背景折射。",
            )
        }
        item { SmallTitle("控件视觉") }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.SystemSettings.PRISMAL_SWITCH,
                    "使用原生玻璃开关",
                    "试验功能，默认关闭。参照 Prismal 原组件的 64×28 轨道、40×24 玻璃滑块与触控弹簧动画；保留原生校验与状态提交。尚不支持实际背景折射。启用或关闭后需重启「系统设置」。",
                    enabled = masterEnabled,
                )
            }
        }
    }
}
