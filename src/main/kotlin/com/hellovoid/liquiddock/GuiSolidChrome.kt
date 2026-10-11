package com.hellovoid.liquiddock

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * The GUI backdrop is fixed; bake the FINAL composite of the background,
 * saturated Plus-blend Prismal Default specular, and shape edge into a 9-patch.
 *
 * The source default_specular.agsl yields a normal-dependent WHITE source,
 * but PrismalSpecularModifier draws that source with BlendMode.Plus and clips
 * it to a continuous rounded rectangle. A plain white SRC_OVER bitmap cannot
 * reproduce the result over our near-white palette: Plus saturates thin lines
 * all the way to white. The 9-patch therefore stores the actual composited
 * opaque RGB result, not a standalone alpha map or a fake white outline.
 *
 * Upstream: PrismalAGSL v1.0.4 PrismalSpecularModifier.kt and
 * default_specular.agsl. Assets built offline from the fixed light/dark canvas.
 */
internal data class GuiPresetChromePalette(
    val canvas: Color,
    val canvasWithoutGlass: Color,
    val cardMaterial: Drawable,
    val pillMaterial: Drawable,
    val thumbMaterial: Drawable,
    val dangerMaterial: Drawable,
)

internal val LocalGuiPresetChromePalette = staticCompositionLocalOf<GuiPresetChromePalette> {
    error("GUI preset material requires ModernSettingsScaffold")
}

@Composable
internal fun rememberGuiPresetChromePalette(): GuiPresetChromePalette {
    val context = LocalContext.current
    val canvas = colorResource(R.color.gui_preset_canvas)
    val canvasWithoutGlass = colorResource(R.color.gui_preset_canvas_glass_off)

    // Qualifiers select light/dark images. Drawables are loaded exactly once at
    // the scaffold; each Cell changes bounds and draws the shared immutable pixels.
    val cardMaterial = remember(context, canvas) {
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.gui_material_card)).mutate()
    }
    val pillMaterial = remember(context, canvas) {
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.gui_material_pill)).mutate()
    }
    val thumbMaterial = remember(context, canvas) {
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.gui_material_thumb)).mutate()
    }
    val dangerMaterial = remember(context, canvas) {
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.gui_material_danger)).mutate()
    }
    return remember(
        canvas, canvasWithoutGlass, cardMaterial, pillMaterial,
        thumbMaterial, dangerMaterial,
    ) {
        GuiPresetChromePalette(
            canvas, canvasWithoutGlass, cardMaterial, pillMaterial,
            thumbMaterial, dangerMaterial,
        )
    }
}

internal enum class GuiSolidChromeStyle { CARD, PILL, THUMB, DANGER }

/**
 * Single precomposited material draw behind the Cell CONTENT. It replaces BOTH
 * the solid background() and the specular drawWithContent overlay. In particular
 * high-intensity edges must never be drawn over labels or interactive controls.
 *
 * No Prismal shader, capture, GraphicsLayer, runtime lighting, blur, per-Cell
 * painter allocation, or second composable. 9-patch preserves corner pixels.
 */
internal fun Modifier.guiSolidChrome(
    style: GuiSolidChromeStyle,
    palette: GuiPresetChromePalette,
): Modifier {
    val material = when (style) {
        GuiSolidChromeStyle.CARD -> palette.cardMaterial
        GuiSolidChromeStyle.PILL -> palette.pillMaterial
        GuiSolidChromeStyle.THUMB -> palette.thumbMaterial
        GuiSolidChromeStyle.DANGER -> palette.dangerMaterial
    }
    return this.drawBehind {
        drawIntoCanvas { canvas ->
            val width = size.width.roundToInt()
            val height = size.height.roundToInt()
            if (width > 0 && height > 0) {
                val native = canvas.nativeCanvas
                val checkpoint = native.save()
                try {
                    material.setBounds(0, 0, width, height)
                    material.draw(native)
                } finally {
                    native.restoreToCount(checkpoint)
                }
            }
        }
    }
}
