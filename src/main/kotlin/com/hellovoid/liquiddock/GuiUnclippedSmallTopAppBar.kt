package com.hellovoid.liquiddock

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUIX small app-bar geometry without its final clipToBounds().
 *
 * MIUIX clips after applying the top system-bar inset, cutting off the
 * upper Prismal shadow of BOTH navigation and action buttons. Keep MIUIX
 * metrics, title typography, window insets, hit targets and bottom slot;
 * remove only the shared parent clip, not the buttons' optical effects.
 */
@Composable
internal fun GuiUnclippedSmallTopAppBar(
    title: String,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    bottomContent: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box(Modifier.layoutId("navigationIcon").padding(start = TopAppBarDefaults.NavigationIconPadding)) {
                navigationIcon()
            }
            Box(Modifier.layoutId("title").padding(horizontal = TopAppBarDefaults.TitlePadding)) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    fontSize = MiuixTheme.textStyles.title3.fontSize,
                    fontWeight = FontWeight.Medium,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
            Box(Modifier.layoutId("actionIcons").padding(end = TopAppBarDefaults.ActionIconPadding)) {
                Row(content = actions)
            }
            Box(Modifier.layoutId("bottomContent")) { bottomContent() }
        },
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            // No clipToBounds(): glass button shadows extend into the inset.
            .pointerInput(Unit) { detectTapGestures { /* consume bar background taps */ } },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val navigation = measurables.first { it.layoutId == "navigationIcon" }.measure(loose)
        val actionIcons = measurables.first { it.layoutId == "actionIcons" }.measure(loose)
        val freeTitleWidth = if (constraints.maxWidth == Constraints.Infinity) {
            Constraints.Infinity
        } else {
            (constraints.maxWidth - navigation.width - actionIcons.width).coerceAtLeast(0)
        }
        val titleWidth = if (freeTitleWidth == Constraints.Infinity) {
            Constraints.Infinity
        } else {
            (freeTitleWidth * 0.9f).roundToInt()
        }
        val titlePlaceable = measurables.first { it.layoutId == "title" }
            .measure(loose.copy(maxWidth = titleWidth))
        val bottom = measurables.first { it.layoutId == "bottomContent" }.measure(loose)
        val barHeight = TopAppBarDefaults.CollapsedHeight.roundToPx()
        val centerY = TopAppBarDefaults.SmallTopAppBarCenterHeight.roundToPx() / 2
        val contentHeight = maxOf(barHeight, centerY + titlePlaceable.height / 2)
        layout(constraints.maxWidth, contentHeight + bottom.height) {
            navigation.placeRelative(0, centerY - navigation.height / 2)
            var titleX = (constraints.maxWidth - titlePlaceable.width) / 2
            if (titleX < navigation.width) {
                titleX = navigation.width
            } else if (titleX + titlePlaceable.width > constraints.maxWidth - actionIcons.width) {
                titleX = constraints.maxWidth - actionIcons.width - titlePlaceable.width
            }
            titlePlaceable.placeRelative(titleX, centerY - titlePlaceable.height / 2)
            actionIcons.placeRelative(
                constraints.maxWidth - actionIcons.width,
                centerY - actionIcons.height / 2,
            )
            bottom.placeRelative(0, contentHeight)
        }
    }
}
