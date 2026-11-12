package com.hellovoid.liquiddock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.delay

/**
 * 崩溃安全模式的 app 侧开关。
 *
 * 不依赖 root：标记放在模块自己的 external files 目录，launcher 侧（有
 * MANAGE_EXTERNAL_STORAGE）负责写入；退出时这里删标记并写 .exit，
 * launcher 下次启动读到 .exit 即清掉内部标记、恢复所有 hook。
 */
object Os4SafeMode {
    /** 模块自己的 Android/media 目录：launcher（有 MANAGE_EXTERNAL_STORAGE）可写，
     *  本 app 无需任何权限即可读；Android/data 是跨应用禁区，不能用。 */
    private const val DIR = "/sdcard/Android/media/com.hellovoid.liquiddock"
    private val flag = File(DIR, "safe_mode.flag")
    private val exitMark = File(DIR, "safe_mode.exit")

    fun isActive(): Boolean = runCatching {
        flag.exists() || File("/sdcard/Android/data/com.hellovoid.liquiddock/files/safe_mode.flag").exists()
    }.getOrDefault(false)

    /** 标记退出；重启桌面后生效。 */
    fun exit(): Boolean = runCatching {
        val dir = File(DIR)
        if (!dir.exists()) dir.mkdirs()
        exitMark.writeText("1")
        flag.delete()
        true
    }.getOrDefault(false)
}

/** 每 2 秒轮询一次安全模式状态，供各页面 header 下方的横幅使用。 */
@Composable
fun rememberSafeModeActive(): Boolean {
    var active by remember { mutableStateOf(Os4SafeMode.isActive()) }
    LaunchedEffect(Unit) {
        while (true) {
            active = Os4SafeMode.isActive()
            delay(2000)
        }
    }
    return active
}

private val BannerBg = Color(0xFF8C1D18)
private val BannerFg = Color(0xFFFFFFFF)

/** 常驻横幅：只在安全模式出现，点按钮退出。 */
@Composable
fun SafeModeBanner(onExit: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BannerBg)
            .padding(start = 16.dp, end = 72.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = "安全模式",
                style = TextStyle(color = BannerFg, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            BasicText(
                text = "桌面连续崩溃，模块功能已全部停用。退出后需重启桌面生效。",
                style = TextStyle(color = BannerFg, fontSize = 12.sp),
            )
        }
        BasicText(
            text = "退出安全模式",
            modifier = Modifier
                .background(Color(0xFFFFFFFF), RoundedCornerShape(8.dp))
                .clickable { onExit() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = TextStyle(color = BannerBg, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}
