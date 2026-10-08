package com.hellovoid.liquiddock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.styropyr0.prismal.components.PrismalGlassMenu
import com.styropyr0.prismal.components.PrismalGlassMenuItem
import com.styropyr0.prismal.components.PrismalGlassSlider
import com.styropyr0.prismal.components.PrismalGlassStepper
import com.styropyr0.prismal.components.PrismalGlassToggle
import com.styropyr0.prismal.components.prismalMenuAnchor
import com.styropyr0.prismal.drawPlainPrismalGlass
import com.styropyr0.prismal.effects.colorControls
import com.styropyr0.prismal.effects.prismalBlur
import com.styropyr0.prismal.shapes.PrismalRoundedRectangle
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalMergedSource
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val ModernPreferenceMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp)

private val LocalPrismalSurfaceBackdrop = staticCompositionLocalOf<PrismalBackdrop?> { null }
private val LocalPrismalOverlayBackdrop = staticCompositionLocalOf<PrismalBackdrop?> { null }

@Composable
internal fun ModernSettingsScaffold(
    title: String,
    showBack: Boolean,
    backLabel: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val backgroundLayer = rememberPrismalGlassLayer()
    val screenLayer = rememberPrismalGlassLayer()
    val overlayBackdrop = rememberPrismalMergedSource(backgroundLayer, screenLayer)
    val background = MiuixTheme.colorScheme.background
    val primary = MiuixTheme.colorScheme.primary
    val surface = MiuixTheme.colorScheme.surface

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
                .prismalGlassLayer(backgroundLayer),
        )

        CompositionLocalProvider(
            LocalPrismalSurfaceBackdrop provides backgroundLayer,
            LocalPrismalOverlayBackdrop provides overlayBackdrop,
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .drawPlainPrismalGlass(
                                    backdrop = overlayBackdrop,
                                    shape = { PrismalRoundedRectangle(0.dp) },
                                    effects = {
                                        prismalBlur(14.dp.value)
                                        colorControls(saturation = 1.16f)
                                    },
                                    onDrawSurface = {
                                        drawRect(surface.copy(alpha = 0.24f))
                                    },
                                ),
                        )
                        TopAppBar(
                            title = title,
                            largeTitle = title,
                            color = Color.Transparent,
                            navigationIcon = {
                                if (showBack) {
                                    PrismalGlassButton(
                                        onClick = onBack,
                                        backdrop = overlayBackdrop,
                                        modifier = Modifier.size(40.dp),
                                        height = 40.dp,
                                        blurRadius = 7.dp,
                                        refractionHeight = 9.dp,
                                        refractionAmount = 12.dp,
                                        pressLift = 2.dp,
                                        contentPadding = PaddingValues(8.dp),
                                        tint = surface,
                                        tintAlpha = 0.20f,
                                        depthEffect = false,
                                    ) {
                                        Text(
                                            text = "‹",
                                            fontSize = 28.sp,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            },
                            actions = actions,
                        )
                    }
                },
                bottomBar = bottomBar,
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .prismalGlassLayer(screenLayer),
                ) {
                    content(padding)
                }
            }
        }
    }
}

@Composable
internal fun ModernBottomNavigation(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val backdrop = LocalPrismalOverlayBackdrop.current
    if (backdrop == null) return
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

            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labels.forEachIndexed { index, label ->
                    val active = index == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (active) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurface.copy(alpha = 0.64f)
                            },
                            fontSize = 12.sp,
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
                .padding(contentPadding)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
    content: @Composable () -> Unit,
) {
    val backdrop = LocalPrismalSurfaceBackdrop.current ?: LocalPrismalOverlayBackdrop.current
    val resolved = modifier
        .then(if (minWidth > 0.dp) Modifier.widthIn(min = minWidth) else Modifier)
        .alpha(if (enabled) 1f else 0.42f)

    if (backdrop == null) {
        Box(
            modifier = resolved
                .height(minHeight)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(insideMargin),
            contentAlignment = Alignment.Center,
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
        pressLift = 2.dp,
        contentPadding = insideMargin,
        tint = MiuixTheme.colorScheme.surface,
        tintAlpha = 0.20f,
        depthEffect = false,
        content = content,
    )
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
                when {
                    endActions != null -> endActions()
                    valueText.isNotBlank() -> Text(
                        text = valueText,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                        fontSize = 13.sp,
                    )
                }
            },
        )
        if (backdrop != null) {
            PrismalGlassSlider(
                value = { value },
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
        }
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
    val backdrop = LocalPrismalSurfaceBackdrop.current ?: return
    PrismalGlassStepper(
        value = value,
        onValueChange = { if (enabled) onValueChange(it) },
        backdrop = backdrop,
        valueRange = valueRange,
        repeatOnHold = true,
        modifier = modifier.alpha(if (enabled) 1f else 0.42f),
    )
}

@Composable
internal fun ModernChoicePreference(
    title: String,
    summary: String,
    items: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val backdrop = LocalPrismalOverlayBackdrop.current ?: LocalPrismalSurfaceBackdrop.current
    var expanded by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .prismalMenuAnchor { anchorBounds = it },
    ) {
        BasicComponent(
            title = title,
            summary = summary,
            enabled = enabled,
            insideMargin = ModernPreferenceMargin,
            endActions = {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.44f),
                    modifier = Modifier.size(width = 10.dp, height = 16.dp),
                )
            },
            onClick = {
                if (enabled && backdrop != null) expanded = true
            },
        )
    }

    if (backdrop != null) {
        PrismalGlassMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchorBounds = anchorBounds,
            backdrop = backdrop,
            width = 300.dp,
            surfaceColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.78f),
        ) {
            items.forEachIndexed { index, label ->
                PrismalGlassMenuItem(
                    text = label,
                    selected = index == selectedIndex,
                    enabled = enabled,
                    onClick = {
                        onSelectedIndexChange(index)
                        expanded = false
                    },
                )
            }
        }
    }
}
