package com.iji.app.ui.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun RecognitionLoadingCard() {
    Column(Modifier.fillMaxWidth()) {
        OverlayBrandRow(badge = "识别中")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            contentAlignment = Alignment.Center,
        ) {
            RecognitionWave()
        }
        Text(
            text = "正在识别账单信息…",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = Color.Black.copy(alpha = 0.54f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RecognitionWave() {
    val transition = rememberInfiniteTransition(label = "recognitionWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        val middle = size.height / 2f
        val wavelength = size.width / 1.65f
        val step = 3.dp.toPx()

        fun wave(amplitude: Float, phaseOffset: Float) = Path().apply {
            var x = 0f
            moveTo(x, middle + sin(phaseOffset) * amplitude)
            while (x <= size.width) {
                val angle = x / wavelength * (2f * PI).toFloat() + phaseOffset
                lineTo(x, middle + sin(angle) * amplitude)
                x += step
            }
            lineTo(size.width, middle + sin(size.width / wavelength * (2f * PI).toFloat() + phaseOffset) * amplitude)
        }

        val style = Stroke(
            width = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawPath(
            path = wave(amplitude = 5.dp.toPx(), phaseOffset = -phase * 0.72f + 1.4f),
            color = OverlayPalette.Accent.copy(alpha = 0.22f),
            style = style,
        )
        drawPath(
            path = wave(amplitude = 8.dp.toPx(), phaseOffset = phase),
            color = OverlayPalette.Accent,
            style = style,
        )
    }
}
