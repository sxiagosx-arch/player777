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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
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
import kotlin.math.abs
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
    var isRemotePlayback by remember { mutableStateOf(false) }

    // METADATA
    var videoCodec by remember { mutableStateOf("—") }
    var audioCodec by remember { mutableStateOf("—") }
    var videoResolution by remember { mutableStateOf("—") }
    var videoBitrate by remember { mutableStateOf("—") }
    var bufferedSeconds by remember { mutableIntStateOf(0) }
    var rebufferCount by remember(channel.id) { mutableIntStateOf(0) }
    var hasBeenReady by remember(channel.id) { mutableStateOf(false) }

    // CONTROLES
    var showControls by remember { mutableStateOf(true) }
    var showChannelsList by remember { mutableStateOf(false) }
    var showTechInfo by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    
    // GESTURES HUD
    var gestureHUDText by remember { mutableStateOf("") }
    var gestureHUDProgress by remember { mutableStateOf(0f) }
    var showGestureHUD by remember { mutableStateOf(false) }
    
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var currentVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
    var isLocked by remember { mutableStateOf(false) }
    var scaleMode by remember { mutableIntStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var nextEpisode by remember { mutableStateOf<com.example.model.IPTVChannel?>(null) }
    var showAutoPlayCountdown by remember { mutableStateOf(false) }
    var autoPlayTimeLeft by remember { mutableIntStateOf(10) }
    var dismissNextPopup by remember { mutableStateOf(false) }

    // GESTURE DETECTOR - Otimizado para Celular
    val gestureDetector = remember {
        android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            var scrollYAccumulator = 0f
            override fun onDown(e: android.view.MotionEvent): Boolean { scrollYAccumulator = 0f; return true }
            override fun onScroll(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null || isLocked) return false
                val width = context.resources.displayMetrics.widthPixels
                val height = context.resources.displayMetrics.heightPixels
                if (abs(distanceY) > abs(distanceX)) {
                    showGestureHUD = true
                    if (e1.x < width / 2) {
                        val delta = distanceY / height
                        activity?.let { act ->
                            val attrs = act.window.attributes
                            val cur = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                            val new = (cur + delta).coerceIn(0.01f, 1.0f)
                            attrs.screenBrightness = new
                            act.window.attributes = attrs
                            gestureHUDText = "Brilho: ${(new * 100).toInt()}%"
                            gestureHUDProgress = new
                        }
                    } else {
                        scrollYAccumulator += distanceY
                        if (abs(scrollYAccumulator) > 40f) {
                            val dir = if (scrollYAccumulator > 0) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
                            val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            currentVolume = vol / maxVolume
                            gestureHUDProgress = currentVolume
                            gestureHUDText = "Volume: ${(gestureHUDProgress * 100).toInt()}%"
                            scrollYAccumulator = 0f
                        }
                    }
                } else if (totalDuration > 0) {
                    showGestureHUD = true
                    val delta = (-distanceX / width * totalDuration).toLong()
                    exoPlayer?.let { p ->
                        val target = (p.currentPosition + delta).coerceIn(0, totalDuration)
                        p.seekTo(target)
                        gestureHUDText = "Procurar: ${formatTime(target)}"
                        gestureHUDProgress = target.toFloat() / totalDuration
                    }
                }
                return true
            }
            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                if (showChannelsList || showTechInfo) {
                    showChannelsList = false
                    showTechInfo = false
                } else {
                    showControls = !showControls
                }
                return true
            }
        })
    }

    LaunchedEffect(channel.id) {
        showChannelsList = false
        showControls = true
        dismissNextPopup = false
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
                    hasBeenReady = true
                    playbackError = null
                    totalDuration = player.duration.takeIf { it > 0L } ?: 0L
                } else if (state == Player.STATE_BUFFERING && hasBeenReady) {
                    rebufferCount++
                    hasBeenReady = false
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = "Falha no carregamento. Tente novamente."
            }
            override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                isRemotePlayback = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
            }
        }
        player.addListener(listener)
        exoPlayer = player
        onDispose {
            player.removeListener(listener)
            val p = exoPlayer
            exoPlayer = null
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try { p?.stop(); session.release() } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    LaunchedEffect(playbackState) {
        if (playbackState == Player.STATE_ENDED && channel.type == "SERIES") {
            val currIndex = adjacentChannels.indexOfFirst { it.id == channel.id }
            if (currIndex != -1 && currIndex < adjacentChannels.size - 1) {
                nextEpisode = adjacentChannels[currIndex + 1]
                showAutoPlayCountdown = true
                autoPlayTimeLeft = 10
            }
        } else { showAutoPlayCountdown = false }
    }

    LaunchedEffect(showAutoPlayCountdown) {
        if (showAutoPlayCountdown) {
            while (autoPlayTimeLeft > 0) {
                delay(1000)
                autoPlayTimeLeft -= 1
            }
            if (autoPlayTimeLeft == 0 && nextEpisode != null) {
                showAutoPlayCountdown = false
                onChannelChange(nextEpisode!!)
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        while (exoPlayer != null) {
            delay(1000)
            exoPlayer?.let {
                currentPosition = it.currentPosition
                totalDuration = it.duration.takeIf { d -> d > 0L } ?: 0L
                bufferedSeconds = ((it.bufferedPosition - it.currentPosition).coerceAtLeast(0L) / 1000L).toInt()
                val v = it.selectedFormat(C.TRACK_TYPE_VIDEO)
                val a = it.selectedFormat(C.TRACK_TYPE_AUDIO)
                videoCodec = v?.codecs ?: v?.sampleMimeType ?: if (isRemotePlayback) "Remoto" else "..."
                audioCodec = a?.codecs ?: a?.sampleMimeType ?: "—"
                videoResolution = if (v != null && v.width > 0) "${v.width}x${v.height}" else "—"
                videoBitrate = v?.bitrate?.takeIf { b -> b > 0 }?.let { b -> String.format(Locale.ROOT, "%.1f Mbps", b / 1_000_000f) } ?: "—"
                if (totalDuration > 0) onSaveProgress(currentPosition, totalDuration)
            }
        }
    }

    LaunchedEffect(showControls, showChannelsList, showTechInfo) {
        if (showControls && !showChannelsList && !showTechInfo) {
            delay(8000)
            showControls = false
        }
    }
    
    LaunchedEffect(showGestureHUD) {
        if (showGestureHUD) {
            delay(2000)
            showGestureHUD = false
        }
    }

    BackHandler {
        when {
            showChannelsList -> showChannelsList = false
            showTechInfo -> showTechInfo = false
            showControls -> showControls = false
            else -> onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // VIDEO SURFACE
        AndroidView(
            modifier = Modifier.fillMaxSize().zIndex(0f).pointerInput(Unit) {
                detectTapGestures(onTap = { 
                    if (showChannelsList || showTechInfo) {
                        showChannelsList = false; showTechInfo = false
                    } else showControls = !showControls
                })
            }.pointerInteropFilter { gestureDetector.onTouchEvent(it) },
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

        // GESTURE HUD
        AnimatedVisibility(visible = showGestureHUD, modifier = Modifier.align(Alignment.Center).zIndex(1f)) {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(0.6f)).padding(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(gestureHUDText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { gestureHUDProgress }, modifier = Modifier.width(150.dp).height(6.dp).clip(RoundedCornerShape(3.dp)), color = NeonGreen)
                }
            }
        }

        // PLAYBACK CONTROLS
        AnimatedVisibility(
            visible = showControls && !showChannelsList && !showTechInfo,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.zIndex(2f)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
                Row(Modifier.align(Alignment.TopStart).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, Modifier.background(Color.Black.copy(0.5f), RoundedCornerShape(25.dp))) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = Color.White)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(channel.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (channel.epgTitle.isNotEmpty()) Text(channel.epgTitle, color = NeonGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Row(Modifier.align(Alignment.TopEnd).padding(24.dp)) {
                    IconButton(onClick = { showTechInfo = true }, Modifier.background(Color.Black.copy(0.5f), RoundedCornerShape(25.dp))) {
                        Icon(Icons.Rounded.Info, null, tint = Color.White)
                    }
                    if (channel.type == "LIVE" || channel.type == "SERIES") {
                        Spacer(Modifier.width(12.dp))
                        IconButton(onClick = { showChannelsList = true }, Modifier.background(Color.Black.copy(0.5f), RoundedCornerShape(25.dp))) {
                            Icon(Icons.Rounded.List, null, tint = Color.White)
                        }
                    }
                }

                Box(Modifier.align(Alignment.Center)) {
                    IconButton(
                        onClick = { exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() } },
                        modifier = Modifier.size(80.dp).background(NeonGreen, RoundedCornerShape(40.dp))
                    ) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(48.dp), tint = Color.Black)
                    }
                }

                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)) {
                    if (totalDuration > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(currentPosition), color = Color.White, fontSize = 12.sp)
                            Text(formatTime(totalDuration), color = Color.White, fontSize = 12.sp)
                        }
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { exoPlayer?.seekTo(it.toLong()) },
                            valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(Color.Red, RoundedCornerShape(5.dp)))
                            Spacer(Modifier.width(8.dp))
                            Text("AO VIVO", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        // LISTS OVERLAY
        AnimatedVisibility(
            visible = showChannelsList,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.zIndex(3f)
        ) {
            if (channel.type == "LIVE") {
                Box(Modifier.fillMaxHeight().width(320.dp).background(Color.Black.copy(alpha = 0.95f)).border(1.dp, NeonGreen)) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text("CANAIS", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Spacer(Modifier.height(16.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(adjacentChannels) { ch ->
                                val isSel = ch.id == channel.id
                                Box(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) NeonGreen.copy(0.25f) else Color.Transparent)
                                        .clickable { onChannelChange(ch); showChannelsList = false }
                                        .padding(14.dp)
                                ) {
                                    Text(ch.name, color = if (isSel) NeonGreen else Color.White, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, fontSize = 16.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            } else if (channel.type == "SERIES") {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.BottomCenter) {
                    Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.95f)).padding(20.dp)) {
                        Text("EPISÓDIOS", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            seriesSeasons.forEach { season ->
                                items(season.episodes) { ep ->
                                    val isSel = ep.id == channel.id
                                    Box(
                                        Modifier.width(150.dp).height(85.dp).clip(RoundedCornerShape(10.dp))
                                            .background(if (isSel) NeonGreen.copy(0.3f) else Color.DarkGray)
                                            .clickable { onChannelChange(ep); showChannelsList = false }
                                            .padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(ep.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }

        // TECH INFO
        AnimatedVisibility(
            visible = showTechInfo,
            enter = slideInHorizontally { it }, exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).zIndex(4f)
        ) {
            Box(Modifier.width(280.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.95f)).border(1.dp, NeonGreen).padding(20.dp)) {
                Column {
                    Text("DETALHES TÉCNICOS", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    HorizontalDivider(color = NeonGreen.copy(0.3f), modifier = Modifier.padding(vertical = 10.dp))
                    TechRow("Vídeo", videoCodec)
                    TechRow("Resolução", videoResolution)
                    TechRow("Bitrate", videoBitrate)
                    TechRow("Buffer", "${bufferedSeconds}s")
                    TechRow("Rebuffers", rebufferCount.toString())
                }
            }
        }

        // AUTO-PLAY
        AnimatedVisibility(visible = showAutoPlayCountdown, modifier = Modifier.align(Alignment.Center).zIndex(5f)) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(0.85f)).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PRÓXIMO EPISÓDIO", color = Color.Gray, fontSize = 14.sp)
                    Text(nextEpisode?.name ?: "", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Text("Iniciando em $autoPlayTimeLeft segundos...", color = NeonGreen, fontSize = 16.sp)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { showAutoPlayCountdown = false }, colors = ButtonDefaults.buttonColors(Color.DarkGray)) { Text("CANCELAR") }
                }
            }
        }

        // LOADING
        if (playbackState == Player.STATE_BUFFERING) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).zIndex(6f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonGreen, strokeWidth = 3.dp)
            }
        }
        
        // ERROR
        if (playbackError != null) {
            Box(Modifier.fillMaxSize().background(Color.Black).zIndex(7f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(playbackError!!, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(32.dp))
                    Button(onClick = { exoPlayer?.prepare(); exoPlayer?.play() }, colors = ButtonDefaults.buttonColors(NeonGreen)) {
                        Text("TENTAR NOVAMENTE", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TechRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
