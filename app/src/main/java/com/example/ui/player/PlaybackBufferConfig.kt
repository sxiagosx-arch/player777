package com.example.ui.player

/**
 * Real ExoPlayer buffer values, persisted by the settings screen.
 * The maximum reserve and playback threshold are user-controlled independently.
 */
data class PlaybackBufferConfig(
    val maxBufferSeconds: Int = 50,
    val playbackStartSeconds: Int = 1
) {
    val maxBufferMs: Int = maxBufferSeconds.coerceIn(MIN_MAX_SECONDS, MAX_MAX_SECONDS) * 1_000
    val minBufferMs: Int = (maxBufferMs / 4).coerceIn(2_000, maxBufferMs)
    val playbackStartMs: Int = (playbackStartSeconds.coerceIn(MIN_START_SECONDS, MAX_START_SECONDS) * 1_000)
        .coerceAtMost(minBufferMs)
    val playbackAfterRebufferMs: Int = (playbackStartMs + 1_000).coerceAtMost(minBufferMs)

    companion object {
        const val MIN_MAX_SECONDS = 5
        const val MAX_MAX_SECONDS = 120
        const val MIN_START_SECONDS = 1
        const val MAX_START_SECONDS = 5
    }
}

/** Persistent, real track-selection modes used by Media3. */
enum class VideoQualityMode {
    AUTO,
    MAXIMUM,
    DATA_SAVER;

    companion object {
        fun fromStorage(value: String): VideoQualityMode = entries
            .firstOrNull { it.name == value }
            ?: AUTO
    }
}
