package com.hellovoid.liquiddock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumericSettingInputTest {
    @Test
    fun integerValuesPreserveNegativeAndBothRangeEndpoints() {
        assertEquals(-20f, parseNumericSettingInput("-20", true, -20f, 200f)!!, 0f)
        assertEquals(200f, parseNumericSettingInput("200", true, -20f, 200f)!!, 0f)
        assertEquals(42f, parseNumericSettingInput(" 42 ", true, -20f, 200f)!!, 0f)
    }

    @Test
    fun decimalsSupportSingleDigitAndCommaWithoutDroppingPrecision() {
        assertEquals(1.5f, parseNumericSettingInput("1.5", false, 0f, 5f)!!, 0f)
        assertEquals(1.5f, parseNumericSettingInput("1,5", false, 0f, 5f)!!, 0f)
    }

    @Test
    fun inputRejectsOutOfRangeFractionalIntegersAndNonnumbers() {
        assertNull(parseNumericSettingInput("-21", true, -20f, 200f))
        assertNull(parseNumericSettingInput("201", true, -20f, 200f))
        assertNull(parseNumericSettingInput("3.5", true, 0f, 10f))
        assertNull(parseNumericSettingInput("12.5", false, 0f, 10f))
        assertNull(parseNumericSettingInput("NaN", false, 0f, 10f))
        assertNull(parseNumericSettingInput("Infinity", false, 0f, 10f))
        assertNull(parseNumericSettingInput("", true, 0f, 10f))
        assertNull(parseNumericSettingInput("oops", true, 0f, 10f))
    }
}
