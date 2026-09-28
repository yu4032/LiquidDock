package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun SideSlideHoldSetting(
    prefs: SharedPreferences,
    enabled: Boolean,
) {
    val key = SideSlideHoldFeatureConfig.KEY
    var value by remember(key) {
        mutableStateOf(prefs.getBoolean(key, SideSlideHoldFeatureConfig.DEFAULT_ENABLED))
    }
    SwitchPreference(
        checked = value,
        onCheckedChange = {
            value = it
            prefs.edit().putBoolean(key, it).apply()
        },
        title = "侧滑停靠呼出侧边栏",
        summary = "Launcher 4.50 Pad：复用系统原生侧滑状态，进入快速切换就绪态后继续停留约 300 ms 呼出安全中心侧边栏；修改后重启桌面与安全中心",
        enabled = enabled,
    )
}
