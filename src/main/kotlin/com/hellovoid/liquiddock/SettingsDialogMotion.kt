package com.hellovoid.liquiddock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Shared spring-like entry and lightweight dismissal for MIUIX settings dialogs.
 * Keep the MIUIX window mounted until the exit transition finishes, otherwise
 * show=false removes the window immediately and the exit motion never runs.
 *
 * PrismalAGSL 1.0.4 provides glass spring controls but no window dialog primitive:
 * preserve MIUIX focus/IME semantics and the original content, rather than
 * creating another backdrop recording surface.
 */
@Composable
internal fun AnimatedSettingsWindowDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    LaunchedEffect(show) {
        visibility.targetState = show
    }
    val windowAttached = show || visibility.currentState || visibility.targetState
    WindowDialog(
        show = windowAttached,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(175)) +
                scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialScale = 0.93f,
                ) +
                slideInVertically(tween(210)) { it / 16 },
            exit = fadeOut(tween(150)) +
                scaleOut(tween(180), targetScale = 0.97f) +
                slideOutVertically(tween(180)) { it / 26 },
        ) {
            Column(content = content)
        }
    }
}
