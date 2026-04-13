package com.wind.ggbond.classtime.ui.screen.main.components.grid

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.FastOutSlowInEasing

internal const val COMPACT_ANIM_DURATION_MS = 220
internal const val MAX_DAY_STAGGER_INDEX = 5
internal const val DAY_STAGGER_DELAY_MS = 60
internal const val MAX_SECTION_STAGGER_INDEX = 5
internal const val SECTION_STAGGER_DELAY_MS = 70
internal const val MAX_STAGGER_DELAY_MS = MAX_SECTION_STAGGER_INDEX * SECTION_STAGGER_DELAY_MS
internal const val TOTAL_COMPACT_ANIM_MS = COMPACT_ANIM_DURATION_MS + MAX_STAGGER_DELAY_MS

@androidx.compose.runtime.Stable
internal fun staggerProgress(
    linearProgress: Float,
    delayMs: Int,
    durationMs: Int = COMPACT_ANIM_DURATION_MS,
    totalMs: Int = TOTAL_COMPACT_ANIM_MS
): Float {
    val rawProgress = ((linearProgress * totalMs - delayMs) / durationMs).coerceIn(0f, 1f)
    return FastOutSlowInEasing.transform(rawProgress)
}

@androidx.compose.runtime.Stable
internal fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

@androidx.compose.runtime.Stable
internal fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    return Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction
    )
}
