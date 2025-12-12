package com.rymin.punch

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ln

// Format score: 1000 displays as 999.99
private fun formatScore(score: Double): String {
    val displayValue = if (score >= 1000.0) 999.99 else score
    return String.format("%.2f", displayValue)
}

@Composable
fun ResultScreen(score: Double, onRestart: () -> Unit) {
    var displayScore by remember { mutableStateOf(0.0) }
    var scale by remember { mutableStateOf(0f) }

    // Logarithmic score animation
    LaunchedEffect(score) {
        val duration = 1500L
        val startTime = System.currentTimeMillis()

        while (displayScore < score) {
            val elapsed = System.currentTimeMillis() - startTime
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

            // Logarithmic curve: fast start, slow end
            val logProgress = if (progress > 0) {
                (ln(1 + progress * 9.0) / ln(10.0)).toFloat()
            } else {
                0f
            }

            displayScore = (score * logProgress).coerceAtMost(score)

            kotlinx.coroutines.delay(16)
        }
        displayScore = score
    }

    // Scale animation
    LaunchedEffect(Unit) {
        val animation = tween<Float>(
            durationMillis = 800,
            easing = {
                // Bounce effect
                val c4 = (2 * Math.PI) / 3
                if (it == 0f || it == 1f) {
                    it
                } else if (it < 0.5f) {
                    -(Math.pow(2.0, 20.0 * it - 10.0) * Math.sin((20.0 * it - 11.125) * c4)).toFloat() / 2
                } else {
                    (Math.pow(2.0, -20.0 * it + 10.0) * Math.sin((20.0 * it - 11.125) * c4)).toFloat() / 2 + 1
                }
            }
        )

        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = animation
        ) { value, _ ->
            scale = value
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
            .clickable { onRestart() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Text(
                text = "🏆 YOUR SCORE 🏆",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.scale(scale)
            )

            Text(
                text = formatScore(displayScore),
                fontSize = 120.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFf39c12),
                modifier = Modifier.scale(scale)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Blink animation for restart text
            val alpha by rememberInfiniteTransition(label = "blink").animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "blink"
            )

            Text(
                text = "TAP TO RESTART",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF95a5a6).copy(alpha = alpha)
            )
        }
    }
}
