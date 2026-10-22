package com.hellovoid.liquiddock.os4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Os4NativeConfTest {
    @Test
    fun `7x4 snapshot is LF terminated and fail closed`() {
        assertEquals(
            "grid_cols=7\ngrid_rows=4\nend=1\n",
            Os4NativeConfPublisher.buildConf(7, 4),
        )
    }

    @Test
    fun `invalid ranges publish nothing`() {
        assertNull(Os4NativeConfPublisher.buildConf(1, 4))
        assertNull(Os4NativeConfPublisher.buildConf(11, 4))
        assertNull(Os4NativeConfPublisher.buildConf(7, 1))
        assertNull(Os4NativeConfPublisher.buildConf(7, 11))
    }

    @Test
    fun `10x10 snapshot is supported`() {
        assertEquals(
            "grid_cols=10\ngrid_rows=10\nend=1\n",
            Os4NativeConfPublisher.buildConf(10, 10),
        )
    }
}
