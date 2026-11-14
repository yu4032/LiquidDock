package com.hellovoid.liquiddock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellovoid.liquiddock.config.ConfigSchema
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class LogSource(val label: String, val fileName: String?) {
    All("全部", null),
    Native("Native Hook", "liquiddock4-boot.log"),
    Java("Java 运行", "liquiddock.log"),
    Boots("启动保护", "liquiddock-boots.log"),
}

private object ModuleLogStore {
    private val mediaDir = File("/sdcard/Android/media/com.hellovoid.liquiddock")
    private const val MAX_PREVIEW_LINES = 300

    fun read(source: LogSource): String {
        val sources = if (source == LogSource.All) {
            listOf(LogSource.Native, LogSource.Java, LogSource.Boots)
        } else {
            listOf(source)
        }
        val sb = StringBuilder()
        for (item in sources) {
            val fileName = item.fileName ?: continue
            val file = File(mediaDir, fileName)
            val content = runCatching {
                if (file.isFile && file.canRead()) file.readText() else ""
            }.getOrDefault("")
            if (content.isNotBlank()) {
                if (sources.size > 1) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("===== ").append(item.label)
                        .append(" (").append(fileName).append(") =====\n")
                }
                sb.append(content.trimEnd()).append("\n")
            }
        }
        return sb.toString().trimEnd()
    }

    fun previewTail(fullText: String): String {
        if (fullText.isBlank()) return ""
        val lines = fullText.lines()
        if (lines.size <= MAX_PREVIEW_LINES) return fullText
        val omitted = lines.size - MAX_PREVIEW_LINES
        return "...（已省略前 $omitted 行，复制/导出将包含完整日志）...\n" +
            lines.takeLast(MAX_PREVIEW_LINES).joinToString("\n")
    }

    fun clear(source: LogSource) {
        val targets = if (source == LogSource.All) {
            listOf(LogSource.Native, LogSource.Java, LogSource.Boots)
        } else {
            listOf(source)
        }
        for (item in targets) {
            val fileName = item.fileName ?: continue
            runCatching {
                val file = File(mediaDir, fileName)
                if (file.exists()) file.writeText("")
            }
        }
    }
}

@Composable
internal fun ModuleLogsPage(
    padding: PaddingValues,
    activity: ComposeSettingsActivity,
    prefs: SharedPreferences,
) {
    val layoutDirection = LocalLayoutDirection.current
    var selectedSource by remember { mutableStateOf(LogSource.All) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var logText by remember { mutableStateOf("") }

    LaunchedEffect(selectedSource, refreshTick) {
        logText = ModuleLogStore.read(selectedSource)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val textToSave = ModuleLogStore.read(selectedSource)
        if (textToSave.isBlank()) {
            Toast.makeText(activity, "当前没有可导出的日志", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val ok = runCatching {
            activity.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(textToSave.toByteArray(Charsets.UTF_8))
            } != null
        }.getOrDefault(false)
        Toast.makeText(
            activity,
            if (ok) "日志已导出" else "导出失败",
            Toast.LENGTH_SHORT,
        ).show()
    }

    val previewText = remember(logText) { ModuleLogStore.previewTail(logText) }
    val listPadding = PaddingValues(
        start = padding.calculateStartPadding(layoutDirection),
        top = padding.calculateTopPadding(),
        end = padding.calculateEndPadding(layoutDirection),
        bottom = 12.dp,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = listPadding,
        ) {
            item { PageHeader("模块日志", "诊断开关、实时查看、复制与导出") }
            item { SmallTitle("运行诊断") }
            item {
                SettingsCard {
                    BooleanSetting(
                        prefs,
                        ConfigSchema.Debug.LOGGING,
                        "诊断日志",
                        "将 LiquidDock 运行诊断写入日志文件；修改后重启对应 Hook 作用域生效",
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "日志内容",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "刷新",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.clickable { refreshTick++ },
                        )
                        Text(
                            text = "清空",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.clickable {
                                ModuleLogStore.clear(selectedSource)
                                refreshTick++
                                Toast.makeText(activity, "已清空当前日志", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LogSource.entries.forEach { src ->
                        val active = src == selectedSource
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                )
                                .clickable { selectedSource = src }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = src.label,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (active) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    val vScroll = rememberScrollState()
                    val hScroll = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 380.dp)
                            .padding(14.dp)
                            .verticalScroll(vScroll)
                            .horizontalScroll(hScroll),
                    ) {
                        if (previewText.isBlank()) {
                            Text(
                                text = "暂无日志。\n请确认模块已激活并重启桌面，或开启上方「诊断日志」后复现操作再点击刷新。",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        } else {
                            SelectionContainer {
                                Text(
                                    text = previewText,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp + padding.calculateStartPadding(layoutDirection),
                    end = 16.dp + padding.calculateEndPadding(layoutDirection),
                    top = 8.dp,
                    bottom = 12.dp + padding.calculateBottomPadding(),
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    val latest = ModuleLogStore.read(selectedSource).ifBlank { logText }
                    if (latest.isBlank()) {
                        Toast.makeText(activity, "当前没有可复制的日志", Toast.LENGTH_SHORT).show()
                    } else {
                        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("LiquidDock Logs", latest))
                        Toast.makeText(activity, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                minHeight = 44.dp,
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("复制日志")
            }
            Spacer(Modifier.width(0.dp))
            Button(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                    exportLauncher.launch("liquiddock-logs-$stamp.txt")
                },
                modifier = Modifier.weight(1f),
                minHeight = 44.dp,
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("导出日志")
            }
        }
    }
}
