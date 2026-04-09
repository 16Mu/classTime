package com.wind.ggbond.classtime.ui.components

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.glassModifier(
    alpha: Float = 0.8f,
    tintColor: Color = Color.White,
    shape: Shape? = null,
    borderWidth: Dp = 0.5.dp,
    borderColor: Color = Color.White.copy(alpha = 0.3f),
    blurEnabled: Boolean = true,
    blurRadius: Float = 35f
): Modifier {
    val effectiveAlpha = if (blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0f) {
        (alpha * 0.6f).coerceIn(0f, 1f)
    } else {
        alpha
    }

    return this.then(
        Modifier.drawBehind {
            val outline = shape?.createOutline(size, layoutDirection, this)

            when (outline) {
                is androidx.compose.ui.graphics.Outline.Rounded -> {
                    drawRoundRect(tintColor.copy(alpha = effectiveAlpha))
                }
                else -> {
                    drawRect(tintColor.copy(alpha = effectiveAlpha))
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
    )
}
