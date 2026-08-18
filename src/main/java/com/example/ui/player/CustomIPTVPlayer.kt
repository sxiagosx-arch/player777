package com.example.ui.player

import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import com.example.model.IPTVChannel
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenDim
import com.example.ui.theme.NeonGreenGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CustomIPTVPlayer(
    channel: IPTVChannel,
    onClose: () -> Unit,
    onSaveProgress: (Long, Long) -> Unit,
    initialPositionMs: Long = 0L,
    adjacentChannels: List<IPTVChannel> = emptyList(),
    seriesSeasons: List<com.example.model.IPTVSeason> = emptyList(),
    epgList: List<com.example.model.EPGProgram> = emptyList(),
    deviceLayoutMode: String = "MOBILE",
    bufferConfig: PlaybackBufferConfig = PlaybackBufferConfig(),
    preferHardwareDecoding: Boolean = true,
    videoQualityMode: VideoQualityMode = VideoQualityMode.AUTO,
    inlineMode: Boolean = false,
    isFav: Boolean = false,
    onToggleFav: () -> Unit = {},
    onChannelChange: (IPTVChannel) -> Unit,
    onFullscreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    
    var exoPlayer by remember { mutableStateOf<Player?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    
    // UI STATES (Exclusive)
    var showControls by remember { mutableStateOf(false) }
    var showChannelsList by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    val controlFocusRequester = remember { FocusRequester() }
    val channelListFocusRequester = remember { FocusRequester() }
    val playerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(channel.id) {
        showChannelsList = false
        showControls = true
    }

    DisposableEffect(inlineMode) {
        val originalOrientation = activity?.requestedOrientation
        if (!inlineMode) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val controller = activity?.let { androidx.core.view.WindowInsetsControllerCompat(it.window, it.window.decorView) }
            controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (!inlineMode) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val controller = activity?.let { androidx.core.view.WindowInsetsControllerCompat(it.window, it.window.decorView) }
                controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                if (originalOrientation != null) activity.requestedOrientation = originalOrientation
            }
        }
    }

    DisposableEffect(channel, bufferConfig, preferHardwareDecoding, videoQualityMode) {
        val dataSourceFactory = CronetUtil.getDataSourceFactory()
        val session = ExoPlayerManager.createSession(
            context = context,
            dataSourceFactory = dataSourceFactory,
            channel = channel,
            initialPositionMs = initialPositionMs,
            bufferConfig = bufferConfig,
            preferHardwareDecoding = preferHardwareDecoding,
            videoQualityMode = videoQualityMode
        )
        val player = session.player
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) {
                    playbackError = null
                    totalDuration = player.duration.takeIf { it > 0L } ?: 0L
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = "Erro no carregamento. Verifique sua lista ou conexão."
            }
        }
        player.addListener(listener)
        exoPlayer = player
        onDispose {
            player.removeListener(listener)
            val p = exoPlayer
            exoPlayer = null
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try { 
                    p?.stop()
                    session.release() 
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        while (exoPlayer != null) {
            delay(1000)
            exoPlayer?.let {
                currentPosition = it.currentPosition
                totalDuration = it.duration.takeIf { d -> d > 0L } ?: 0L
                if (totalDuration > 0) onSaveProgress(currentPosition, totalDuration)
            }
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            showChannelsList = false
            delay(8000)
            showControls = false
        }
    }
    
    LaunchedEffect(showChannelsList) {
        if (showChannelsList) {
            showControls = false
        }
    }

    BackHandler {
        if (showChannelsList) showChannelsList = false
        else if (showControls) showControls = false
        else onClose()
    }

    LaunchedEffect(Unit) {
        if (deviceLayoutMode == "TV") {
            delay(500)
            runCatching { playerFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (deviceLayoutMode != "TV") return@onKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode
                
                if (event.type == KeyEventType.KeyDown) {
                    if (!showControls && !showChannelsList) {
                        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                            showChannelsList = true
                        } else {
                            showControls = true
                        }
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false

                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (!showControls && !showChannelsList) {
                            showControls = true
                        } else {
                            exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (!showChannelsList) {
                            showChannelsList = true
                            showControls = false
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!showControls && !showChannelsList) showControls = true
                        else if (channel.type != "LIVE") exoPlayer?.let { it.seekTo((it.currentPosition - 15000L).coerceAtLeast(0L)) }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!showControls && !showChannelsList) showControls = true
                        else if (channel.type != "LIVE") exoPlayer?.let { it.seekTo((it.currentPosition + 15000L).coerceAtMost(it.duration)) }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.KEYCODE_ESCAPE -> {
                        if (showChannelsList || showControls) {
                            showChannelsList = false
                            showControls = false
                            runCatching { playerFocusRequester.requestFocus() }
                            true
                        } else {
                            onClose()
                            true
                        }
                    }
                    else -> false
                }
            }
    ) {
        // SURFACE
        AndroidView(
            modifier = Modifier.fillMaxSize().zIndex(0f),
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
            }
        )

        // CONTROLS
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.zIndex(1f)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) {
                Column(Modifier.align(Alignment.TopStart).padding(32.dp)) {
                    Text(channel.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
                    if (channel.epgTitle.isNotEmpty()) {
                        Text(channel.epgTitle, color = NeonGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Box(Modifier.align(Alignment.Center)) {
                    IconButton(
                        onClick = { exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() } },
                        modifier = Modifier
                            .size(110.dp)
                            .background(NeonGreen, RoundedCornerShape(55.dp))
                            .focusRequester(controlFocusRequester)
                            .focusable()
                    ) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(70.dp), tint = Color.Black)
                    }
                }

                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 48.dp, vertical = 60.dp)) {
                    if (totalDuration > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(currentPosition), color = Color.White, fontWeight = FontWeight.Bold)
                            Text(formatTime(totalDuration), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { if (totalDuration > 0) currentPosition.toFloat() / totalDuration.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                            color = NeonGreen,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).background(Color.Red, RoundedCornerShape(7.dp)))
                            Spacer(Modifier.width(12.dp))
                            Text("AO VIVO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                    }
                }
            }
            LaunchedEffect(Unit) {
                if (deviceLayoutMode == "TV") runCatching { controlFocusRequester.requestFocus() }
            }
        }

        // CHANNELS LIST
        AnimatedVisibility(
            visible = showChannelsList,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.zIndex(2f)
        ) {
            val list = if (channel.type == "SERIES") seriesSeasons.flatMap { it.episodes } else adjacentChannels
            Box(Modifier.fillMaxHeight().width(420.dp).background(Color.Black.copy(alpha = 0.98f)).border(2.dp, NeonGreen)) {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("CANAIS", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    Spacer(Modifier.height(24.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(list) { ch ->
                            val isSel = ch.id == channel.id
                            var isFoc by remember { mutableStateOf(false) }
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (isFoc) NeonGreen else if (isSel) NeonGreen.copy(0.25f) else Color.Transparent)
                                    .onFocusChanged { isFoc = it.isFocused }
                                    .then(if (isSel) Modifier.focusRequester(channelListFocusRequester) else Modifier)
                                    .focusable()
                                    .clickable { onChannelChange(ch); showChannelsList = false }
                                    .padding(18.dp)
                            ) {
                                Text(ch.name, color = if (isFoc) Color.Black else Color.White, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, fontSize = 18.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            LaunchedEffect(Unit) {
                if (deviceLayoutMode == "TV") runCatching { channelListFocusRequester.requestFocus() }
            }
        }

        // LOADING
        if (playbackState == Player.STATE_BUFFERING) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).zIndex(3f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonGreen, strokeWidth = 4.dp)
            }
        }
        
        // ERROR
        if (playbackError != null) {
            Box(Modifier.fillMaxSize().background(Color.Black).zIndex(4f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(playbackError!!, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(24.dp))
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { exoPlayer?.prepare(); exoPlayer?.play() }, colors = ButtonDefaults.buttonColors(NeonGreen)) {
                        Text("TENTAR NOVAMENTE", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun Player.selectedFormat(trackType: Int): Format? {
    currentTracks.groups.forEach { group ->
        if (group.type == trackType) {
            for (i in 0 until group.length) {
                if (group.isTrackSelected(i)) return group.getTrackFormat(i)
            }
        }
    }
    return null
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}
