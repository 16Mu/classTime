// [Monet] 已排查：该文件不涉及课程颜色渲染，无需适配
package com.wind.ggbond.classtime.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 极小的自动更新提示组件
 * 
 * 特点：
 * - 60x60dp圆形悬浮窗
 * - 悬浮在屏幕右下角
 * - 更新中：旋转的刷新图标
 * - 成功：绿色对勾，5秒后消失
 * - 失败：红色错误图标，可点击查看详情
 */
@Composable
fun CompactUpdateFloatingView(
    isUpdating: Boolean,
    updateResult: Pair<Boolean, String>?,
    onDismiss: () -> Unit
) {
    // 自动消失倒计时
    LaunchedEffect(updateResult) {
        if (updateResult != null) {
            delay(5000)  // 5秒后自动消失
            onDismiss()
        }
    }
    
    // 旋转动画
    val infiniteTransition = rememberInfiniteTransition(label = "update")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // 缩放动画
    val scale by animateFloatAsState(
        targetValue = if (isUpdating || updateResult != null) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    // 仅在有内容显示时渲染
    if (scale > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Surface(
                modifier = Modifier
                    .size(60.dp)
                    .alpha(scale),
                shape = CircleShape,
                color = when {
                    isUpdating -> MaterialTheme.colorScheme.primaryContainer
                    updateResult?.first == true -> Color(0xFF4CAF50).copy(alpha = 0.9f)
                    updateResult?.first == false -> Color(0xFFF44336).copy(alpha = 0.9f)
                    else -> MaterialTheme.colorScheme.surface
                },
                shadowElevation = 8.dp,
                tonalElevation = 2.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isUpdating -> {
                            // 更新中：旋转的刷新图标
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "更新中",
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer(rotationZ = rotation),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        updateResult?.first == true -> {
                            // 成功：绿色对勾
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "更新成功",
                                modifier = Modifier.size(32.dp),
                                tint = Color.White
                            )
                        }
                        updateResult?.first == false -> {
                            // 失败：红色错误图标
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "更新失败",
                                modifier = Modifier.size(32.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}











