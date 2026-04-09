package com.wind.ggbond.classtime.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BackgroundLoadingAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg_loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bg_alpha"
    )

    Box(
        modifier = modifier.background(Color.Black.copy(alpha = alpha))
    ) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center).size(48.dp),
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun BackgroundErrorPlaceholder(
    modifier: Modifier = Modifier,
    message: String = "加载失败"
) {
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
