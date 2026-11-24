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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    PENDING_RESTART("待重启"),
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
    val homeMarginsEnabled: Boolean = false,
    val bigFolderEnabled: Boolean,
    val folderGridEnabled: Boolean = false,
    val widget1x1Enabled: Boolean,
    val dockEnabled: Boolean,
    val dividerEnabled: Boolean,
    val recentsBlurEnabled: Boolean,
    val workstationEnabled: Boolean = false,
) {
    companion object {
        fun fromPrefs(prefs: SharedPreferences): HookFeatureConfig {
            fun bool(key: com.hellovoid.liquiddock.config.ConfigKey<Boolean>): Boolean =
                prefs.getBoolean(key.name(), key.uiDefault())

            val glassOn = bool(ConfigSchema.Glass.ENABLED) || bool(ConfigSchema.Glass.SOFT_GLASS_ENABLED)
            val recentsBlur = listOf(
                ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT,
                ConfigSchema.Recents.FOLDER_BLUR_PERCENT,
                ConfigSchema.Recents.SHORTCUT_BLUR_PERCENT,
                ConfigSchema.Workstation.ALL_APPS_BLUR_PERCENT,
                ConfigSchema.Workstation.WS_ALL_APPS_BLUR_PERCENT,
            ).any { key -> prefs.getInt(key.name(), key.uiDefault()) != 100 }
            fun nonZero(key: com.hellovoid.liquiddock.config.ConfigKey<Int>): Boolean {
                val tenthsKey = "${key.name()}_tenths"
                return if (prefs.contains(tenthsKey)) {
                    prefs.getInt(tenthsKey, key.uiDefault()) != 0
                } else {
                    prefs.getInt(key.name(), key.uiDefault()) != 0
                }
            }
            val homeMarginKeys = listOf(
                ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE,
                ConfigSchema.Grid.LANDSCAPE_TOP_DISTANCE,
                ConfigSchema.Grid.LANDSCAPE_BOTTOM_DISTANCE,
                ConfigSchema.Grid.LANDSCAPE_ROW_GAP,
                ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y,
                ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE,
                ConfigSchema.Grid.PORTRAIT_TOP_DISTANCE,
                ConfigSchema.Grid.PORTRAIT_BOTTOM_DISTANCE,
                ConfigSchema.Grid.PORTRAIT_ROW_GAP,
                ConfigSchema.Grid.PORTRAIT_INDICATOR_Y,
                ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET,
                ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING,
                ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING,
                ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_INDICATOR_Y,
                ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET,
                ConfigSchema.Workstation.ALL_APPS_PORTRAIT_TOP_SPACING,
                ConfigSchema.Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING,
                ConfigSchema.Workstation.ALL_APPS_PORTRAIT_INDICATOR_Y,
                ConfigSchema.Workstation.DESKTOP_LANDSCAPE_HORIZONTAL_OFFSET,
                ConfigSchema.Workstation.DESKTOP_LANDSCAPE_TOP_SPACING,
                ConfigSchema.Workstation.DESKTOP_LANDSCAPE_BOTTOM_SPACING,
                ConfigSchema.Workstation.DESKTOP_LANDSCAPE_INDICATOR_Y,
                ConfigSchema.Workstation.DESKTOP_PORTRAIT_HORIZONTAL_OFFSET,
                ConfigSchema.Workstation.DESKTOP_PORTRAIT_TOP_SPACING,
                ConfigSchema.Workstation.DESKTOP_PORTRAIT_BOTTOM_SPACING,
                ConfigSchema.Workstation.DESKTOP_PORTRAIT_INDICATOR_Y,
            )

            return HookFeatureConfig(
                masterEnabled = bool(ConfigSchema.Core.ENABLED),
                glassEnabled = glassOn,
                iconGlassEnabled = glassOn && bool(ConfigSchema.Glass.ICON_GLASS),
                widgetGlassEnabled = glassOn && bool(ConfigSchema.Glass.WIDGET_GLASS),
                gridEnabled = bool(ConfigSchema.Grid.ENABLED),
                iconSizeEnabled = bool(ConfigSchema.Grid.ICON_SIZE_ENABLED),
                homeMarginsEnabled = homeMarginKeys.any(::nonZero),
                bigFolderEnabled = bool(ConfigSchema.Grid.BIG_FOLDER_ENABLED),
                folderGridEnabled = bool(ConfigSchema.Grid.FOLDER_GRID_ENABLED),
                widget1x1Enabled = bool(ConfigSchema.Grid.WIDGET_1X1_TO_2X2_ENABLED),
                dockEnabled = bool(ConfigSchema.Dock.ENABLED),
                dividerEnabled = bool(ConfigSchema.Divider.ENABLED),
                recentsBlurEnabled = recentsBlur,
                workstationEnabled = bool(ConfigSchema.Workstation.DOCK_CUSTOMIZATION),
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
    val pendingRestartCount: Int get() = items.count { it.state == HookProbeState.PENDING_RESTART }
    val missingCount: Int get() = items.count { it.state == HookProbeState.MISSING }
}

private val PAD_BYPASS_REGEX = Regex("""OS4 big folder Pad bypass installed: patched=(\d+)""")
private val WIDGET_1X1_MENU_REGEX = Regex("""OS4 widget 1x1<->2x2 menu installed=(\d+)/(\d+)""")
private val BOOT_PID_REGEX = Regex("""native_init enter pid=(\d+)""")

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

private val WORKSTATION_HOOKS_REGEX = Regex("""workstation hooks installed=(\d+)/""")

private val HOME_MARGIN_RUNTIME_REGEX = Regex(
    """home margin monitor runtime=matched scope=(\d+) dir=(\d+) cols=(\d+) rows=(\d+)""",
)
private val HOME_MARGIN_STATUS_REGEX = Regex(
    """home margin monitor state=([a-z_]+) detail=([^\r\n]*)""",
)

internal fun evaluateHookMonitor(
    config: HookFeatureConfig,
    statusMap: Map<String, String>,
    bootLog: String,
    currentBootLog: String = bootLog,
): HookMonitorSnapshot {
    val statusPid = statusMap["pid"]?.toIntOrNull()
    val bootLogPid = BOOT_PID_REGEX.findAll(bootLog).lastOrNull()
        ?.groupValues?.getOrNull(1)?.toIntOrNull()
    val statusStale = statusPid != null && bootLogPid != null && statusPid != bootLogPid
    val pid = if (statusStale) bootLogPid else (statusPid ?: bootLogPid)

    val patchTotal = statusMap["patch_total"]?.toIntOrNull() ?: 0
    val patchIntact = statusMap["patch_intact"]?.toIntOrNull() ?: 0
    val pinFailed = statusMap["pin_failed"]?.toIntOrNull() ?: 0
    val patchDegraded = !statusStale && patchTotal > 0 && patchIntact < patchTotal
    val activeDetail = when {
        patchDegraded -> "代码页校验异常 ($patchIntact/$patchTotal)"
        !statusStale && patchTotal > 0 && patchIntact == patchTotal && pinFailed > 0 ->
            "已生效 ($patchIntact/$patchTotal，锁页失败 $pinFailed)"
        !statusStale && patchTotal > 0 && patchIntact == patchTotal ->
            "已生效 ($patchIntact/$patchTotal)"
        else -> "Hook 已生效"
    }

    fun resolve(
        enabled: Boolean,
        hooked: Boolean,
        stale: Boolean = statusStale,
        pendingRestart: Boolean = false,
    ): HookProbeState = when {
        !config.masterEnabled -> HookProbeState.PAUSED
        !enabled -> HookProbeState.DISABLED
        stale -> HookProbeState.MISSING
        hooked && (!patchDegraded || patchIntact > 0) -> HookProbeState.ACTIVE
        pendingRestart -> HookProbeState.PENDING_RESTART
        else -> HookProbeState.MISSING
    }

    fun probe(
        id: String,
        title: String,
        enabled: Boolean,
        hooked: Boolean,
        missingDetail: String = "偏移或 Hook 异常",
        activeDetailOverride: String? = null,
        staleDetailOverride: String? = null,
        staleOverride: Boolean? = null,
    ): HookProbeItem {
        val isStale = staleOverride ?: statusStale
        val bootDisabled = !isStale && statusMap["boot_$id"] == "0"
        val state = resolve(enabled, hooked, isStale, pendingRestart = bootDisabled)
        val detail = when (state) {
            HookProbeState.ACTIVE -> if (patchDegraded) activeDetail else (activeDetailOverride ?: "Hook 已生效")
            HookProbeState.PENDING_RESTART -> "新开启，重启桌面后生效"
            HookProbeState.DISABLED -> "开关未开启"
            HookProbeState.PAUSED -> "总开关已暂停"
            HookProbeState.MISSING -> if (isStale) {
                staleDetailOverride ?: "桌面重启后状态已过期，请重启桌面"
            } else missingDetail
        }
        return HookProbeItem(id = id, title = title, state = state, detail = detail)
    }

    val latestCurrentBootMarker = BOOT_PID_REGEX.findAll(currentBootLog).lastOrNull()
    val currentBootSection = latestCurrentBootMarker?.let { currentBootLog.substring(it.range.first) }
        ?: bootLog

    fun resolveHooked(key: String, fallbackLog: () -> Boolean): Boolean =
        if (!statusStale && statusMap.containsKey(key)) statusMap[key] == "1" else fallbackLog()

    val glassHooked = resolveHooked("glass") {
        currentBootSection.contains("OS4 glass pipeline installed") ||
            currentBootSection.contains("OS4 glass gates installed")
    }

    val iconGlassHooked = resolveHooked("glass_icons") {
        currentBootSection.contains("icon glass hooks installed")
    }

    val widgetGlassHooked = resolveHooked("glass_widgets") {
        currentBootSection.contains("OS4 RemoteViews component hook installed") &&
            currentBootSection.contains("OS4 RemoteViews bg hook installed")
    }

    val gridHooked = resolveHooked("grid") {
        currentBootSection.contains("complete layout pipeline ready=1")
    }

    val homeIconsHooked = resolveHooked("home_icons") {
        currentBootSection.contains("home icons applied:")
    }

    val currentMarginLog = latestCurrentBootMarker?.let { currentBootLog.substring(it.range.first) }
        ?: currentBootLog
    val currentLogHasMarginEvidence = HOME_MARGIN_STATUS_REGEX.containsMatchIn(currentMarginLog) ||
        HOME_MARGIN_RUNTIME_REGEX.containsMatchIn(currentMarginLog)
    val homeMarginStatusStale = statusStale && !currentLogHasMarginEvidence
    val homeMarginRuntime = HOME_MARGIN_RUNTIME_REGEX.findAll(currentMarginLog).lastOrNull()
    val homeMarginState = statusMap["home_margins_state"]?.toIntOrNull()
    val homeMarginLogState = HOME_MARGIN_STATUS_REGEX.findAll(currentMarginLog).lastOrNull()
        ?.groupValues?.getOrNull(1)
    val homeMarginHits = statusMap["home_margins_context_hits"]?.toIntOrNull() ?: 0
    val homeMarginRuntimeFailed = homeMarginLogState in setOf(
        "symbols_missing", "field_decode_failed", "target_rejected", "hook_failed",
        "runtime_object_mismatch", "runtime_dimensions_missing",
    ) || (homeMarginLogState == null && homeMarginState in setOf(1, 2, 5, 6, 7, 8))
    val homeMarginsHooked = when {
        homeMarginRuntimeFailed -> false
        homeMarginLogState == "grid_context_matched" -> true
        homeMarginRuntime != null -> true
        !homeMarginStatusStale && homeMarginState == 4 -> true
        homeMarginStatusStale -> false
        statusMap.containsKey("home_margins") ->
            statusMap["home_margins"] == "1" && (homeMarginState != 3 || homeMarginHits > 0)
        else -> bootLog.contains("home margin: currentConfig hooked")
    }
    val homeMarginsActiveDetail = homeMarginRuntime?.takeIf { !homeMarginRuntimeFailed }?.let { match ->
        val scope = match.groupValues[1].toIntOrNull()
        val dir = match.groupValues[2].toIntOrNull()
        val cols = match.groupValues[3]
        val rows = match.groupValues[4]
        val scopeText = if (scope == 2) "所有应用" else if (scope == 1) "工作台桌面" else "桌面"
        val dirText = if (dir == 1) "横屏" else "竖屏"
        "已匹配 $scopeText $dirText 网格（${cols}×${rows}）"
    } ?: if (homeMarginState == 4 || homeMarginHits > 0) "动态边距已命中网格" else null
    val homeMarginsMissingDetail = when (homeMarginLogState) {
        "symbols_missing" -> "currentConfig 目标未找到或签名不匹配"
        "field_decode_failed" -> "当前版本字段语义无法识别"
        "target_rejected" -> "目标地址或指令校验失败"
        "hook_failed" -> "边距 Hook 安装失败"
        "runtime_object_mismatch" -> "运行时 GridConfig 对象与已解析布局不匹配"
        "runtime_dimensions_missing" -> "没有取得当前方向或网格行列数"
        "hooks_installed_waiting_for_grid" -> "Hook 已安装，尚未匹配目标网格"
        "grid_context_matched" -> "边距 Hook 已命中，但当前运行状态尚未刷新"
        else -> when (homeMarginState) {
        1 -> "currentConfig 目标未找到或签名不匹配"
        2 -> "currentConfig Hook 安装失败"
        3 -> "Hook 已安装，尚未匹配目标网格"
        4 -> "已匹配网格，但运行状态文件未刷新"
        5 -> "当前版本字段语义无法识别，部分边距不可用"
        6 -> "运行时 GridConfig 对象与已解析布局不匹配"
        7 -> "没有取得当前方向或网格行列数"
        else -> "偏移或 Hook 异常"
        }
    }
    val homeMarginKnownFailure = homeMarginRuntimeFailed || homeMarginState in setOf(1, 2, 5, 6, 7, 8)
    val homeMarginStaleDetail = if (homeMarginKnownFailure) {
        "最近一次检测失败：$homeMarginsMissingDetail"
    } else null
    // The margin hook is installed even while every margin is zero so later preference
    // changes can take effect without rebuilding the hook. Do not let the UI's
    // "disabled" state hide a concrete failure reported by that always-installed hook.
    val homeMarginsShouldReport = config.homeMarginsEnabled || homeMarginKnownFailure

    val folderHooked = resolveHooked("big_folder") {
        val folderGateHooked = currentBootSection.contains("OS4 big folder gate installed:")
        val padBypassMatch = PAD_BYPASS_REGEX.findAll(currentBootSection).lastOrNull()
        val folderBypassPatched = padBypassMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        folderGateHooked || (folderBypassPatched != null && folderBypassPatched > 0)
    }

    val folderGridHooked = resolveHooked("folder_grid") {
        currentBootSection.contains("OS4 folder grid setup") &&
            (currentBootSection.contains("ready=1") || currentBootSection.contains("installed=7"))
    }

    val widget1x1Hooked = resolveHooked("widget_1x1") {
        val widget1x1Match = WIDGET_1X1_MENU_REGEX.findAll(currentBootSection).lastOrNull()
        val widget1x1Installed = widget1x1Match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val widget1x1Resize = currentBootSection.contains("widget resize: 1x1 enlarge enabled")
        widget1x1Installed > 0 || widget1x1Resize
    }

    val dockHooked = statusMap["dock"] == "1"

    val dividerHooked = resolveHooked("divider") {
        currentBootSection.contains("divider: inner installed") && currentBootSection.contains("divider: bgPaint installed")
    }

    val recentsHooked = resolveHooked("recents") {
        currentBootSection.contains("OS4 Recents blur gate installed:")
    }

    val wsHooksFromStatus = if (!statusStale) statusMap["workstation_hooks"]?.toIntOrNull() else null
    val wsMatch = WORKSTATION_HOOKS_REGEX.findAll(currentBootSection).lastOrNull()
    val wsInstalled = wsHooksFromStatus ?: (wsMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0)
    val workstationHooked = resolveHooked("workstation") {
        wsInstalled > 0
    }
    val wsDetail = if (wsInstalled > 0) "已生效 ($wsInstalled 个 Hook)" else null

    val widget1x1Match = Regex("""widget 1x1<->2x2 menu hooks=(\d+)""").findAll(currentBootSection).lastOrNull()
    val widget1x1Count = widget1x1Match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val widget1x1Detail = if (widget1x1Count > 0) "已生效 ($widget1x1Count 个 Hook)" else null

    val folderGridDetail = if (currentBootSection.contains("installed=7")) "已生效 (7 个 Hook)" else null

    val folderGridMissingDetail = when {
        statusMap["folder_cols_ready"] == "1" && statusMap["folder_rows_ready"] == "0" ->
            "列已就绪，行 Hook 异常"
        statusMap["folder_cols_ready"] == "0" && statusMap["folder_rows_ready"] == "1" ->
            "行已就绪，列 Hook 异常"
        else -> "偏移或 Hook 异常"
    }

    val items = listOf(
        probe("glass", "液态 / 柔光玻璃", config.glassEnabled, glassHooked),
        probe("glass_icons", "图标玻璃", config.iconGlassEnabled, iconGlassHooked),
        probe("glass_widgets", "小组件玻璃", config.widgetGlassEnabled, widgetGlassHooked),
        probe("grid", "桌面网格", config.gridEnabled, gridHooked),
        probe("home_icons", "图标大小", config.iconSizeEnabled, homeIconsHooked),
        probe(
            "home_margins", "桌面与所有应用边距", homeMarginsShouldReport, homeMarginsHooked,
            missingDetail = homeMarginsMissingDetail,
            activeDetailOverride = homeMarginsActiveDetail,
            staleDetailOverride = homeMarginStaleDetail,
            staleOverride = homeMarginStatusStale,
        ),
        probe("big_folder", "大文件夹", config.bigFolderEnabled, folderHooked),
        probe("folder_grid", "文件夹行列", config.folderGridEnabled, folderGridHooked, folderGridMissingDetail, folderGridDetail),
        probe("widget_1x1", "1×1↔2×2", config.widget1x1Enabled, widget1x1Hooked, activeDetailOverride = widget1x1Detail),
        probe("dock", "Dock 布局", config.dockEnabled, dockHooked),
        probe("divider", "分割线", config.dividerEnabled, dividerHooked),
        probe("recents", "背景模糊", config.recentsBlurEnabled, recentsHooked),
        probe("workstation", "工作台", config.workstationEnabled, workstationHooked, activeDetailOverride = wsDetail),
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

        val currentBootLog = runCatching {
            if (bootLogFile.isFile) bootLogFile.readText() else ""
        }.getOrDefault("")
        val bootLog = runCatching {
            val previous = if (bootLogPrevFile.isFile) bootLogPrevFile.readText() else ""
            val current = currentBootLog
            listOf(previous, current).filter(String::isNotBlank).joinToString("\n")
        }.getOrDefault("")
        return evaluateHookMonitor(config, statusMap, bootLog, currentBootLog)
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
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, prefs) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        }
    }

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
                        snap.missingCount == 0 && snap.pendingRestartCount == 0 ->
                            "Hook 全部就绪 (${snap.activeCount}/${snap.enabledCount})"
                        snap.missingCount == 0 ->
                            "Hook 已就绪 (${snap.activeCount}/${snap.enabledCount})，${snap.pendingRestartCount} 项需重启桌面生效"
                        snap.pendingRestartCount > 0 ->
                            "Hook 已生效 ${snap.activeCount}/${snap.enabledCount}，${snap.pendingRestartCount} 项待重启，${snap.missingCount} 项异常"
                        else ->
                            "Hook 已生效 ${snap.activeCount}/${snap.enabledCount}，${snap.missingCount} 项异常"
                    }
                    Text(
                        text = summaryText,
                        fontSize = 12.sp,
                        color = if (snap != null && (snap.missingCount > 0 || snap.pendingRestartCount > 0) && masterEnabled) {
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
        HookProbeState.PENDING_RESTART -> Color(0xFFD97706) to Color(0xFFD97706)
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
