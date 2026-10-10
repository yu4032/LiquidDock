package com.hellovoid.liquiddock

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Window-safe spring motion for the settings dialogs.
 *
 * PrismalAGSL 1.0.4 does not expose a modal/window component. Preserve MIUIX
 * WindowDialog for focus, IME and outside-click behavior; animate its content
 * with Prismal-like spring easing, without adding a new backdrop recorder.
 *
 * Important: the content stays measured at its full size while fading/scaling.
 * AnimatedVisibility would resize the MIUIX dialog while it opens or closes.
 * Keep the dialog window attached until its exit transition has fully settled.
 */
@Composable
internal fun AnimatedSettingsWindowDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val transition = updateTransition(targetState = show, label = "settings-modal-visibility")
    val opacity by transition.animateFloat(
        transitionSpec = { tween(if (targetState) 195 else 155) },
        label = "settings-modal-opacity",
    ) { visible -> if (visible) 1f else 0f }
    val panelScale by transition.animateFloat(
        transitionSpec = {
            if (targetState) spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ) else tween(170)
        },
        label = "settings-modal-scale",
    ) { visible -> if (visible) 1f else 0.94f }
    val panelOffset by transition.animateFloat(
        transitionSpec = { tween(if (targetState) 220 else 165) },
        label = "settings-modal-rise",
    ) { visible -> if (visible) 0f else 16f }

    WindowDialog(
        show = show || transition.currentState || transition.isRunning,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.graphicsLayer {
                alpha = opacity
                scaleX = panelScale
                scaleY = panelScale
                translationY = panelOffset
            },
            content = content,
        )
    }
}
