package com.hellovoid.liquiddock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity

/**
 * Trigger only when the scrolling child is already at its top boundary.
 * onPostScroll receives only the unconsumed vertical drag: normal scrolling,
 * slider horizontal gestures and page transitions do not open search.
 */
@Composable
internal fun rememberSettingsPullDownSearch(onTrigger: () -> Unit): NestedScrollConnection {
    val currentTrigger = rememberUpdatedState(onTrigger)
    val density = LocalDensity.current
    val threshold = with(density) { 76.dp.toPx() }
    return remember(threshold) {
        object : NestedScrollConnection {
            private var overscroll = 0f
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) {
                    overscroll = 0f
                } else if (available.y > 0f) {
                    overscroll += available.y
                    if (overscroll >= threshold) {
                        overscroll = 0f
                        currentTrigger.value()
                    }
                } else {
                    overscroll = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                overscroll = 0f
                return Velocity.Zero
            }
        }
    }
}
