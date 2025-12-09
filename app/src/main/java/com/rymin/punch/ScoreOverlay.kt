package com.rymin.punch

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rymin.punch.data.LeaderboardRepository
import kotlin.math.ln

enum class ScoreDisplayPhase {
    SHOWING_SCORE,
    INPUT_NAME,
    READY_TO_RESTART
}

@Composable
fun ScoreOverlay(
    score: Double,
    onRestart: () -> Unit,
    onLeaderboardUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val leaderboardRepo = remember { LeaderboardRepository(context) }
    val isTopScore = remember { leaderboardRepo.isTopScore(score) }

    var displayScore by remember { mutableStateOf(0.0) }
    val animatedScale = remember { Animatable(0f) }
    var blinkAlpha by remember { mutableStateOf(1f) }
    var phase by remember { mutableStateOf(ScoreDisplayPhase.SHOWING_SCORE) }
    var playerName by remember { mutableStateOf("") }

    // Logarithmic score animation
    LaunchedEffect(score) {
        displayScore = 0.0
        animatedScale.snapTo(0f)
        animatedScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )

        val duration = 2000L
        val startTime = System.currentTimeMillis()
        while (displayScore < score) {
            val elapsed = System.currentTimeMillis() - startTime
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

            val logProgress = if (progress > 0) {
                (ln(1 + progress * 9.0) / ln(10.0)).toFloat()
            } else {
                0f
            }
            displayScore = (score * logProgress).coerceAtMost(score)

            kotlinx.coroutines.delay(16)
        }
        displayScore = score

        // Wait 1 second after animation completes
        kotlinx.coroutines.delay(1000)

        // Move to name input if top score, otherwise ready to restart
        phase = if (isTopScore) {
            ScoreDisplayPhase.INPUT_NAME
        } else {
            ScoreDisplayPhase.READY_TO_RESTART
        }
    }

    // Blinking text animation
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            blinkAlpha = if (blinkAlpha == 1f) 0.3f else 1f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .pointerInput(Unit) {
                detectDragGestures { _, _ -> }
            },
        contentAlignment = Alignment.Center
    ) {
        when (phase) {
            ScoreDisplayPhase.SHOWING_SCORE -> {
                ShowingScoreContent(
                    displayScore = displayScore,
                    animatedScale = animatedScale.value
                )
            }
            ScoreDisplayPhase.INPUT_NAME -> {
                NameInputContent(
                    score = score,
                    playerName = playerName,
                    onNameChange = { playerName = it },
                    onSubmit = {
                        if (playerName.isNotBlank()) {
                            leaderboardRepo.addEntry(playerName, score)
                            phase = ScoreDisplayPhase.READY_TO_RESTART
                        }
                    },
                    animatedScale = animatedScale.value
                )
            }
            ScoreDisplayPhase.READY_TO_RESTART -> {
                ReadyToRestartContent(
                    score = score,
                    blinkAlpha = blinkAlpha,
                    onRestart = onRestart,
                    animatedScale = animatedScale.value
                )
            }
        }
    }
}

@Composable
private fun ShowingScoreContent(
    displayScore: Double,
    animatedScale: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.scale(animatedScale)
    ) {
        Text(
            text = "💥 SCORE 💥",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFf39c12)
        )

        Text(
            text = String.format("%.2f", displayScore),
            fontSize = 120.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
    }
}

@Composable
private fun NameInputContent(
    score: Double,
    playerName: String,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    animatedScale: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .scale(animatedScale)
            .padding(32.dp)
    ) {
        Text(
            text = "🏆 TOP 10! 🏆",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFf39c12)
        )

        Text(
            text = String.format("%.2f", score),
            fontSize = 80.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )

        Text(
            text = "ENTER YOUR NAME",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp)
        )

        BasicTextField(
            value = playerName,
            onValueChange = { if (it.length <= 10) onNameChange(it) },
            textStyle = TextStyle(
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            ),
            singleLine = true,
            modifier = Modifier
                .width(400.dp)
                .background(Color(0xFF2C3E50), RoundedCornerShape(8.dp))
                .border(3.dp, Color(0xFFf39c12), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (playerName.isEmpty()) {
                    Text(
                        text = "Your Name",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                }
                innerTextField()
            }
        }

        Button(
            onClick = onSubmit,
            enabled = playerName.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFf39c12),
                disabledContainerColor = Color.Gray
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .padding(top = 16.dp)
                .height(60.dp)
                .width(300.dp)
        ) {
            Text(
                text = "SUBMIT",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ReadyToRestartContent(
    score: Double,
    blinkAlpha: Float,
    onRestart: () -> Unit,
    animatedScale: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.scale(animatedScale)
    ) {
        Text(
            text = "💥 SCORE 💥",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFf39c12)
        )

        Text(
            text = String.format("%.2f", score),
            fontSize = 120.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )

        Text(
            text = "TAP TO RESTART",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = blinkAlpha),
            modifier = Modifier
                .padding(top = 32.dp)
                .pointerInput(Unit) {
                    detectTapGestures {
                        onRestart()
                    }
                }
        )
    }
}
