package com.wind.ggbond.classtime.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.wind.ggbond.classtime.MainActivity

@Composable
fun WidgetEmptyState(
    message: String,
    primaryFontSize: Float = 20f,
    secondaryFontSize: Float = 12f,
    spacerHeight: Float = 4f,
    verticalPadding: Float = 0f,
    clickable: Boolean = true
) {
    val modifier = GlanceModifier.fillMaxSize().let { mod ->
        if (verticalPadding > 0f) mod.padding(vertical = verticalPadding.dp) else mod
    }.let { mod ->
        if (clickable) mod.clickable(actionStartActivity<MainActivity>()) else mod
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "- -",
                style = TextStyle(
                    fontSize = primaryFontSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = dayNightColorProvider(day = WidgetColors.emptyPrimaryDay, night = WidgetColors.emptyPrimaryNight)
                )
            )
            Spacer(modifier = GlanceModifier.height(spacerHeight.dp))
            Text(
                text = message,
                style = TextStyle(
                    fontSize = secondaryFontSize.sp,
                    color = dayNightColorProvider(day = WidgetColors.emptySecondaryDay, night = WidgetColors.emptySecondaryNight)
                )
            )
        }
    }
}
