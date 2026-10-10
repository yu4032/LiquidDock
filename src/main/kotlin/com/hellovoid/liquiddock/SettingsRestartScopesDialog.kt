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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.styropyr0.prismal.components.PrismalGlassButton
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
    val backdrop = LocalPrismalSurfaceBackdrop.current
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
        val listMaxHeight = (maxHeight - 240.dp).coerceAtLeast(120.dp)
        ModernSurface(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .widthIn(max = 520.dp)
                .graphicsLayer { scaleX = panelScale; scaleY = panelScale }
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
                if (backdrop != null) {
                    PrismalGlassButton(
                        onClick = { if (selected.isNotEmpty()) onRestart(selected.toSet()) },
                        backdrop = backdrop,
                        modifier = Modifier.alpha(if (selected.isNotEmpty()) 1f else 0.38f),
                        isInteractive = selected.isNotEmpty(),
                        height = 42.dp,
                        blurRadius = 7.dp,
                        refractionHeight = 9.dp,
                        refractionAmount = 12.dp,
                        pressLift = 0.dp,
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 8.dp),
                        tint = Color(0xFFD73333),
                        tintAlpha = 0.92f,
                        depthEffect = false,
                        depthShadow = null,
                    ) {
                        Text(
                            text = "重启",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(21.dp))
                            .background(Color(0xFFD73333))
                            .alpha(if (selected.isNotEmpty()) 1f else 0.38f)
                            .clickable(enabled = selected.isNotEmpty()) {
                                onRestart(selected.toSet())
                            }
                            .padding(horizontal = 26.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
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

