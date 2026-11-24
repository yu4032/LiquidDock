package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellovoid.liquiddock.config.ConfigSchema
import java.io.File
import kotlinx.coroutines.delay

/**
 * Reads OS4 native hook installation states written by libliquiddock4.so
 * and surfaces per-feature unhooked warnings in the corresponding settings pages.
 */
object Os4HookHealth {
    private val statusFile = File("/sdcard/Android/media/com.hellovoid.liquiddock/hook_status.conf")

    fun readStatus(): Map<String, Boolean> = runCatching {
        if (!statusFile.isFile) return emptyMap()
        val map = LinkedHashMap<String, Boolean>()
        statusFile.forEachLine { line ->
            val idx = line.indexOf('=')
            if (idx > 0) {
                val k = line.substring(0, idx).trim()
                val v = line.substring(idx + 1).trim()
                if (k != "pid") map[k] = (v == "1")
            }
        }
        map
    }.getOrDefault(emptyMap())

    private fun isDetached(key: String, status: Map<String, Boolean>): Boolean {
        if (status[key] != false) return false
        // If the feature was disabled when the launcher started, its hook was intentionally
        // not armed yet; it requires a launcher restart rather than a detached-hook alarm.
        if (status["boot_$key"] == false) return false
        return true
    }

    internal fun detachedMessage(
        page: Page,
        prefs: SharedPreferences,
        status: Map<String, Boolean>,
        os4Launcher: Boolean,
    ): String? {
        if (!os4Launcher || status.isEmpty()) return null
        if (!prefs.getBoolean(ConfigSchema.Core.ENABLED.name(), ConfigSchema.Core.ENABLED.uiDefault())) {
            return null
        }

        val gridOn = prefs.getBoolean(ConfigSchema.Grid.ENABLED.name(), ConfigSchema.Grid.ENABLED.uiDefault())
        val dockOn = prefs.getBoolean(ConfigSchema.Dock.ENABLED.name(), ConfigSchema.Dock.ENABLED.uiDefault())
        val dividerOn = prefs.getBoolean(ConfigSchema.Divider.ENABLED.name(), ConfigSchema.Divider.ENABLED.uiDefault())
        val glassOn = prefs.getBoolean(ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault()) ||
            prefs.getBoolean(
                ConfigSchema.Glass.SOFT_GLASS_ENABLED.name(),
                ConfigSchema.Glass.SOFT_GLASS_ENABLED.uiDefault(),
            )
        val iconGlassOn = glassOn && prefs.getBoolean(
            ConfigSchema.Glass.ICON_GLASS.name(), ConfigSchema.Glass.ICON_GLASS.uiDefault())
        val iconCornerKey = ConfigSchema.Glass.ICON_CORNER_RADIUS
        val iconCornerTenthsKey = "${iconCornerKey.name()}_tenths"
        val iconCorner = if (prefs.contains(iconCornerTenthsKey)) {
            prefs.getInt(iconCornerTenthsKey, iconCornerKey.uiDefault())
        } else {
            prefs.getInt(iconCornerKey.name(), iconCornerKey.uiDefault())
        }
        val widgetGlassOn = glassOn && prefs.getBoolean(
            ConfigSchema.Glass.WIDGET_GLASS.name(), ConfigSchema.Glass.WIDGET_GLASS.uiDefault())
        val recentsBlurOn = listOf(
            ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT,
            ConfigSchema.Recents.FOLDER_BLUR_PERCENT,
            ConfigSchema.Recents.SHORTCUT_BLUR_PERCENT,
            ConfigSchema.Workstation.ALL_APPS_BLUR_PERCENT,
            ConfigSchema.Workstation.WS_ALL_APPS_BLUR_PERCENT,
        ).any { key -> prefs.getInt(key.name(), key.uiDefault()) != 100 }

        return when (page) {
            Page.Grid, Page.GridBasics, Page.Workstation ->
                if (gridOn && isDetached("grid", status)) {
                    "桌面网格布局 / 旋转 Hook 已脱钩，自定义行列数可能未生效。"
                } else null
            Page.Dock ->
                if (dockOn && isDetached("dock", status)) {
                    "Dock 几何参数 Hook 已脱钩，宽高、间距或底边距调整可能未生效。"
                } else null
            Page.Divider ->
                if (dividerOn && isDetached("divider", status)) {
                    "Dock 分割线 Hook 已脱钩，分割线绘制可能未生效。"
                } else null
            Page.GlassIcons -> when {
                iconGlassOn && isDetached("glass_icons", status) ->
                    "图标玻璃 Hook 已脱钩，图标材质与圆角可能未生效。"
                iconGlassOn && iconCorner > 0 && isDetached("glass_icon_radius", status) ->
                    "图标圆角 / 旋转同步 Hook 已脱钩，旋转后圆角可能无法自动跟随尺寸变化。"
                else -> null
            }
            Page.GlassWidgets ->
                if (widgetGlassOn && isDetached("glass_widgets", status)) {
                    "小组件玻璃 / RemoteViews Hook 已脱钩，小组件材质或组件隐藏可能未生效。"
                } else null
            Page.SoftGlass, Page.SoftGlassComponents, Page.Liquid, Page.GlassHub -> when {
                glassOn && isDetached("glass", status) ->
                    "原生玻璃材质 Hook 已脱钩，玻璃效果可能未生效。"
                (page == Page.SoftGlass || page == Page.SoftGlassComponents) &&
                    recentsBlurOn && isDetached("recents", status) ->
                    "多任务后台模糊 Hook 已脱钩，后台模糊度调整可能未生效。"
                else -> null
            }
            Page.Home -> {
                val detached = ArrayList<String>()
                if (glassOn && isDetached("glass", status)) detached.add("原生玻璃")
                if (iconGlassOn && isDetached("glass_icons", status)) detached.add("图标玻璃")
                else if (iconGlassOn && iconCorner > 0 && isDetached("glass_icon_radius", status)) {
                    detached.add("图标圆角旋转同步")
                }
                if (widgetGlassOn && isDetached("glass_widgets", status)) detached.add("小组件玻璃")
                if (gridOn && isDetached("grid", status)) detached.add("桌面网格")
                if (dockOn && isDetached("dock", status)) detached.add("Dock 几何")
                if (dividerOn && isDetached("divider", status)) detached.add("Dock 分割线")
                if (recentsBlurOn && isDetached("recents", status)) detached.add("后台模糊")
                if (detached.isEmpty()) null
                else "检测到以下功能 Hook 脱钩：${detached.joinToString("、")}。请尝试重启桌面或检查模块日志。"
            }
            else -> null
        }
    }
}

@Composable
fun rememberOs4HookStatus(enabled: Boolean): Map<String, Boolean> {
    var status by remember(enabled) {
        mutableStateOf(if (enabled) Os4HookHealth.readStatus() else emptyMap())
    }
    if (enabled) {
        LaunchedEffect(Unit) {
            while (true) {
                status = Os4HookHealth.readStatus()
                delay(2000)
            }
        }
    }
    return status
}

private val UnhookedBg = Color(0xFF9A3412)
private val UnhookedFg = Color(0xFFFFFFFF)

@Composable
fun HookUnhookedBanner(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(UnhookedBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        BasicText(
            text = "功能脱钩提示",
            style = TextStyle(color = UnhookedFg, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        BasicText(
            text = message,
            style = TextStyle(color = UnhookedFg, fontSize = 12.sp),
        )
    }
}
