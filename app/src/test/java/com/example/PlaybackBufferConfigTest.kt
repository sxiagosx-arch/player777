package com.example

import com.example.ui.player.PlaybackBufferConfig
import com.example.ui.player.VideoQualityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBufferConfigTest {

    @Test
    fun `maximum buffer is clamped to supported limits`() {
        assertEquals(5_000, PlaybackBufferConfig(maxBufferSeconds = 1).maxBufferMs)
        assertEquals(120_000, PlaybackBufferConfig(maxBufferSeconds = 500).maxBufferMs)
    }

    @Test
    fun `playback thresholds stay valid for ExoPlayer`() {
        val config = PlaybackBufferConfig(maxBufferSeconds = 60, playbackStartSeconds = 3)

        assertEquals(20_000, config.minBufferMs)
        assertEquals(60_000, config.maxBufferMs)
        assertEquals(3_000, config.playbackStartMs)
        assertEquals(4_000, config.playbackAfterRebufferMs)
        assertTrue(config.playbackStartMs <= config.minBufferMs)
        assertTrue(config.playbackAfterRebufferMs <= config.minBufferMs)
    }

    @Test
    fun `unknown quality setting safely falls back to automatic`() {
        assertEquals(VideoQualityMode.AUTO, VideoQualityMode.fromStorage("OLD_VALUE"))
        assertEquals(VideoQualityMode.MAXIMUM, VideoQualityMode.fromStorage("MAXIMUM"))
    }
}
