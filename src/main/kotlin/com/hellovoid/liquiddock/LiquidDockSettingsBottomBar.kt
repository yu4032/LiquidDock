package com.hellovoid.liquiddock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class SettingsBottomItem(
    val labelRes: Int,
    val iconRes: Int,
)

private val settingsBottomItems = listOf(
    SettingsBottomItem(R.string.tab_overview, R.drawable.ic_settings_overview),
    SettingsBottomItem(R.string.tab_layout, R.drawable.ic_settings_layout),
    SettingsBottomItem(R.string.tab_glass, R.drawable.ic_settings_glass),
    SettingsBottomItem(R.string.tab_more, R.drawable.ic_settings_more),
)

@Composable
internal fun LiquidDockSettingsBottomBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: LayerBackdrop,
) {
    val shape = RoundedCornerShape(34.dp)
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val isLight = MiuixTheme.colorScheme.surface.luminance() > 0.5f
    val highlight = remember(isLight) {
        if (isLight) Highlight.GlassStrokeMiddleLight else Highlight.GlassStrokeMiddleDark
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .textureBlur(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = 25f,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(
                            BlendColorEntry(color = surface.copy(alpha = 0.58f)),
                        ),
                    ),
                    highlight = highlight,
                )
                .background(surface.copy(alpha = 0.16f), shape)
                .selectableGroup()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            settingsBottomItems.forEachIndexed { index, item ->
                SettingsBottomBarItem(
                    item = item,
                    selected = selectedIndex == index,
                    onClick = { onSelected(index) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.SettingsBottomBarItem(
    item: SettingsBottomItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    }
    val indicatorColor = if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MiuixTheme.colorScheme.surface.copy(alpha = 0f)
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .background(indicatorColor, CircleShape)
                .padding(horizontal = 15.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = stringResource(item.labelRes),
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = stringResource(item.labelRes),
            color = contentColor,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
