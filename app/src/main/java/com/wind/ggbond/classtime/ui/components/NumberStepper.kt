package com.wind.ggbond.classtime.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wind.ggbond.classtime.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..Constants.Course.MAX_MORNING_SECTION_COUNT,
    label: String = ""
) {
    val haptic = LocalHapticFeedback.current
    val isAtMin = value <= range.first
    val isAtMax = value >= range.last

    var scaleTarget by remember { mutableFloatStateOf(1f) }
    val scale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = tween(durationMillis = 100),
        label = "number_scale"
    )

    LaunchedEffect(value) {
        scaleTarget = 1.15f
        scaleTarget = 1f
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(
            text = "−",
            enabled = !isAtMin,
            isIncrement = false,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onValueChange((value - 1).coerceIn(range))
            },
            onLongClick = {
                var v = value
                while (v > range.first) {
                    v = (v - 1).coerceIn(range)
                    onValueChange(v)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    delay(120)
                }
            }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            )
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                )
            }
        }

        StepperButton(
            text = "+",
            enabled = !isAtMax,
            isIncrement = true,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onValueChange((value + 1).coerceIn(range))
            },
            onLongClick = {
                var v = value
                while (v < range.last) {
                    v = (v + 1).coerceIn(range)
                    onValueChange(v)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    delay(120)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepperButton(
    text: String,
    enabled: Boolean,
    isIncrement: Boolean,
    onClick: () -> Unit,
    onLongClick: suspend () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    Surface(
        modifier = Modifier
            .size(48.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { if (enabled) onClick() },
                onLongClick = {
                    if (enabled) {
                        longPressJob?.cancel()
                        longPressJob = coroutineScope.launch { onLongClick() }
                    }
                }
            ),
        shape = CircleShape,
        color = if (isIncrement) {
            if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (!isIncrement && enabled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        } else if (!isIncrement && !enabled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        } else null
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = when {
                    isIncrement && enabled -> MaterialTheme.colorScheme.onPrimary
                    isIncrement && !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    !isIncrement && enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            longPressJob?.cancel()
        }
    }
}
