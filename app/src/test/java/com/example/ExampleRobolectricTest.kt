package com.example

import com.example.model.IPTVChannel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Keeps a fast model test without requiring an Android runtime download. */
class ExampleRobolectricTest {

    @Test
    fun `channel keeps safe playback defaults`() {
        val channel = IPTVChannel(id = "1", name = "Canal", url = "https://example.test/live.m3u8")

        assertEquals("LIVE", channel.type)
        assertEquals("HD", channel.resolution)
    }
}
