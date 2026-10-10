package com.hellovoid.liquiddock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.PrismalGlassSurface
import com.styropyr0.prismal.components.PrismalGlassButton
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalMergedSource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
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
// Frozen and static chrome CompositionLocals are defined in SettingsSurfaceComponents.kt.

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

// Body surface, card, button and preference controls live in SettingsSurfaceComponents.kt.
