// [Monet] 已排查：该文件不涉及课程颜色渲染，无需适配
package com.wind.ggbond.classtime.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.GifDecoder
import coil3.request.crossfade
import androidx.compose.ui.res.stringResource
import com.wind.ggbond.classtime.R

@Composable
fun GifBackgroundPlayer(
    gifUri: Uri,
    modifier: Modifier = Modifier,
    blurRadius: Float = 0f,
    dimAmount: Float = 0.4f,
    contentScale: ContentScale = ContentScale.Crop,
    onLoading: (() -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    onError: ((Throwable) -> Unit)? = null
) {
    Box(modifier = modifier) {
        val imageLoader = rememberGifImageLoader()
        
        AsyncImage(
            model = gifUri,
            contentDescription = stringResource(R.string.desc_info),
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            imageLoader = imageLoader,
            onSuccess = { onSuccess?.invoke() },
            onError = { onError?.invoke(it.result.throwable) },
            onLoading = { onLoading?.invoke() }
        )
        
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
fun GifBackgroundWithStatus(
    gifUri: Uri,
    modifier: Modifier = Modifier,
    blurRadius: Float = 0f,
    dimAmount: Float = 0.4f,
    content: @Composable () -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        if (!hasError) {
            GifBackgroundPlayer(
                gifUri = gifUri,
                modifier = Modifier.matchParentSize(),
                blurRadius = blurRadius,
                dimAmount = dimAmount,
                onLoading = { isLoading = true },
                onSuccess = { isLoading = false },
                onError = { 
                    hasError = true
                    isLoading = false
                }
            )
            
            if (isLoading) {
                LoadingAnimation(modifier = Modifier.matchParentSize())
            }
        } else {
            ErrorPlaceholder(
                modifier = Modifier.matchParentSize(),
                message = "GIF 加载失败"
            )
        }
        
        content()
    }
}

@Composable
private fun rememberGifImageLoader(): ImageLoader {
    val context = LocalContext.current
    
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
    
    DisposableEffect(imageLoader) {
        onDispose {
            imageLoader.shutdown()
        }
    }
    
    return imageLoader
}

@Composable
private fun LoadingAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = alpha))
    ) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center).size(48.dp),
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ErrorPlaceholder(modifier: Modifier = Modifier, message: String = "加载失败") {
    Box(
        modifier = modifier.background(Color.DarkGray)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = message,
            modifier = Modifier.align(Alignment.Center).size(48.dp),
            tint = Color.White.copy(alpha = 0.6f)
        )
    }
}
