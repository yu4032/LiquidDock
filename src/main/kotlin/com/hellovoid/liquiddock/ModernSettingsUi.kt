package com.hellovoid.liquiddock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.PrismalGlassSurface
import com.styropyr0.prismal.components.PrismalGlassButton
import com.styropyr0.prismal.components.PrismalGlassStepper
import com.styropyr0.prismal.shapes.PrismalRoundedRectangle
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalMergedSource
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val ModernPreferenceMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
internal const val SETTINGS_UI_PREFS = "liquiddock_settings_ui"
internal const val SETTINGS_UI_GLASS_ENABLED = "glass_effect_enabled"

// Flat header blur uses the same Prismal source as the bottom navigation.
// Ordinary Compose text in the page body is captured by prismalGlassLayer.
private const val TOP_BAR_BLUR_RADIUS = 14f
private const val TOP_BAR_GLASS_TINT_ALPHA = 0.34f
private val TOP_BAR_ACTION_SHADOW_ROOM = 10.dp
private const val TOP_BAR_BOTTOM_STROKE_ALPHA = 0.10f

internal val LocalPrismalSurfaceBackdrop = staticCompositionLocalOf<PrismalBackdrop?> { null }
internal val LocalPrismalOverlayBackdrop = staticCompositionLocalOf<PrismalBackdrop?> { null }
internal val LocalTouchPrismalBackdrop = staticCompositionLocalOf<PrismalBackdrop?> { null }
private val LocalFrozenPrismalBackdrop = staticCompositionLocalOf<PrismalBackdrop?> { null }
// Cheap, static approximation of the existing Prismal cells. This flag never
// gives a body component access to the live header/footer capture layers.
private val LocalStaticGlassChrome = staticCompositionLocalOf { false }

@Composable
internal fun ModernSettingsScaffold(
    title: String,
    glassEnabled: Boolean = true,
    showBack: Boolean,
    backLabel: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    overlay: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val backgroundLayer = rememberPrismalGlassLayer()
    val screenLayer = rememberPrismalGlassLayer()
    val overlayBackdrop = rememberPrismalMergedSource(backgroundLayer, screenLayer)
    val background = MiuixTheme.colorScheme.background
    val primary = MiuixTheme.colorScheme.primary
    val surface = MiuixTheme.colorScheme.surface
    // PrismalGlassSurface's tint applies BlendMode.Hue before its alpha overlay.
    // A neutral surface fill avoids recoloring green/other content behind the bar.
    val headerNeutralColor = if (surface.luminance() < 0.5f) Color.Black else Color.White
    // Keep continuous Prismal sampling for top bar actions/header and bottom
    // capsule. Body cells use frozen chrome, while numeric stepper/reset retain
    // native Prismal and sliders/toggles sample only during touch.
    val surfaceBackdrop: PrismalBackdrop? = null
    // Touch-gated controls use the original background sample, but their
    // render nodes only attach during active gestures. No third capture layer.
    val touchBackdrop = if (glassEnabled) backgroundLayer else null
    // Real native Prismal material is rendered once and cached per body cell;
    // the top/footer still use the original live merged sources.
    val frozenBackdrop = if (glassEnabled) backgroundLayer else null
    val activeOverlayBackdrop = if (glassEnabled) overlayBackdrop else null
    // Header and bottom navigation share the captured background + content.
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The capture modifier must wrap background() so the sampled
                // backdrop is opaque and covers unblurred content underneath.
                .then(
                    if (glassEnabled) Modifier.prismalGlassLayer(backgroundLayer)
                    else Modifier,
                )
                .then(
                    if (glassEnabled) {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(
                                    background,
                                    // Keep the captured gradient opaque.
                                    lerp(background, primary, 0.07f),
                                    background,
                                ),
                            ),
                        )
                    } else {
                        Modifier.background(lerp(background, Color.Black, 0.06f))
                    },
                ),
        )

        CompositionLocalProvider(
            LocalPrismalSurfaceBackdrop provides surfaceBackdrop,
            LocalPrismalOverlayBackdrop provides activeOverlayBackdrop,
            LocalTouchPrismalBackdrop provides touchBackdrop,
            LocalFrozenPrismalBackdrop provides frozenBackdrop,
            LocalStaticGlassChrome provides glassEnabled,
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    // MIUIX lays out the bar; Prismal draws its glass.
                    val headerContent: @Composable BoxScope.() -> Unit = {
                        GuiUnclippedSmallTopAppBar(
                            title = title,
                            navigationIcon = {
                                if (showBack) {
                                    Box(
                                        modifier = Modifier.size(52.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (glassEnabled) {
                                            PrismalGlassButton(
                                                onClick = onBack,
                                                backdrop = overlayBackdrop,
                                                modifier = Modifier.size(40.dp),
                                                height = 40.dp,
                                                blurRadius = 7.dp,
                                                refractionHeight = 9.dp,
                                                refractionAmount = 12.dp,
                                                pressLift = 2.dp,
                                                contentPadding = PaddingValues(9.dp),
                                                tint = Color.Unspecified,
                                                surfaceColor = headerNeutralColor.copy(alpha = 0.20f),
                                                useVibrancy = false,
                                                saturation = 1f,
                                                depthEffect = false,
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Back,
                                                    contentDescription = backLabel,
                                                    tint = MiuixTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .clickable(onClick = onBack),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Back,
                                                    contentDescription = backLabel,
                                                    tint = MiuixTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            actions = actions,
                            bottomContent = {
                                Spacer(Modifier.height(TOP_BAR_ACTION_SHADOW_ROOM))
                            },
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    MiuixTheme.colorScheme.onSurface.copy(
                                        alpha = TOP_BAR_BOTTOM_STROKE_ALPHA,
                                    ),
                                ),
                        )
                    }
                    if (activeOverlayBackdrop != null) {
                        GuiPrismalFlatHeader(
                            backdrop = activeOverlayBackdrop,
                            modifier = Modifier.fillMaxWidth().zIndex(1f),
                            blurRadius = TOP_BAR_BLUR_RADIUS.dp,
                            overlayColor = headerNeutralColor.copy(alpha = TOP_BAR_GLASS_TINT_ALPHA),
                            statusBarEdgeColor = if (background.luminance() < 0.5f) Color.Black else Color.White,
                            content = headerContent,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().zIndex(1f).background(surface),
                            content = headerContent,
                        )
                    }
                },
                bottomBar = bottomBar,
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f)
                        .then(
                            if (glassEnabled) Modifier.prismalGlassLayer(screenLayer)
                            else Modifier,
                        ),
                ) {
                    content(padding)
                }
            }
            overlay()
        }
    }
}

// Restart scopes live in SettingsRestartScopesDialog.kt; bottom navigation
// lives in SettingsBottomNavigation.kt with its single-pass glyph policy.

@Composable
internal fun ModernTopActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalPrismalOverlayBackdrop.current ?: LocalPrismalSurfaceBackdrop.current
    val actionSurface = MiuixTheme.colorScheme.surface
    val neutralActionTint = if (actionSurface.luminance() < 0.5f) Color.Black else Color.White
    if (backdrop == null) {
        top.yukonga.miuix.kmp.basic.Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            minHeight = 34.dp,
            minWidth = 0.dp,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
        ) { Text(text, fontSize = 13.sp, maxLines = 1) }
        return
    }

    PrismalGlassButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        modifier = modifier.alpha(if (enabled) 1f else 0.42f),
        isInteractive = enabled,
        height = 36.dp,
        blurRadius = 7.dp,
        refractionHeight = 9.dp,
        refractionAmount = 12.dp,
        pressLift = 2.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        // Prismal tint uses BlendMode.Hue; never let app-bar actions recolor
        // underlying green content into the theme's purple.
        tint = Color.Unspecified,
        surfaceColor = neutralActionTint.copy(alpha = 0.20f),
        useVibrancy = false,
        saturation = 1f,
        depthEffect = false,
    ) {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ModernSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    staticPress: Boolean = false,
    // Static list cells can skip the costly edge lens while retaining native
    // Prismal blur, tint, specular, outline, and press feedback.
    edgeRefraction: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalPrismalSurfaceBackdrop.current
    val frozenBackdrop = LocalFrozenPrismalBackdrop.current
    val staticChrome = LocalStaticGlassChrome.current
    val colors = MiuixTheme.colorScheme
    val darkTheme = colors.background.luminance() < 0.5f
    val cardShape = RoundedCornerShape(24.dp)
    // Match the Prismal neutral wash/rim with static Compose gradients.
    // No RenderEffect, backdrop evaluation or per-cell offscreen shader.
    val solidCardColor = if (darkTheme) lerp(colors.surface, colors.onSurface, 0.08f) else colors.surface
    val cardStroke = colors.onSurface.copy(alpha = if (darkTheme) 0.16f else 0.09f)
    if (backdrop == null) {
        if (staticChrome && frozenBackdrop != null) {
            // Replay one GPU layer with full original Prismal blur, lens and
            // specular; children and click highlights are NOT cached.
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            ) {
                GuiFrozenPrismalChrome(
                    backdrop = frozenBackdrop,
                    shape = { PrismalRoundedRectangle(24.dp) },
                    modifier = Modifier.matchParentSize(),
                    blurRadius = 12.dp,
                    refractionHeightPx = 16f,
                    refractionAmountPx = 21f,
                    chromaticAberration = 0.28f,
                    tint = colors.surface,
                    tintAlpha = 0.24f,
                    saturation = 1.32f,
                    depthEffect = true,
                    surfaceColor = if (darkTheme) Color.White.copy(alpha = 0.055f)
                        else Color.Unspecified,
                )
                Column(
                    modifier = Modifier.padding(contentPadding),
                    content = content,
                )
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(solidCardColor)
                    .border(1.dp, cardStroke, cardShape)
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(contentPadding),
                content = content,
            )
        }
        return
    }

    // Dark glass stays refractive; only a faint neutral wash/rim raises separation.
    val glassCardModifier = modifier
        .fillMaxWidth()
        .then(if (darkTheme) Modifier.border(1.dp, cardStroke, cardShape) else Modifier)
    val darkGlassWash = if (darkTheme) Color.White.copy(alpha = 0.055f) else Color.Unspecified
    val lensHeightPx = if (edgeRefraction) 16f else 0f
    val lensAmountPx = if (edgeRefraction) 21f else 0f
    val lensDispersion = if (edgeRefraction) 0.28f else 0f

    val cardContent: @Composable BoxScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
    if (onClick == null || staticPress) {
        // Noninteractive and navigation Cells omit the recaptured depth shadow.
        // Navigation retains its ripple without scale/parallax.
        GuiStaticPressPrismalSurface(
            backdrop = backdrop,
            modifier = glassCardModifier,
            shape = { PrismalRoundedRectangle(24.dp) },
            onClick = onClick,
            blurRadius = 12.dp,
            tint = MiuixTheme.colorScheme.surface,
            tintAlpha = 0.24f,
            saturation = 1.32f,
            refractionHeightPx = lensHeightPx,
            refractionAmountPx = lensAmountPx,
            chromaticAberration = lensDispersion,
            depthEffect = true,
            surfaceColor = darkGlassWash,
            content = cardContent,
        )
    } else {
        PrismalGlassSurface(
            backdrop = backdrop,
            modifier = glassCardModifier,
            shape = { PrismalRoundedRectangle(24.dp) },
            onClick = onClick,
            blurRadius = 12.dp,
            tint = MiuixTheme.colorScheme.surface,
            tintAlpha = 0.24f,
            saturation = 1.32f,
            refractionHeightPx = lensHeightPx,
            refractionAmountPx = lensAmountPx,
            chromaticAberration = lensDispersion,
            depthEffect = true,
            surfaceColor = darkGlassWash,
            content = cardContent,
        )
    }
}

@Composable
internal fun ModernFeatureCard(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSurface = MiuixTheme.colorScheme.onSurface
    ModernSurface(
        modifier = modifier,
        onClick = onClick,
        staticPress = true,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    modifier = Modifier.padding(top = 5.dp),
                    fontSize = 12.sp,
                    color = onSurface.copy(alpha = 0.62f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = onSurface.copy(alpha = 0.38f),
                modifier = Modifier
                    .padding(start = 14.dp)
                    .size(width = 10.dp, height = 16.dp),
            )
        }
    }
}

@Composable
internal fun ModernListDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 18.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startIndent, end = 18.dp)
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

@Composable
internal fun ModernSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.54f),
    )
}

@Composable
internal fun Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModernSurface(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
        content = content,
    )
}

@Composable
internal fun Button(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    minWidth: Dp = 0.dp,
    minHeight: Dp = 36.dp,
    insideMargin: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    destructive: Boolean = false,
    // Only small numeric reset actions opt into native Prismal. Other body
    // buttons keep their static cached/solid performance path.
    prismalNumericAction: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    // This generic action is inside the scrolling page. Top-bar actions
    // are rendered separately by ModernTopActionButton with Prismal.
    val backdrop = if (prismalNumericAction) {
        LocalTouchPrismalBackdrop.current
    } else {
        LocalPrismalSurfaceBackdrop.current
    }
    val staticChrome = LocalStaticGlassChrome.current
    val colors = MiuixTheme.colorScheme
    val darkTheme = colors.background.luminance() < 0.5f
    val resolved = modifier
        .then(if (minWidth > 0.dp) Modifier.widthIn(min = minWidth) else Modifier)
        // Solid fallback actions need a visible boundary even when disabled.
        // Numeric +/- and reset opt into native Prismal when GUI glass is on.
        .alpha(if (enabled) 1f else if (backdrop == null) 0.66f else 0.42f)

    if (backdrop == null) {
        val buttonShape = RoundedCornerShape(minHeight / 2)
        val frozenBackdrop = LocalFrozenPrismalBackdrop.current
        val fillColor = if (destructive) Color(0xFFD73333)
            else lerp(colors.surface, colors.onSurface, if (darkTheme) 0.12f else 0.045f)
        val outlineColor = if (destructive) Color.White.copy(alpha = 0.16f)
            else colors.onSurface.copy(alpha = if (darkTheme) 0.30f else 0.19f)
        Box(
            modifier = resolved
                .height(minHeight)
                .clip(buttonShape)
                .then(if (!destructive && staticChrome && frozenBackdrop != null) Modifier
                    else Modifier.background(fillColor, buttonShape))
                .border(1.dp, outlineColor, buttonShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (!destructive && staticChrome && frozenBackdrop != null) {
                GuiFrozenPrismalChrome(
                    backdrop = frozenBackdrop,
                    shape = { PrismalRoundedRectangle(minHeight / 2) },
                    modifier = Modifier.matchParentSize(),
                    blurRadius = 7.dp,
                    refractionHeightPx = 9f,
                    refractionAmountPx = 12f,
                    tint = colors.surface,
                    tintAlpha = 0.20f,
                    depthEffect = false,
                )
            }
            Row(
                modifier = Modifier.padding(insideMargin),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                content()
            }
        }
        return
    }

    PrismalGlassButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        modifier = resolved,
        isInteractive = enabled,
        height = minHeight,
        blurRadius = 7.dp,
        refractionHeight = 9.dp,
        refractionAmount = 12.dp,
        pressLift = 0.dp,
        contentPadding = insideMargin,
        tint = if (destructive) Color(0xFFD73333) else MiuixTheme.colorScheme.surface,
        tintAlpha = if (destructive) 0.92f else 0.20f,
        depthEffect = false,
        depthShadow = null,
    ) {
        content()
    }
}

@Composable
internal fun ArrowPreference(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    insideMargin: PaddingValues = ModernPreferenceMargin,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        enabled = enabled,
        insideMargin = insideMargin,
        endActions = {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.44f),
                modifier = Modifier.size(width = 10.dp, height = 16.dp),
            )
        },
        onClick = { if (enabled) onClick() },
    )
}

@Composable
internal fun SwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    insideMargin: PaddingValues = ModernPreferenceMargin,
) {
    val backdrop = LocalTouchPrismalBackdrop.current
    // Upstream Prismal remembers its gesture callbacks. Bridge them to the
    // latest setting state instead of retaining values from first composition.
    val selectedState = rememberUpdatedState(checked)
    val enabledState = rememberUpdatedState(enabled)
    val onChangedState = rememberUpdatedState(onCheckedChange)
    val stableSelected = remember { { selectedState.value } }
    val stableToggleChange: (Boolean) -> Unit = remember {
        { next -> if (enabledState.value) onChangedState.value(next) }
    }
    BasicComponent(
        title = title,
        summary = summary,
        enabled = enabled,
        insideMargin = insideMargin,
        endActions = {
            if (backdrop != null) {
                GuiOnTouchPrismalToggle(
                    selected = stableSelected,
                    onSelect = stableToggleChange,
                    backdrop = backdrop,
                    enabled = enabled,
                )
            } else {
                top.yukonga.miuix.kmp.basic.Switch(
                    checked = checked,
                    onCheckedChange = { next -> if (enabled) onCheckedChange(next) },
                    enabled = enabled,
                )
            }
        },
        onClick = { if (enabled) onCheckedChange(!checked) },
    )
}

/**
 * Parse direct numeric input without silently storing an out-of-range or
 * fractional integer value. Persistence and quantization remain with the
 * existing setting's own callback.
 */
internal fun parseNumericSettingInput(
    raw: String,
    integerOnly: Boolean,
    min: Float,
    max: Float,
): Float? {
    val normalized = raw.trim().replace(',', '.')
    val value = if (integerOnly) {
        normalized.toIntOrNull()?.toFloat()
    } else {
        normalized.toFloatOrNull()
    }
    return value?.takeIf { it.isFinite() && it in min..max }
}

@Composable
internal fun NumericSettingInputDialog(
    visible: Boolean,
    title: String,
    currentText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    integerOnly: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var entered by remember(title) { mutableStateOf(TextFieldValue(currentText)) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) {
            entered = TextFieldValue(currentText, selection = TextRange(0, currentText.length))
        }
    }
    val parsed = parseNumericSettingInput(
        entered.text, integerOnly, valueRange.start, valueRange.endInclusive,
    )
    AnimatedSettingsWindowDialog(
        show = visible,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            val minText = if (integerOnly) valueRange.start.roundToInt().toString() else valueRange.start.toString()
            val maxText = if (integerOnly) valueRange.endInclusive.roundToInt().toString() else valueRange.endInclusive.toString()
            Text(
                text = "允许范围：$minText – $maxText",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
            top.yukonga.miuix.kmp.basic.TextField(
                value = entered,
                onValueChange = { entered = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    keyboardType = when {
                        valueRange.start < 0f -> KeyboardType.Text
                        integerOnly -> KeyboardType.Number
                        else -> KeyboardType.Decimal
                    },
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { parsed?.let(onConfirm) },
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = onDismiss,
                    minWidth = 72.dp,
                    minHeight = 40.dp,
                ) {
                    Text("取消")
                }
                Spacer(Modifier.padding(horizontal = 5.dp))
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = { parsed?.let(onConfirm) },
                    enabled = parsed != null,
                    minWidth = 72.dp,
                    minHeight = 40.dp,
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
internal fun SliderPreference(
    value: Float,
    onValueChange: (Float) -> Unit,
    title: String,
    summary: String? = null,
    valueText: String = "",
    valueTextForPreview: ((Float) -> String)? = null,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    snapIncrement: Float = 0f,
    endActions: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = ModernPreferenceMargin,
) {
    val backdrop = LocalTouchPrismalBackdrop.current
    val currentValue by rememberUpdatedState(value)
    var editingValue by remember(title) { mutableStateOf(false) }
    var previewValue by remember(title) { mutableStateOf<Float?>(null) }
    val intervals = (steps + 1).coerceAtLeast(1)
    val stepSize = ((valueRange.endInclusive - valueRange.start) / intervals)
        .takeIf { it > 0f } ?: 0.01f
    fun quantize(raw: Float): Float = DiscreteSliderSteps.snap(
        raw, valueRange.start, valueRange.endInclusive, steps, snapIncrement,
    )
    val motionThreshold = if (steps > 0 || snapIncrement <= 0f) stepSize else snapIncrement
    val sliderEnabledState = rememberUpdatedState(enabled)
    val sliderCallbackState = rememberUpdatedState(onValueChange)
    val quantizeState = rememberUpdatedState<(Float) -> Float>({ raw -> quantize(raw) })
    // Only dispatch when the number shown at the right changes. The track/thumb
    // continue moving freely; persistence receives the displayed snapped value.
    val lastLiveStep = remember(title) { arrayOfNulls<Float>(1) }
    val stableSliderValue = remember { { currentValue } }
    val stableSliderChange: (Float) -> Unit = remember {
        { next ->
            val snapped = quantizeState.value(next)
            if (sliderEnabledState.value && lastLiveStep[0] != snapped) {
                sliderCallbackState.value(snapped)
            }
            lastLiveStep[0] = null
        }
    }
    val stableSliderPreview: (Float?) -> Unit = remember {
        { next ->
            previewValue = next
            if (next == null) {
                lastLiveStep[0] = null
            } else if (sliderEnabledState.value && lastLiveStep[0] != next) {
                lastLiveStep[0] = next
                sliderCallbackState.value(next)
            }
        }
    }
    // Miuix keeps a continuous visual draft, but persists its displayed
    // quantized value while the finger is moving, not only after release.
    var nativeDragging by remember { mutableStateOf(false) }
    var nativeDraft by remember { mutableFloatStateOf(value) }
    val shownValueText = previewValue?.let { valueTextForPreview?.invoke(it) } ?: valueText

    Column {
        BasicComponent(
            title = title,
            summary = summary,
            enabled = enabled,
            insideMargin = insideMargin,
            endActions = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (shownValueText.isNotBlank()) {
                        Text(
                            text = shownValueText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = enabled) { editingValue = true }
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                            fontSize = 13.sp,
                        )
                    }
                    endActions?.invoke()
                }
            },
        )
        if (backdrop != null) {
            GuiOnTouchPrismalSlider(
                value = stableSliderValue,
                onValueChange = stableSliderChange,
                valueRange = valueRange,
                visibilityThreshold = motionThreshold,
                backdrop = backdrop,
                steps = steps,
                snapIncrement = snapIncrement,
                onValuePreview = stableSliderPreview,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
                    .alpha(if (enabled) 1f else 0.42f),
            )
        } else {
            top.yukonga.miuix.kmp.basic.Slider(
                value = if (nativeDragging) nativeDraft else currentValue,
                onValueChange = { next ->
                    if (enabled) {
                        nativeDraft = next
                        nativeDragging = true
                        stableSliderPreview(quantizeState.value(next))
                    }
                },
                onValueChangeFinished = {
                    if (nativeDragging) {
                        val released = nativeDraft
                        nativeDragging = false
                        stableSliderChange(released)
                        stableSliderPreview(null)
                    }
                },
                valueRange = valueRange,
                steps = 0, // Native track stays continuous; only its values snap.
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            )
        }
    }
    val inputStep = if (steps > 0 || snapIncrement <= 0f) stepSize else snapIncrement
    val integerOnly = valueRange.start % 1f == 0f &&
        valueRange.endInclusive % 1f == 0f && inputStep % 1f == 0f
    NumericSettingInputDialog(
        visible = editingValue,
        title = title,
        currentText = if (integerOnly) value.roundToInt().toString() else value.toString(),
        valueRange = valueRange,
        integerOnly = integerOnly,
        onDismiss = { editingValue = false },
        onConfirm = { next ->
            onValueChange(quantize(next))
            editingValue = false
        },
    )
}

@Composable
internal fun ModernGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    snapIncrement: Float = 0f,
    onValuePreview: (Float?) -> Unit = {},
) {
    val backdrop = LocalTouchPrismalBackdrop.current
    val currentValue by rememberUpdatedState(value)
    val sliderEnabledState = rememberUpdatedState(enabled)
    val sliderCallbackState = rememberUpdatedState(onValueChange)
    val previewCallbackState = rememberUpdatedState(onValuePreview)
    val stableSliderPreview: (Float?) -> Unit = remember {
        { next -> previewCallbackState.value(next) }
    }
    val stableSliderValue = remember { { currentValue } }
    val stableSliderChange: (Float) -> Unit = remember {
        { next -> if (sliderEnabledState.value) sliderCallbackState.value(next) }
    }
    var nativeDragging by remember { mutableStateOf(false) }
    var nativeDraft by remember { mutableFloatStateOf(value) }
    if (backdrop != null) {
        GuiOnTouchPrismalSlider(
            value = stableSliderValue,
            onValueChange = stableSliderChange,
            valueRange = valueRange,
            visibilityThreshold = visibilityThreshold,
            backdrop = backdrop,
            enabled = enabled,
            steps = steps,
            snapIncrement = snapIncrement,
            onValuePreview = stableSliderPreview,
            modifier = modifier.alpha(if (enabled) 1f else 0.42f),
        )
    } else {
        top.yukonga.miuix.kmp.basic.Slider(
            value = if (nativeDragging) nativeDraft else currentValue,
            onValueChange = { next ->
                if (enabled) {
                    nativeDraft = next
                    nativeDragging = true
                    previewCallbackState.value(DiscreteSliderSteps.snap(
                        next, valueRange.start, valueRange.endInclusive,
                        steps, snapIncrement,
                    ))
                }
            },
            onValueChangeFinished = {
                if (nativeDragging) {
                    val nearest = DiscreteSliderSteps.snap(
                        nativeDraft, valueRange.start, valueRange.endInclusive,
                        steps, snapIncrement,
                    )
                    nativeDragging = false
                    sliderCallbackState.value(nearest)
                    previewCallbackState.value(null)
                }
            },
            valueRange = valueRange,
            steps = 0, // Apply quantization only after release, not on pointer movement.
            enabled = enabled,
            modifier = modifier,
        )
    }
}

@Composable
internal fun ModernGlassStepper(
    value: Int,
    valueRange: IntRange,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The ordinary body surface backdrop is intentionally null. Restore
    // native Prismal +/- through the same glass-only source used by sliders.
    val backdrop = LocalTouchPrismalBackdrop.current
    if (backdrop != null) {
        PrismalGlassStepper(
            value = value,
            onValueChange = { if (enabled) onValueChange(it) },
            backdrop = backdrop,
            valueRange = valueRange,
            repeatOnHold = true,
            modifier = modifier.alpha(if (enabled) 1f else 0.42f),
        )
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onValueChange((value - 1).coerceAtLeast(valueRange.first)) },
                enabled = enabled && value > valueRange.first,
                minWidth = 36.dp,
                minHeight = 36.dp,
                insideMargin = PaddingValues(0.dp),
            ) { Text("−") }
            Button(
                onClick = { onValueChange((value + 1).coerceAtMost(valueRange.last)) },
                enabled = enabled && value < valueRange.last,
                minWidth = 36.dp,
                minHeight = 36.dp,
                insideMargin = PaddingValues(0.dp),
            ) { Text("+") }
        }
    }
}
