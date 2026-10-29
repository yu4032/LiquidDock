package com.hellovoid.liquiddock

import com.hellovoid.liquiddock.config.ConfigSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The UI grouping is a semantic partition, never a blind chunked(3) split. */
class LiquidSettingGroupsTest {
    @Test fun everyOpticalConfigAppearsInExactlyOneFunctionalGroup() {
        val groups = liquidAllGroups
        val keys = groups.flatMap { it.specs }.map { it.key }
        assertEquals("all original controls retained", liquidSpecs.size, keys.size)
        assertEquals("no duplicate controls", keys.size, keys.toSet().size)
        assertEquals("complete coverage", liquidSpecs.map { it.key }.toSet(), keys.toSet())
        assertTrue("bounded Prismal cell complexity", groups.all {
            it.specs.isNotEmpty() && it.specs.size <= 3
        })
        assertEquals("stable unique group headings", groups.size, groups.map { it.title }.toSet().size)
    }

    @Test fun colorChannelsAndDispersionNeverShareACell() {
        val groups = liquidColorGroups
        assertEquals(
            listOf("玻璃染色（保留纹理）", "染色强度与饱和度",
                "玻璃底色（纯色覆盖）", "底色透明度", "色散"),
            groups.map { it.title },
        )
        assertEquals(
            listOf(ConfigSchema.Glass.TINT_RED, ConfigSchema.Glass.TINT_GREEN,
                ConfigSchema.Glass.TINT_BLUE),
            groups[0].specs.map { it.config },
        )
        assertEquals(
            listOf(ConfigSchema.Glass.TINT_ALPHA, ConfigSchema.Glass.PRISMAL_VIBRANCY),
            groups[1].specs.map { it.config },
        )
        assertEquals(
            listOf(ConfigSchema.Glass.BASE_COLOR_RED, ConfigSchema.Glass.BASE_COLOR_GREEN,
                ConfigSchema.Glass.BASE_COLOR_BLUE),
            groups[2].specs.map { it.config },
        )
        assertEquals(listOf(ConfigSchema.Glass.BASE_COLOR_ALPHA),
            groups[3].specs.map { it.config })
        assertEquals(
            listOf(ConfigSchema.Glass.CHROMATIC,
                ConfigSchema.Glass.PRISMAL_DISPERSION_R,
                ConfigSchema.Glass.PRISMAL_DISPERSION_B),
            groups[4].specs.map { it.config },
        )
        assertFalse(groups[0].specs.any { it.config == ConfigSchema.Glass.CHROMATIC })
    }

    @Test fun eachLeafPagePreservesAllItsExistingConfigKeys() {
        fun assertSame(groups: List<LiquidSpecGroup>, old: List<IntSpec>) {
            assertEquals(old.map { it.key }.toSet(),
                groups.flatMap { it.specs }.map { it.key }.toSet())
            assertEquals(old.size, groups.flatMap { it.specs }.size)
        }
        assertSame(liquidMaterialGroups, liquidMaterialSpecs)
        assertSame(liquidRefractionGroups, liquidRefractionSpecs)
        assertSame(liquidColorGroups, liquidColorSpecs)
        assertSame(liquidLightingGroups, liquidLightingSpecs)
        assertSame(liquidOs4Groups, liquidOs4Specs)
        assertSame(liquidShadowGroups, liquidShadowSpecs)
        assertSame(liquidSamplingGroups, liquidSamplingSpecs)
    }
}
