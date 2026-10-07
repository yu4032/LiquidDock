package com.hellovoid.liquiddock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.PrismalGlassSurface
import com.styropyr0.prismal.components.LocalPrismalBottomTabHighlightedIndex
import com.styropyr0.prismal.components.PrismalGlassBottomTab
import com.styropyr0.prismal.components.PrismalGlassBottomTabs
import com.styropyr0.prismal.components.PrismalGlassToggle
import com.styropyr0.prismal.components.PrismalGlassSlider
import com.styropyr0.prismal.shapes.PrismalRoundedRectangle
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalMergedSource

internal val LiquidDockPreferenceMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp)

private val LocalSettingsScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }
private val LocalPrismalSurfaceBackdrop = staticCompositionLocalOf<PrismalBackdrop?> { null }

@Composable
internal fun LiquidDockTheme(content: @Composable () -> Unit) {
    val controller = remember { ThemeController(ColorSchemeMode.MonetSystem) }
    MiuixTheme(controller = controller, content = content)
}

@Composable
internal fun LiquidDockSettingsScaffold(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable (PrismalBackdrop) -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backgroundLayer = rememberPrismalGlassLayer()
    val screenLayer = rememberPrismalGlassLayer()
    val overlayBackdrop = rememberPrismalMergedSource(backgroundLayer, screenLayer)
    val background = MiuixTheme.colorScheme.background
    val primary = MiuixTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            background,
                            primary.copy(alpha = 0.08f),
                            background,
                        ),
                    ),
                )
                .prismalGlassLayer(backgroundLayer),
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PrismalGlassSurface(
                    backdrop = overlayBackdrop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = { RectangleShape },
                    blurRadius = 14.dp,
                    tint = MiuixTheme.colorScheme.surface,
                    tintAlpha = 0.34f,
                    saturation = 1.35f,
                    refractionHeightPx = 18f,
                    refractionAmountPx = 24f,
                    chromaticAberration = 0.45f,
                    depthEffect = true,
                ) {
                    TopAppBar(
                        title = title,
                        largeTitle = title,
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        titlePadding = 20.dp,
                        navigationIcon = {
                            if (showBack) {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = MiuixIcons.Back,
                                        contentDescription = null,
                                    )
                                }
                            }
                        },
                        actions = actions,
                    )
                }
            },
            bottomBar = { bottomBar(overlayBackdrop) },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .prismalGlassLayer(screenLayer),
            ) {
                CompositionLocalProvider(
                    LocalSettingsScrollBehavior provides scrollBehavior,
                    LocalPrismalSurfaceBackdrop provides backgroundLayer,
                ) {
                    content(padding)
                }
            }
        }
    }
}

@Composable
internal fun LiquidDockSettingsPage(
    padding: PaddingValues,
    extraBottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = LocalSettingsScrollBehavior.current
    var modifier = Modifier
        .fillMaxSize()
        .overScrollVertical()
    if (scrollBehavior != null) {
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + extraBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
internal fun LiquidDockSectionTitle(title: String) {
    SmallTitle(
        text = title,
        modifier = Modifier.padding(top = 4.dp),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
internal fun LiquidDockSectionCard(
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalPrismalSurfaceBackdrop.current
    if (backdrop == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            content = content,
        )
        return
    }

    PrismalGlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = { PrismalRoundedRectangle(24.dp) },
        onClick = onClick,
        blurRadius = 12.dp,
        tint = MiuixTheme.colorScheme.surface,
        tintAlpha = 0.26f,
        saturation = 1.4f,
        refractionHeightPx = 16f,
        refractionAmountPx = 22f,
        chromaticAberration = 0.35f,
        depthEffect = true,
    ) {
        Column(content = content)
    }
}

@Composable
internal fun LiquidDockActionRow(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(22.dp),
            )
        },
        endActions = {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(width = 10.dp, height = 16.dp),
            )
        },
        insideMargin = LiquidDockPreferenceMargin,
        onClick = onClick,
    )
}

@Composable
internal fun LiquidDockGlassToggleRow(
    title: String,
    summary: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val backdrop = LocalPrismalSurfaceBackdrop.current
    BasicComponent(
        title = title,
        summary = summary,
        enabled = enabled,
        insideMargin = LiquidDockPreferenceMargin,
        endActions = {
            if (backdrop != null) {
                PrismalGlassToggle(
                    selected = { checked },
                    onSelect = { next ->
                        if (enabled) onCheckedChange(next)
                    },
                    backdrop = backdrop,
                )
            }
        },
        onClick = {
            if (enabled) onCheckedChange(!checked)
        },
    )
}

@Composable
internal fun LiquidDockGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalPrismalSurfaceBackdrop.current ?: return
    PrismalGlassSlider(
        value = { value },
        onValueChange = { next ->
            if (enabled) onValueChange(next)
        },
        valueRange = valueRange,
        visibilityThreshold = visibilityThreshold,
        backdrop = backdrop,
        modifier = modifier.alpha(if (enabled) 1f else 0.42f),
    )
}

@Composable
internal fun LiquidDockGlassNavigationBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    labels: List<String>,
    icons: List<ImageVector>,
    backdrop: PrismalBackdrop,
    modifier: Modifier = Modifier,
) {
    PrismalGlassBottomTabs(
        selectedTabIndex = { selectedIndex },
        onTabSelected = onSelected,
        backdrop = backdrop,
        tabsCount = labels.size,
        modifier = modifier,
        tintDropletContent = true,
        dropletContentTint = MiuixTheme.colorScheme.primary,
    ) {
        labels.forEachIndexed { index, label ->
            val highlightedIndex = LocalPrismalBottomTabHighlightedIndex.current()
            val selected = highlightedIndex == index
            val contentColor = if (selected) {
                MiuixTheme.colorScheme.onSurface
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantActions
            }
            PrismalGlassBottomTab(
                onClick = { onSelected(index) },
            ) {
                Icon(
                    imageVector = icons[index],
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = label,
                    color = contentColor,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                )
            }
        }
    }
}
