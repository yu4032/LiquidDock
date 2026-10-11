package com.hellovoid.liquiddock

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One opaque color per theme and control kind. The GUI backdrop is fixed,
 * so no drawable inflation, gradient, specular/refraction pass, extra layout
 * child or offscreen buffer is justified for ordinary setting cells.
 */
internal enum class GuiSolidChromeStyle { CARD, PILL, DANGER }

private val cardShape = RoundedCornerShape(24.dp)
private val pillShape = RoundedCornerShape(999.dp)

internal fun guiSolidChromeColor(style: GuiSolidChromeStyle, darkTheme: Boolean): Color =
    when (style) {
        GuiSolidChromeStyle.CARD ->
            if (darkTheme) Color(0xFF343944) else Color(0xFFF0F2F5)
        GuiSolidChromeStyle.PILL ->
            if (darkTheme) Color(0xFF4C5260) else Color(0xFFE5E9EE)
        GuiSolidChromeStyle.DANGER -> Color(0xFFD73333)
    }

internal fun Modifier.guiSolidChrome(
    style: GuiSolidChromeStyle,
    darkTheme: Boolean,
): Modifier = background(
    guiSolidChromeColor(style, darkTheme),
    if (style == GuiSolidChromeStyle.CARD) cardShape else pillShape,
)
