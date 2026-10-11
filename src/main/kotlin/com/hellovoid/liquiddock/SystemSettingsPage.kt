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
                    "试验功能，默认关闭。绘制玻璃质感和边缘高光；保留原生触摸、校验和状态写入，着色器失败时回退系统绘制。启用或关闭后重启「系统设置」。",
                    enabled = masterEnabled,
                )
            }
        }
    }
}
