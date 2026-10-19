package com.hellovoid.liquiddock

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styropyr0.prismal.shapes.PrismalRoundedRectangle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * Offline Prismal capsule surface: fixed theme fill and continuous curvature,
 * pre-rasterized normal-lit white rim and soft shadow. The 3-slice PNG only
 * stretches its straight 8dp center band; the end caps retain their geometry.
 * No live Prismal AGSL, RenderEffect, blur, or background capture.
 */
@Composable
internal fun BakedPrismalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focus: FocusRequester,
) {
    val palette = LocalGuiPresetChromePalette.current
    val context = LocalContext.current
    val atlas = remember(context) {
        try {
            ImageBitmap.imageResource(context.resources, R.drawable.gui_search_prismal_chrome)
        } catch (error: RuntimeException) {
            Log.w("SettingsSearch", "Missing baked Prismal search chrome", error)
            null
        }
    }
    val shape = remember { PrismalRoundedRectangle(24.dp) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(60.dp)
            .guiBakedPrismalSearchChrome(atlas),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .height(48.dp)
                .background(palette.pillFill, shape)
                .then(if (atlas == null) Modifier.guiStaticSpecularRim(shape, palette) else Modifier)
                .clip(shape),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 17.sp, color = MiuixTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "搜索设置页面",
                                fontSize = 17.sp,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .48f),
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

private fun Modifier.guiBakedPrismalSearchChrome(atlas: ImageBitmap?): Modifier {
    if (atlas == null) return this
    return drawWithCache {
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        val fixedEnd = 54.dp.toPx().roundToInt()
        onDrawWithContent {
            drawContent()
            if (width <= 0 || height <= 0) return@onDrawWithContent
            if (width <= 2 * fixedEnd) {
                drawImage(
                    image = atlas,
                    srcSize = IntSize(348, 180),
                    dstSize = IntSize(width, height),
                    filterQuality = FilterQuality.Medium,
                )
            } else {
                val central = width - 2 * fixedEnd
                drawImage(
                    image = atlas,
                    srcOffset = IntOffset(0, 0),
                    srcSize = IntSize(162, 180),
                    dstSize = IntSize(fixedEnd, height),
                    filterQuality = FilterQuality.Medium,
                )
                drawImage(
                    image = atlas,
                    srcOffset = IntOffset(162, 0),
                    srcSize = IntSize(24, 180),
                    dstOffset = IntOffset(fixedEnd, 0),
                    dstSize = IntSize(central, height),
                    filterQuality = FilterQuality.Medium,
                )
                drawImage(
                    image = atlas,
                    srcOffset = IntOffset(186, 0),
                    srcSize = IntSize(162, 180),
                    dstOffset = IntOffset(width - fixedEnd, 0),
                    dstSize = IntSize(fixedEnd, height),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}
