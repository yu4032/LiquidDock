package com.hellovoid.liquiddock

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * All fixed settings backgrounds share four precomposed resources (card/pill × light/dark).
 *
 * No PrismalBackdrop, Shader, GraphicsLayer, RenderEffect or per-instance refraction map is
 * created for the body surface. XML GradientDrawables are inflated only when an instance is
 * composed; the same constant resource definitions are used throughout the entire settings UI.
 * A separate drawable instance avoids mutable draw bounds leaking between concurrently drawn
 * Compose nodes. Animated controls and contents remain outside this static background.
 */
internal enum class GuiStaticGlassStyle { CARD, PILL }

@Composable
internal fun GuiStaticGlassChrome(
    modifier: Modifier = Modifier,
    style: GuiStaticGlassStyle,
    darkTheme: Boolean,
) {
    val context = LocalContext.current
    val resourceId = when (style) {
        GuiStaticGlassStyle.CARD -> if (darkTheme) {
            R.drawable.gui_static_glass_card_dark
        } else {
            R.drawable.gui_static_glass_card_light
        }
        GuiStaticGlassStyle.PILL -> if (darkTheme) {
            R.drawable.gui_static_glass_pill_dark
        } else {
            R.drawable.gui_static_glass_pill_light
        }
    }
    val drawable = remember(context, resourceId) {
        requireNotNull(ContextCompat.getDrawable(context, resourceId)) {
            "Missing static GUI glass resource"
        }.mutate()
    }
    Box(
        modifier = modifier.drawBehind {
            // A direct draw to the parent Canvas: no offscreen texture, no captured backdrop,
            // and no per-cell lighting/refraction calculation while lists scroll.
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                val checkpoint = nativeCanvas.save()
                drawable.setBounds(0, 0,
                    size.width.roundToInt().coerceAtLeast(0),
                    size.height.roundToInt().coerceAtLeast(0))
                drawable.draw(nativeCanvas)
                nativeCanvas.restoreToCount(checkpoint)
            }
        },
    )
}
