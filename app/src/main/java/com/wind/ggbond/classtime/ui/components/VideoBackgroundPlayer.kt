package com.wind.ggbond.classtime.ui.components

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun VideoBackgroundPlayer(
    videoUri: Uri,
    modifier: Modifier = Modifier,
    dimAmount: Float = 0f,
    isPlaying: Boolean = true,
    volume: Float = 0f,
    blurRadius: Float = 0f,
    onPlayerReady: (() -> Unit)? = null,
    onPlayerError: ((Exception) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentOnPlayerReady by rememberUpdatedState(onPlayerReady)
    val currentOnPlayerError by rememberUpdatedState(onPlayerError)

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(videoUri) {
        exoPlayer?.release()
        exoPlayer = null

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (playing) currentOnPlayerReady?.invoke()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                currentOnPlayerError?.invoke(Exception(error.message))
            }
        }

        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            this.volume = volume
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = currentIsPlaying
            addListener(listener)
        }
        exoPlayer = player
    }

    LaunchedEffect(isPlaying) {
        exoPlayer?.playWhenReady = isPlaying
    }

    LaunchedEffect(volume) {
        exoPlayer?.volume = volume
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer?.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (currentIsPlaying) exoPlayer?.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerViewRef?.player = null
            playerViewRef = null
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    val blurRenderEffect = if (blurRadius > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        android.graphics.RenderEffect
            .createBlurEffect(blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP)
    } else null

    Box(modifier = modifier.then(
        if (blurRenderEffect != null) {
            Modifier.graphicsLayer { renderEffect = blurRenderEffect }
        } else {
            Modifier
        }
    )) {
        exoPlayer?.let { player ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                update = { view ->
                    view.player = player
                    playerViewRef = view
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (dimAmount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAmount))
            )
        }
    }
}

@Composable
fun VideoBackgroundWithLoader(
    videoUri: Uri,
    modifier: Modifier = Modifier,
    dimAmount: Float = 0f,
    isPlaying: Boolean = true,
    content: @Composable () -> Unit = {}
) {
    var isVideoReady by remember(videoUri) { mutableStateOf(false) }
    var hasError by remember(videoUri) { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (!hasError) {
            VideoBackgroundPlayer(
                videoUri = videoUri,
                modifier = Modifier.matchParentSize(),
                dimAmount = dimAmount,
                isPlaying = isPlaying,
                onPlayerReady = { isVideoReady = true },
                onPlayerError = { hasError = true }
            )

            if (!isVideoReady) {
                BackgroundLoadingAnimation(
                    modifier = Modifier.matchParentSize()
                )
            }
        } else {
            BackgroundErrorPlaceholder(
                modifier = Modifier.matchParentSize(),
                message = "视频加载失败"
            )
        }

        content()
    }
}
