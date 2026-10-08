package com.hellovoid.liquiddock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.PrismalGlassSurface
import com.styropyr0.prismal.components.PrismalGlassBottomTab
import com.styropyr0.prismal.components.PrismalGlassBottomTabs
import com.styropyr0.prismal.components.PrismalGlassButton
import com.styropyr0.prismal.components.PrismalGlassSlider
import com.styropyr0.prismal.components.PrismalGlassStepper
import com.styropyr0.prismal.components.PrismalGlassToggle
import com.styropyr0.prismal.shapes.PrismalRoundedRectangle
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalMergedSource
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val ModernPreferenceMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
internal const val SETTINGS_UI_PREFS = "liquiddock_settings_ui"
internal const val SETTINGS_UI_GLASS_ENABLED = "glass_effect_enabled"

// Uniform background blur, with no progressive gradient or refractive edge rim.
private val TOP_BAR_GLASS_BLUR = 14.dp
private val TOP_BAR_ACTION_SHADOW_ROOM = 10.dp
private const val TOP_BAR_BOTTOM_STROKE_ALPHA = 0.10f

private val LocalPrismalSurfaceBackdrop = staticCompositionLocalOf<PrismalBackdrop?> { null }
private val LocalPrismalOverlayBackdrop = staticCompositionLocalOf<PrismalBackdrop?> { null }

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
    val surfaceBackdrop = if (glassEnabled) backgroundLayer else null
    val activeOverlayBackdrop = if (glassEnabled) overlayBackdrop else null
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            background,
                            primary.copy(alpha = 0.07f),
                            background,
                        ),
                    ),
                )
                .then(
                    if (glassEnabled) Modifier.prismalGlassLayer(backgroundLayer)
                    else Modifier,
                ),
        )

        CompositionLocalProvider(
            LocalPrismalSurfaceBackdrop provides surfaceBackdrop,
            LocalPrismalOverlayBackdrop provides activeOverlayBackdrop,
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Blur the real scene behind the header uniformly, not through
                        // a gradient or a perimeter lens. The bar stays rectangular.
                        if (glassEnabled) {
                            PrismalGlassSurface(
                                backdrop = overlayBackdrop,
                                modifier = Modifier.matchParentSize(),
                                shape = { PrismalRoundedRectangle(0.dp) },
                                blurRadius = TOP_BAR_GLASS_BLUR,
                                // Prismal's tint applies BlendMode.Hue even for low alpha,
                                // turning green backdrops purple under some Monet palettes.
                                // Use neutral glass instead so sampled background hues survive.
                                tint = Color.Unspecified,
                                surfaceColor = Color.Gray.copy(alpha = 0.06f),
                                refractionHeightPx = 0f,
                                refractionAmountPx = 0f,
                                chromaticAberration = 0f,
                                depthEffect = false,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(surface),
                            )
                        }

                        SmallTopAppBar(
                            title = title,
                            color = Color.Transparent,
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
                                                tint = surface,
                                                tintAlpha = 0.20f,
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
                },
                bottomBar = bottomBar,
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
) {
    if (!visible) return
    val backdrop = LocalPrismalOverlayBackdrop.current ?: LocalPrismalSurfaceBackdrop.current

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
                .widthIn(max = 520.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            onClick = {},
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
                    SwitchPreference(
                        checked = item.id in selected,
                        onCheckedChange = { onToggle(item.id) },
                        title = item.title,
                        summary = item.packageName,
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
                        onClick = { if (selected.isNotEmpty()) onRestart() },
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
                            .clickable(enabled = selected.isNotEmpty(), onClick = onRestart)
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

@Composable
internal fun ModernBottomNavigation(
    labels: List<String>,
    icons: List<ImageVector>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val backdrop = LocalPrismalOverlayBackdrop.current
    val selected by rememberUpdatedState(selectedIndex)
    val onSelect by rememberUpdatedState(onSelected)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .height(64.dp),
        ) {
            if (backdrop != null) {
                PrismalGlassBottomTabs(
                    selectedTabIndex = { selected },
                    onTabSelected = { index ->
                        if (index != selected) onSelect(index)
                    },
                    backdrop = backdrop,
                    tabsCount = labels.size,
                    modifier = Modifier.fillMaxSize(),
                    tintDropletContent = false,
                    dropletContentTint = MiuixTheme.colorScheme.primary,
                ) {
                    labels.indices.forEach { index ->
                        PrismalGlassBottomTab(
                            onClick = {
                                if (index != selected) onSelect(index)
                            },
                        ) {}
                    }
                }
            } else {
                val fallbackShape = RoundedCornerShape(30.dp)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            color = MiuixTheme.colorScheme.surface.copy(alpha = 0.96f),
                            shape = fallbackShape,
                        )
                        .border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                            shape = fallbackShape,
                        ),
                )
            }

            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labels.forEachIndexed { index, label ->
                    val active = index == selected
                    val contentColor = if (active) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.64f)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                if (index != selected) onSelect(index)
                            },
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = icons[index],
                            contentDescription = label,
                            tint = contentColor,
                            modifier = Modifier.size(21.dp),
                        )
                        Text(
                            text = label,
                            color = contentColor,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModernTopActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalPrismalOverlayBackdrop.current ?: LocalPrismalSurfaceBackdrop.current
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
        tint = MiuixTheme.colorScheme.surface,
        tintAlpha = 0.20f,
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalPrismalSurfaceBackdrop.current
    if (backdrop == null) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.94f))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(contentPadding),
            content = content,
        )
        return
    }

    PrismalGlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth(),
        shape = { PrismalRoundedRectangle(24.dp) },
        onClick = onClick,
        blurRadius = 12.dp,
        tint = MiuixTheme.colorScheme.surface,
        tintAlpha = 0.24f,
        saturation = 1.32f,
        refractionHeightPx = 16f,
        refractionAmountPx = 21f,
        chromaticAberration = 0.28f,
        depthEffect = true,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
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
    content: @Composable RowScope.() -> Unit,
) {
    val backdrop = LocalPrismalSurfaceBackdrop.current ?: LocalPrismalOverlayBackdrop.current
    val resolved = modifier
        .then(if (minWidth > 0.dp) Modifier.widthIn(min = minWidth) else Modifier)
        .alpha(if (enabled) 1f else 0.42f)

    if (backdrop == null) {
        Row(
            modifier = resolved
                .height(minHeight)
                .clip(RoundedCornerShape(minHeight / 2))
                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.96f))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(insideMargin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            content()
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
        tint = MiuixTheme.colorScheme.surface,
        tintAlpha = 0.20f,
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
    val backdrop = LocalPrismalSurfaceBackdrop.current
    BasicComponent(
        title = title,
        summary = summary,
        enabled = enabled,
        insideMargin = insideMargin,
        endActions = {
            if (backdrop != null) {
                PrismalGlassToggle(
                    selected = { checked },
                    onSelect = { next -> if (enabled) onCheckedChange(next) },
                    backdrop = backdrop,
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

@Composable
internal fun SliderPreference(
    value: Float,
    onValueChange: (Float) -> Unit,
    title: String,
    summary: String? = null,
    valueText: String = "",
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    endActions: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = ModernPreferenceMargin,
) {
    val backdrop = LocalPrismalSurfaceBackdrop.current
    val currentValue by rememberUpdatedState(value)
    val intervals = (steps + 1).coerceAtLeast(1)
    val stepSize = ((valueRange.endInclusive - valueRange.start) / intervals)
        .takeIf { it > 0f } ?: 0.01f
    fun quantize(raw: Float): Float {
        if (steps <= 0) return raw.coerceIn(valueRange.start, valueRange.endInclusive)
        val index = ((raw - valueRange.start) / stepSize).roundToInt()
        return (valueRange.start + index * stepSize)
            .coerceIn(valueRange.start, valueRange.endInclusive)
    }

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
                    if (valueText.isNotBlank()) {
                        Text(
                            text = valueText,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                            fontSize = 13.sp,
                        )
                    }
                    endActions?.invoke()
                }
            },
        )
        if (backdrop != null) {
            PrismalGlassSlider(
                value = { currentValue },
                onValueChange = { next ->
                    if (enabled) onValueChange(quantize(next))
                },
                valueRange = valueRange,
                visibilityThreshold = stepSize,
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
                    .alpha(if (enabled) 1f else 0.42f),
            )
        } else {
            top.yukonga.miuix.kmp.basic.Slider(
                value = currentValue,
                onValueChange = { next ->
                    if (enabled) onValueChange(quantize(next))
                },
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            )
        }
    }
}

@Composable
internal fun ModernGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalPrismalSurfaceBackdrop.current
    val currentValue by rememberUpdatedState(value)
    if (backdrop != null) {
        PrismalGlassSlider(
            value = { currentValue },
            onValueChange = { next ->
                if (enabled) onValueChange(next)
            },
            valueRange = valueRange,
            visibilityThreshold = visibilityThreshold,
            backdrop = backdrop,
            modifier = modifier.alpha(if (enabled) 1f else 0.42f),
        )
    } else {
        top.yukonga.miuix.kmp.basic.Slider(
            value = currentValue,
            onValueChange = { next ->
                if (enabled) onValueChange(next)
            },
            valueRange = valueRange,
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
    val backdrop = LocalPrismalSurfaceBackdrop.current
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
