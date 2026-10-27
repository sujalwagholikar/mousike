package com.mousike.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mousike.app.ui.theme.MousikeGreen

@Composable
fun VisualizerBars(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
    barSpacing: Dp = 2.dp,
    maxHeight: Dp = 24.dp,
    activeColor: Color = MousikeGreen
) {
    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(barSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val displayBars = amplitudes.take(16)
        displayBars.forEach { amp ->
            val animatedHeight by animateFloatAsState(
                targetValue = amp.coerceIn(0.12f, 1f),
                animationSpec = tween(durationMillis = 80),
                label = "bar_height"
            )
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(fraction = animatedHeight)
                    .background(activeColor, RoundedCornerShape(2.dp))
            )
        }
    }
}
