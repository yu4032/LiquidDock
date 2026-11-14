package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellovoid.liquiddock.config.ConfigSchema
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class HookProbeState(val label: String) {
    ACTIVE("已生效"),
    DISABLED("未启用"),
    MISSING("未生效"),
    PAUSED("已暂停"),
}

data class HookProbeItem(
    val id: String,
    val title: String,
    val state: HookProbeState,
    val detail: String,
)

data class HookFeatureConfig(
    val masterEnabled: Boolean,
    val glassEnabled: Boolean,
    val iconGlassEnabled: Boolean,
    val widgetGlassEnabled: Boolean,
    val gridEnabled: Boolean,
    val iconSizeEnabled: Boolean,
    val bigFolderEnabled: Boolean,
    val widget1x1Enabled: Boolean,
    val dockEnabled: Boolean,
    val dividerEnabled: Boolean,
    val recentsBlurEnabled: Boolean,
) {
    companion object {
        fun fromPrefs(prefs: SharedPreferences): HookFeatureConfig {
            fun bool(key: com.hellovoid.liquiddock.config.ConfigKey<Boolean>): Boolean =
                prefs.getBoolean(key.name(), key.uiDefault())

            val glassOn = bool(ConfigSchema.Glass.ENABLED)
            val recentsBlur = prefs.getInt(
                ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT.name(),
                ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT.uiDefault(),
            ) != 100

            return HookFeatureConfig(
                masterEnabled = bool(ConfigSchema.Core.ENABLED),
                glassEnabled = glassOn,
                iconGlassEnabled = glassOn && bool(ConfigSchema.Glass.ICON_GLASS),
                widgetGlassEnabled = glassOn && bool(ConfigSchema.Glass.WIDGET_GLASS),
                gridEnabled = bool(ConfigSchema.Grid.ENABLED),
                iconSizeEnabled = bool(ConfigSchema.Grid.ICON_SIZE_ENABLED),
                bigFolderEnabled = bool(ConfigSchema.Grid.BIG_FOLDER_ENABLED),
                widget1x1Enabled = bool(ConfigSchema.Grid.WIDGET_1X1_TO_2X2_ENABLED),
                dockEnabled = bool(ConfigSchema.Dock.ENABLED),
                dividerEnabled = bool(ConfigSchema.Divider.ENABLED),
                recentsBlurEnabled = recentsBlur,
            )
        }
    }
}

data class HookMonitorSnapshot(
    val pid: Int?,
    val hasStatusFile: Boolean,
    val items: List<HookProbeItem>,
) {
    val enabledCount: Int get() = items.count { it.state != HookProbeState.DISABLED }
    val activeCount: Int get() = items.count { it.state == HookProbeState.ACTIVE }
    val missingCount: Int get() = items.count { it.state == HookProbeState.MISSING }
}

private val PAD_BYPASS_REGEX = Regex("""OS4 big folder Pad bypass installed: patched=(\d+)""")
private val WIDGET_1X1_MENU_REGEX = Regex("""OS4 widget 1x1<->2x2 menu installed=(\d+)/(\d+)""")

internal fun parseStatusKeyValues(text: String): Map<String, String> {
    if (text.isBlank()) return emptyMap()
    val map = LinkedHashMap<String, String>()
    text.lineSequence().forEach { line ->
        val idx = line.indexOf('=')
        if (idx > 0) {
            val k = line.substring(0, idx).trim()
            val v = line.substring(idx + 1).trim()
            if (k.isNotEmpty()) map[k] = v
        }
    }
    return map
}

internal fun evaluateHookMonitor(
    config: HookFeatureConfig,
    statusMap: Map<String, String>,
    bootLog: String,
): HookMonitorSnapshot {
    val pid = statusMap["pid"]?.toIntOrNull()

    fun resolve(enabled: Boolean, hooked: Boolean): HookProbeState = when {
        !config.masterEnabled -> HookProbeState.PAUSED
        !enabled -> HookProbeState.DISABLED
        hooked -> HookProbeState.ACTIVE
        else -> HookProbeState.MISSING
    }

    fun probe(
        id: String,
        title: String,
        enabled: Boolean,
        hooked: Boolean,
    ): HookProbeItem {
        val state = resolve(enabled, hooked)
        val detail = when (state) {
            HookProbeState.ACTIVE -> "Hook 已生效"
            HookProbeState.DISABLED -> "开关未开启"
            HookProbeState.PAUSED -> "总开关已暂停"
            HookProbeState.MISSING -> "偏移或 Hook 异常"
        }
        return HookProbeItem(id = id, title = title, state = state, detail = detail)
    }

    val glassHooked = statusMap["glass"] == "1" ||
        bootLog.contains("OS4 glass pipeline installed") ||
        bootLog.contains("OS4 glass gates installed")

    val iconGlassHooked = statusMap["glass_icons"] == "1" ||
        bootLog.contains("icon glass hooks installed")

    val widgetGlassHooked = statusMap["glass_widgets"] == "1" ||
        (bootLog.contains("OS4 RemoteViews component hook installed") &&
            bootLog.contains("OS4 RemoteViews bg hook installed"))

    val gridHooked = statusMap["grid"] == "1" ||
        bootLog.contains("complete layout pipeline ready=1")

    val homeIconsHooked = bootLog.contains("home icons applied:") ||
        bootLog.contains("home margin currentConfig hooked")

    val folderGateHooked = bootLog.contains("OS4 big folder gate installed:")
    val padBypassMatch = PAD_BYPASS_REGEX.findAll(bootLog).lastOrNull()
    val folderBypassPatched = padBypassMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
    val folderHooked = folderGateHooked || (folderBypassPatched != null && folderBypassPatched > 0)

    val widget1x1Match = WIDGET_1X1_MENU_REGEX.findAll(bootLog).lastOrNull()
    val widget1x1Installed = widget1x1Match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val widget1x1Resize = bootLog.contains("widget resize: 1x1 enlarge enabled")
    val widget1x1Hooked = widget1x1Installed > 0 || widget1x1Resize

    val dockHooked = statusMap["dock"] == "1"

    val dividerHooked = statusMap["divider"] == "1" ||
        (bootLog.contains("divider: inner installed") && bootLog.contains("divider: bgPaint installed"))

    val recentsHooked = statusMap["recents"] == "1" ||
        bootLog.contains("OS4 Recents blur gate installed:")

    val items = listOf(
        probe("glass", "柔光玻璃", config.glassEnabled, glassHooked),
        probe("glass_icons", "图标玻璃", config.iconGlassEnabled, iconGlassHooked),
        probe("glass_widgets", "小组件玻璃", config.widgetGlassEnabled, widgetGlassHooked),
        probe("grid", "桌面网格", config.gridEnabled, gridHooked),
        probe("home_icons", "图标与边距", config.iconSizeEnabled, homeIconsHooked),
        probe("big_folder", "大文件夹", config.bigFolderEnabled, folderHooked),
        probe("widget_1x1", "1×1↔2×2", config.widget1x1Enabled, widget1x1Hooked),
        probe("dock", "Dock 布局", config.dockEnabled, dockHooked),
        probe("divider", "分割线", config.dividerEnabled, dividerHooked),
        probe("recents", "最近任务模糊", config.recentsBlurEnabled, recentsHooked),
    )

    return HookMonitorSnapshot(
        pid = pid,
        hasStatusFile = statusMap.isNotEmpty(),
        items = items,
    )
}

object HookMonitorStore {
    private val mediaDir = File("/sdcard/Android/media/com.hellovoid.liquiddock")
    private val statusFile = File(mediaDir, "hook_status.conf")
    private val bootLogFile = File(mediaDir, "liquiddock4-boot.log")
    private val bootLogPrevFile = File(mediaDir, "liquiddock4-boot.log.prev")

    fun load(prefs: SharedPreferences): HookMonitorSnapshot {
        val config = HookFeatureConfig.fromPrefs(prefs)
        val statusText = runCatching {
            if (statusFile.isFile) statusFile.readText() else ""
        }.getOrDefault("")
        val statusMap = parseStatusKeyValues(statusText)

        val bootLog = buildString {
            runCatching {
                if (bootLogPrevFile.isFile) append(bootLogPrevFile.readText()).append('\n')
            }
            runCatching {
                if (bootLogFile.isFile) append(bootLogFile.readText())
            }
        }
        return evaluateHookMonitor(config, statusMap, bootLog)
    }
}

@Composable
fun HookMonitorCard(
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var refreshTick by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<HookMonitorSnapshot?>(null) }

    LaunchedEffect(masterEnabled, refreshTick) {
        snapshot = withContext(Dispatchers.IO) {
            HookMonitorStore.load(prefs)
        }
    }

    val snap = snapshot
    val scheme = MiuixTheme.colorScheme

    ModernSurface(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hook 监控台",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                        )
                        if (snap?.pid != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "PID ${snap.pid}",
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(scheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    val summaryText = when {
                        snap == null -> "正在检测模块挂载状态..."
                        !masterEnabled -> "总开关已暂停，重启桌面或恢复开关后生效"
                        !snap.hasStatusFile && snap.activeCount == 0 ->
                            "暂未读取到状态文件，请重启桌面后刷新"
                        snap.missingCount == 0 ->
                            "Hook 全部就绪 (${snap.activeCount}/${snap.enabledCount})"
                        else ->
                            "Hook 已生效 ${snap.activeCount}/${snap.enabledCount}，${snap.missingCount} 项异常"
                    }
                    Text(
                        text = summaryText,
                        fontSize = 12.sp,
                        color = if (snap != null && snap.missingCount > 0 && masterEnabled) {
                            Color(0xFFD97706)
                        } else {
                            scheme.onSurfaceVariantSummary
                        },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonitorActionChip(
                        label = "刷新",
                        onClick = { refreshTick++ },
                    )
                    MonitorActionChip(
                        label = "日志",
                        onClick = onOpenLogs,
                    )
                }
            }

            if (snap != null) {
                Spacer(Modifier.height(12.dp))
                val rows = snap.items.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            pair.forEach { item ->
                                HookProbeTile(
                                    item = item,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorActionChip(
    label: String,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.primary,
        )
    }
}

@Composable
private fun HookProbeTile(
    item: HookProbeItem,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val (dotColor, stateColor) = when (item.state) {
        HookProbeState.ACTIVE -> Color(0xFF16A34A) to Color(0xFF16A34A)
        HookProbeState.MISSING -> Color(0xFFDC2626) to Color(0xFFDC2626)
        HookProbeState.DISABLED, HookProbeState.PAUSED ->
            scheme.onSurfaceVariantSummary.copy(alpha = 0.55f) to scheme.onSurfaceVariantSummary
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = item.state.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = stateColor,
                )
            }
            Text(
                text = item.detail,
                fontSize = 10.sp,
                color = scheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
