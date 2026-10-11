package com.hellovoid.liquiddock

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Solid-color replacement for a PrismalGlassStepper button.
 *
 * Maintains press feedback, tap-to-step, range-aware disabled state and
 * repeat-on-hold without instantiating an optical surface or backdrop sampler.
 */
@Composable
internal fun GuiStaticRepeatStepButton(
    label: String,
    enabled: Boolean,
    staticChrome: Boolean,
    onStep: () -> Unit,
) {
    val callback = rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme
    val presetChrome = LocalGuiPresetChromePalette.current
    val darkTheme = colors.background.luminance() < 0.5f
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (staticChrome) Modifier.guiNativePrismalDepthShadow(shape) else Modifier)
            .then(
                if (staticChrome) {
                    Modifier.guiExactPrismalCornerChrome(GuiSolidChromeStyle.PILL, presetChrome, shape)
                } else {
                    Modifier.background(
                        lerp(colors.surface, colors.onSurface, if (darkTheme) 0.12f else 0.045f),
                        shape,
                    )
                }
            )
            .clip(shape)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            try {
                                coroutineScope {
                                    var repeated = false
                                    val repeat = launch {
                                        delay(430L)
                                        while (isActive) {
                                            repeated = true
                                            callback.value()
                                            delay(90L)
                                        }
                                    }
                                    try {
                                        val released = tryAwaitRelease()
                                        if (released && !repeated) callback.value()
                                    } finally {
                                        repeat.cancel()
                                    }
                                }
                            } finally {
                                pressed = false
                            }
                        },
                    )
                }
            }
            .semantics {
                role = Role.Button
                if (enabled) {
                    onClick {
                        callback.value()
                        true
                    }
                } else {
                    disabled()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (pressed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Color.White.copy(alpha = if (darkTheme) 0.13f else 0.18f),
                        shape,
                    ),
            )
        }
        Text(label, color = colors.onSurface)
    }
}
