package com.hellovoid.liquiddock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.depth.PrismalDepthShadow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Existing restart-scope dialog and action behavior unchanged. This file does
// not own navigation, backdrop capture, or the restart process implementation.
internal data class RestartScopeItem(
    val id: String,
    val title: String,
    val packageName: String,
)

@Composable
internal fun RestartScopesDialog(
    visible: Boolean,
    items: List<RestartScopeItem>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onRestart: (Set<String>) -> Unit,
) {
    // Restart dialog is body chrome. It must not sample the header/footer
    // backdrop or install Prismal's per-control gesture/shader pipeline.
    // The scrim fades without changing backdrop ownership; only the panel
    // receives the spring scale (never scale the complete fullscreen scrim).
    val panelScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "restart-panel-scale",
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(190)),
        exit = fadeOut(tween(170)),
    ) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // The modal's scaled layer must include the full Prismal halo,
        // otherwise its rectangular offscreen layer chops off the shadow.
        val opticalGutter = PrismalDepthShadow.Default.radius * 2f
        val listMaxHeight = (maxHeight - 240.dp).coerceAtLeast(120.dp)
        ModernSurface(
            modifier = Modifier
                // Include the optical gutter in the transformed layer bounds:
                // Prismal's native depth shadow draws outside the card outline.
                .graphicsLayer { scaleX = panelScale; scaleY = panelScale }
                .padding(horizontal = opticalGutter, vertical = opticalGutter)
                .widthIn(max = 520.dp)
                // Consume taps on the dialog panel without installing an
                // interactive Prismal surface over its child switches/buttons.
                .clickable(onClick = {}),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Text(
                text = "重启作用域",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "选择需要重新加载 Hook 的进程作用域。",
                modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = listMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                items.forEachIndexed { index, item ->
                    // Prismal is visual only: the current selected set is
                    // authoritative for both the switch and the row action.
                    val checked = item.id in selected
                    BasicComponent(
                        title = item.title,
                        summary = item.packageName,
                        endActions = {
                            RestartScopeToggle(
                                scopeId = item.id,
                                checked = checked,
                                backdrop = LocalTouchPrismalBackdrop.current,
                                onToggle = onToggle,
                            )
                        },
                        onClick = { onToggle(item.id, !checked) },
                    )
                    if (index != items.lastIndex) {
                        ModernListDivider()
                    }
                }
            }

            Text(
                text = "System Framework（system）需要重启设备，因此不在此列表。",
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.52f),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = { onRestart(selected.toSet()) },
                    enabled = selected.isNotEmpty(),
                    minHeight = 42.dp,
                    minWidth = 100.dp,
                    insideMargin = PaddingValues(horizontal = 26.dp, vertical = 8.dp),
                    destructive = true,
                ) {
                    Text(
                        text = "重启",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
    }
}

/**
 * The restart dialog retains native Prismal toggle optics when glass is on.
 * The spring gesture in PrismalAGSL remembers its original closures, so bridge
 * both state and callbacks to their latest values instead of capturing the
 * checkbox defaults from when the dialog first appeared.
 */
@Composable
private fun RestartScopeToggle(
    scopeId: String,
    checked: Boolean,
    backdrop: PrismalBackdrop?,
    onToggle: (String, Boolean) -> Unit,
) {
    val currentId = rememberUpdatedState(scopeId)
    val currentChecked = rememberUpdatedState(checked)
    val currentToggle = rememberUpdatedState(onToggle)
    val selectedProvider = remember { { currentChecked.value } }
    val stableToggle: (Boolean) -> Unit = remember {
        { next -> currentToggle.value(currentId.value, next) }
    }

    if (backdrop != null) {
        GuiOnTouchPrismalToggle(
            selected = selectedProvider,
            onSelect = stableToggle,
            backdrop = backdrop,
        )
    } else {
        top.yukonga.miuix.kmp.basic.Switch(
            checked = checked,
            onCheckedChange = stableToggle,
        )
    }
}

