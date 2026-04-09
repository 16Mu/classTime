package com.wind.ggbond.classtime.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.GifDecoder
import coil3.request.crossfade
import com.wind.ggbond.classtime.R

@Composable
fun GifBackgroundPlayer(
    gifUri: Uri,
    modifier: Modifier = Modifier,
    dimAmount: Float = 0f,
    contentScale: ContentScale = ContentScale.Crop,
    onLoading: (() -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    onError: ((Throwable) -> Unit)? = null
) {
    Box(modifier = modifier) {
        val imageLoader = rememberGifImageLoader()
        var hasError by remember { mutableStateOf(false) }

        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray)
            )
        } else {
            AsyncImage(
                model = gifUri,
                contentDescription = stringResource(R.string.desc_info),
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                imageLoader = imageLoader,
                onSuccess = { onSuccess?.invoke() },
                onError = {
                    hasError = true
                    onError?.invoke(it.result.throwable)
                },
                onLoading = { onLoading?.invoke() }
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
