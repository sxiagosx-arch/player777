@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.example.model.IPTVChannel

data class PlaybackSession(
    val player: ExoPlayer
) {
    fun release() = player.release()
}

/** Creates one deterministic playback session for the full-screen player. */
object ExoPlayerManager {
    fun createSession(
        context: Context,
        dataSourceFactory: DataSource.Factory,
        channel: IPTVChannel,
        initialPositionMs: Long,
        bufferConfig: PlaybackBufferConfig,
        preferHardwareDecoding: Boolean,
        videoQualityMode: VideoQualityMode
    ): PlaybackSession {
        val appContext = context.applicationContext
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferConfig.minBufferMs,
                bufferConfig.maxBufferMs,
                bufferConfig.playbackStartMs,
                bufferConfig.playbackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = DefaultTrackSelector(appContext).apply {
            val parameters = buildUponParameters()
                .clearVideoSizeConstraints()
                .clearViewportSizeConstraints()
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true) // Fix 4K black screen
                .setPreferredAudioLanguages("pt-BR", "pt", "por")
                .setPreferredTextLanguages("pt-BR", "pt", "por")

            when (videoQualityMode) {
                VideoQualityMode.AUTO -> parameters.setForceHighestSupportedBitrate(false)
                VideoQualityMode.MAXIMUM -> parameters.setForceHighestSupportedBitrate(true)
                VideoQualityMode.DATA_SAVER -> parameters
                    .setForceHighestSupportedBitrate(false)
                    .setMaxVideoSize(1280, 720)
                    .setMaxVideoBitrate(2_500_000)
            }
            setParameters(parameters)
        }

        val renderersFactory = DefaultRenderersFactory(appContext)
            .setMediaCodecSelector(
                if (preferHardwareDecoding) MediaCodecSelector.DEFAULT else MediaCodecSelector.PREFER_SOFTWARE
            )
            // Restore native hardware decoders as default for stability and 4K
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(
                DefaultLoadErrorHandlingPolicy(if (channel.type == "LIVE") 8 else 5)
            )

        val localPlayer = ExoPlayer.Builder(appContext, renderersFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(appContext))
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(15_000)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                setHandleAudioBecomingNoisy(true)
                videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }

        if (channel.url.isNotBlank()) {
            try {
                // Local ExoPlayer is deliberately the only startup path. A CastPlayer
                // creates a remote controller even when no receiver is selected and that
                // path is not reliable on all Android TV/GMS combinations.
                localPlayer.setMediaItem(channel.toMediaItem())
                if (initialPositionMs > 0L) localPlayer.seekTo(initialPositionMs)
                localPlayer.prepare()
                localPlayer.playWhenReady = true
            } catch (error: Throwable) {
                localPlayer.release()
                throw error
            }
        }

        return PlaybackSession(localPlayer)
    }
}

private fun IPTVChannel.toMediaItem(): MediaItem {
    val artworkUri = logo.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        ?.let(Uri::parse)
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(url)
        .setMimeType(inferMimeType(url))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setSubtitle(description.ifBlank { categoryName })
                .setArtworkUri(artworkUri)
                .build()
        )
        .build()
}

private fun inferMimeType(url: String): String? {
    val cleanUrl = url.substringBefore('?').lowercase()
    return when {
        cleanUrl.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        cleanUrl.endsWith(".mp4") || cleanUrl.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
        cleanUrl.endsWith(".ts") || cleanUrl.endsWith(".mpegts") -> MimeTypes.VIDEO_MP2T
        cleanUrl.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
        cleanUrl.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
        else -> null
    }
}
