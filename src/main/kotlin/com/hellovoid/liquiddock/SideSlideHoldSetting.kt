package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

@Composable
internal fun SideSlideHoldSetting(
    prefs: SharedPreferences,
    enabled: Boolean,
) {
    val key = SideSlideHoldFeatureConfig.KEY
    var value by remember(key) {
        mutableStateOf(prefs.getBoolean(key, SideSlideHoldFeatureConfig.DEFAULT_ENABLED))
    }
    val distanceKey = SideSlideHoldFeatureConfig.SECOND_STAGE_DISTANCE_PX_KEY
    var secondStageDistancePx by remember(distanceKey) {
        mutableStateOf(
            prefs.getInt(
                distanceKey,
                SideSlideHoldFeatureConfig.DEFAULT_SECOND_STAGE_DISTANCE_PX,
            ).coerceIn(
                SideSlideHoldFeatureConfig.MIN_SECOND_STAGE_DISTANCE_PX,
                SideSlideHoldFeatureConfig.MAX_SECOND_STAGE_DISTANCE_PX,
            ),
        )
    }
    SwitchPreference(
        checked = value,
        onCheckedChange = {
            value = it
            prefs.edit().putBoolean(key, it).apply()
        },
        title = "侧滑停靠呼出侧边栏",
        summary = "Launcher 4.50 Pad：保留第一段系统返回反馈；达到第二段触发距离并短暂停留后振动、弹出小 Dock；修改后在“重启作用域”中重启桌面与安全中心",
        enabled = enabled,
    )
    SliderPreference(
        value = secondStageDistancePx.toFloat(),
        onValueChange = { raw ->
            val rounded = ((raw / 5f).toInt() * 5).coerceIn(
                SideSlideHoldFeatureConfig.MIN_SECOND_STAGE_DISTANCE_PX,
                SideSlideHoldFeatureConfig.MAX_SECOND_STAGE_DISTANCE_PX,
            )
            secondStageDistancePx = rounded
            prefs.edit().putInt(distanceKey, rounded).apply()
        },
        title = "第二段振动触发距离",
        summary = "与屏幕边缘的横向拖动距离；默认 240 px。调大可拉开与第一段系统返回振动的间隔",
        valueText = "$secondStageDistancePx px",
        enabled = enabled && value,
        valueRange = SideSlideHoldFeatureConfig.MIN_SECOND_STAGE_DISTANCE_PX.toFloat()..
                SideSlideHoldFeatureConfig.MAX_SECOND_STAGE_DISTANCE_PX.toFloat(),
        steps = 59,
        insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 2.dp),
    )
}
