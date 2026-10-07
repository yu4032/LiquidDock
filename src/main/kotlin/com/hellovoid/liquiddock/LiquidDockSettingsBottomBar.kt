package com.hellovoid.liquiddock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.components.PrismalGlassBottomTab
import com.styropyr0.prismal.components.PrismalGlassBottomTabs
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class SettingsBottomItem(
    val labelRes: Int,
    val icon: ImageVector,
)

private val settingsBottomItems = listOf(
    SettingsBottomItem(R.string.tab_overview, MiuixIcons.Tune),
    SettingsBottomItem(R.string.tab_layout, MiuixIcons.GridView),
    SettingsBottomItem(R.string.tab_glass, MiuixIcons.Image),
    SettingsBottomItem(R.string.tab_more, MiuixIcons.Settings),
)

@Composable
internal fun LiquidDockSettingsBottomBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: PrismalBackdrop,
) {
    val accent = MiuixTheme.colorScheme.primary
    val baseContent = MiuixTheme.colorScheme.onSurfaceVariantActions

    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        PrismalGlassBottomTabs(
            selectedTabIndex = { selectedIndex },
            onTabSelected = onSelected,
            backdrop = backdrop,
            tabsCount = settingsBottomItems.size,
            modifier = Modifier
                .widthIn(max = 520.dp),
            tintDropletContent = true,
            dropletContentTint = accent,
        ) {
            settingsBottomItems.forEachIndexed { index, item ->
                PrismalGlassBottomTab(
                    onClick = { onSelected(index) },
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(item.labelRes),
                        tint = baseContent,
                        modifier = Modifier.size(21.dp),
                    )
                    Text(
                        text = stringResource(item.labelRes),
                        color = baseContent,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
