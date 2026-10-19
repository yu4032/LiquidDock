package com.hellovoid.liquiddock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Use MIUIX's own dialog enter/exit animation and window lifecycle.
 *
 * WindowDialog already animates its dialog surface, retains it until the exit
 * completes, and reserves interior space for controls. Wrapping its children in
 * another fading/scaling graphicsLayer recorded them into a tight intermediate
 * rectangle and cropped the Prismal shadow/edge chrome around those children.
 * Keep just one transition, owned by the window, with no child-level layer.
 */
@Composable
internal fun AnimatedSettingsWindowDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    WindowDialog(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        Column(content = content)
    }
}
