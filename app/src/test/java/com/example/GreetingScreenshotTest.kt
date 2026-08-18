package com.example

import com.example.ui.player.PlaybackBufferConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/** Replaces the obsolete template screenshot that referenced the removed Greeting screen. */
class GreetingScreenshotTest {

    @Test
    fun `default buffer configuration is deterministic`() {
        val config = PlaybackBufferConfig()

        assertEquals(50_000, config.maxBufferMs)
        assertEquals(1_000, config.playbackStartMs)
    }
}
