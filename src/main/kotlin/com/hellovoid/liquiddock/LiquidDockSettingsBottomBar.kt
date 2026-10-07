package com.hellovoid.liquiddock

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
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

private val BottomBarHighlight = Highlight(
    width = 1.dp,
    alpha = 0.78f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.16f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(
            position = LightPosition(0.2f, -0.25f, -0.1f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.85f, 1.05f, -0.35f),
            color = Color.White,
            intensity = 0.42f,
        ),
        dualPeak = true,
    ),
)

private val SelectionHighlight = Highlight(
    width = 1.dp,
    alpha = 0.92f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.22f),
        innerBlurRadius = 1.5.dp,
        primaryLight = LightSource(
            position = LightPosition(0.15f, -0.35f, -0.1f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.9f, 0.9f, -0.25f),
            color = Color.White,
            intensity = 0.5f,
        ),
        dualPeak = true,
    ),
)

@Composable
internal fun LiquidDockSettingsBottomBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: LayerBackdrop,
) {
    val shape = RoundedCornerShape(32.dp)
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val glassTint = if (dark) {
        surface.copy(alpha = 0.34f)
    } else {
        surface.copy(alpha = 0.46f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        padding = maxOf(padding, 28.dp.toPx())
                        colorControls(
                            brightness = if (dark) 0.015f else 0f,
                            contrast = 1.035f,
                            saturation = 1.32f,
                        )
                        blur(5.dp.toPx(), 5.dp.toPx())
                    },
                    highlight = { BottomBarHighlight },
                    onDrawSurface = {
                        drawRect(glassTint)
                        drawRect(Color.White.copy(alpha = if (dark) 0.025f else 0.045f))
                    },
                )
                .selectableGroup()
                .padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            settingsBottomItems.forEachIndexed { index, item ->
                SettingsBottomBarItem(
                    item = item,
                    selected = selectedIndex == index,
                    backdrop = backdrop,
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
    backdrop: LayerBackdrop,
    onClick: () -> Unit,
) {
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val accentColor = MiuixTheme.colorScheme.primary
    val contentColor = if (selected) {
        accentColor
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    }
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "bottom-tab-scale",
    )
    val selectionShape = RoundedCornerShape(24.dp)
    val selectedModifier = if (selected) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { selectionShape },
            effects = {
                padding = maxOf(padding, 16.dp.toPx())
                colorControls(
                    brightness = if (dark) 0.02f else 0f,
                    contrast = 1.06f,
                    saturation = 1.48f,
                )
                blur(2.5.dp.toPx(), 2.5.dp.toPx())
            },
            highlight = { SelectionHighlight },
            onDrawSurface = {
                drawRect(
                    accentColor.copy(
                        alpha = if (dark) 0.12f else 0.09f,
                    ),
                )
                drawRect(Color.White.copy(alpha = if (dark) 0.035f else 0.055f))
            },
        )
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 2.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(selectedModifier)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = stringResource(item.labelRes),
            tint = contentColor,
            modifier = Modifier.size(if (selected) 23.dp else 21.dp),
        )
        Text(
            text = stringResource(item.labelRes),
            color = contentColor,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
