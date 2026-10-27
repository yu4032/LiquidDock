package com.hellovoid.liquiddock

import com.hellovoid.liquiddock.config.ConfigKey

/** Complete, ordered list data: never populated by composition or visible-row discovery. */
internal sealed interface SettingsBlock {
    val id: String
    data class Header(override val id: String, val title: String) : SettingsBlock
    data class Info(override val id: String, val text: String) : SettingsBlock
    data class Row(
        override val id: String,
        val key: String,
        val title: String,
        val summary: String = "",
        val number: IntSpec? = null,
        val toggle: ConfigKey<Boolean>? = null,
        val scope: String? = null,
        val requires: List<Pair<ConfigKey<Boolean>, Boolean>> = emptyList(),
    ) : SettingsBlock {
        init { require((number != null) xor (toggle != null)) }
    }
    data class Link(
        override val id: String, val title: String, val summary: String, val destination: Page,
    ) : SettingsBlock
    data class ResetEdge(override val id: String, val scope: String) : SettingsBlock
}

internal data class SettingsMeta(
    val page: Page,
    val title: String,
    val blocks: List<SettingsBlock>,
    val requires: List<Pair<ConfigKey<Boolean>, Boolean>> = emptyList(),
) {
    init {
        require(blocks.map { it.id }.distinct().size == blocks.size) { "Duplicate block ID on $page" }
        val keys = blocks.filterIsInstance<SettingsBlock.Row>().map { it.key }
        require(keys.distinct().size == keys.size) { "Duplicate preference key on $page" }
    }
    fun indexOfKey(key: String): Int = blocks.indexOfFirst { it is SettingsBlock.Row && it.key == key }
}

/** Positive scroll moves content up. Oversize controls retain their leading edge. */
internal fun settingsCenterScrollDelta(offset: Int, size: Int, viewportStart: Int, viewportEnd: Int): Float =
    if (size >= viewportEnd - viewportStart) (offset - viewportStart).toFloat()
    else offset + size / 2f - (viewportStart + viewportEnd) / 2f
