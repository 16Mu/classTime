// [Monet] 已排查：该文件不涉及课程颜色渲染，无需适配
package com.wind.ggbond.classtime.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.glassModifier(
    alpha: Float = 0.8f,
    tintColor: Color = Color.White,
    shape: Shape? = null,
    borderWidth: Dp = 0.5.dp,
    borderColor: Color = Color.White.copy(alpha = 0.3f),
    blurEnabled: Boolean = true
): Modifier {
    // Apply Gaussian blur for API 31+ when blurEnabled is true
    val blurModifier = if (blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            renderEffect = RenderEffect.createBlurEffect(
                35f, // radiusX: blur radius in pixels (25-50 range)
                35f, // radiusY: blur radius in pixels (25-50 range)
                Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    } else {
        Modifier
    }
    
    // Apply semi-transparent overlay and border
    val overlayModifier = Modifier.drawBehind {
        val outline = shape?.createOutline(size, layoutDirection, this)
        
        when (outline) {
            is androidx.compose.ui.graphics.Outline.Rounded -> {
                drawRoundRect(tintColor.copy(alpha = alpha))
            }
            else -> {
                drawRect(tintColor.copy(alpha = alpha))
            }
        }
        
        if (borderWidth.value > 0) {
            when (outline) {
                is androidx.compose.ui.graphics.Outline.Rectangle -> {
                    drawRect(color = borderColor, style = Stroke(width = borderWidth.toPx()))
                }
                is androidx.compose.ui.graphics.Outline.Rounded -> {
                    val rrect = outline.roundRect
                    drawRoundRect(
                        color = borderColor,
                        style = Stroke(width = borderWidth.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            rrect.topLeftCornerRadius.x,
                            rrect.topLeftCornerRadius.y
                        )
                    )
                }
                else -> {
                    drawRect(color = borderColor, style = Stroke(width = borderWidth.toPx()))
                }
            }
        }
    }
    
    return this.then(blurModifier).then(overlayModifier)
}
