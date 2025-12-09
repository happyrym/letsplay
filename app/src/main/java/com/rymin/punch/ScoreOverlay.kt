package com.rymin.punch

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
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

// Format score: 1000 displays as 999.99
private fun formatScore(score: Double): String {
    val displayValue = if (score >= 1000.0) 999.99 else score
    return String.format("%.2f", displayValue)
}

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
                            onLeaderboardUpdated()
                            phase = ScoreDisplayPhase.READY_TO_RESTART
                        }
                    },
                    onSkip = {
                        phase = ScoreDisplayPhase.READY_TO_RESTART
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
            text = formatScore(displayScore),
            fontSize = 120.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
    }
}

// 3x3 letter pad layout (like old phone T9)
private val letterPadKeys = listOf(
    listOf("ABC", "DEF", "GHI"),
    listOf("JKL", "MNO", "PRS"),
    listOf("TUV", "WXY", "Z_")
)

@Composable
private fun NameInputContent(
    score: Double,
    playerName: String,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    animatedScale: Float
) {
    // Track which key is selected and current index within that key
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var charIndex by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .scale(animatedScale)
            .padding(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side - Score display
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "🏆 TOP 10! 🏆",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFf39c12)
            )

            Text(
                text = formatScore(score),
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            // Name display
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .background(Color(0xFF2C3E50), RoundedCornerShape(8.dp))
                    .border(3.dp, Color(0xFFf39c12), RoundedCornerShape(8.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (playerName.isEmpty()) {
                    Text(
                        text = "YOUR NAME",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                } else {
                    Text(
                        text = playerName,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7f8c8d)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(50.dp).width(120.dp)
                ) {
                    Text("SKIP", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = onSubmit,
                    enabled = playerName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF27ae60),
                        disabledContainerColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(50.dp).width(120.dp)
                ) {
                    Text("SAVE", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Right side - 3x3 Letter Pad
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 32.dp)
        ) {
            Text(
                text = "TAP TO SELECT",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f)
            )

            // 3x3 Grid
            letterPadKeys.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { key ->
                        LetterPadButton(
                            letters = key,
                            onClick = { selectedChar ->
                                if (playerName.length < 8) {
                                    onNameChange(playerName + selectedChar)
                                }
                            }
                        )
                    }
                }
            }

            // Delete button
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Button(
                    onClick = {
                        if (playerName.isNotEmpty()) {
                            onNameChange(playerName.dropLast(1))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe74c3c)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(width = 200.dp, height = 60.dp)
                ) {
                    Text("⌫ DEL", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = {
                        if (playerName.length < 8) {
                            onNameChange(playerName + " ")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3498db)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(width = 100.dp, height = 60.dp)
                ) {
                    Text("_", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LetterPadButton(
    letters: String,
    onClick: (Char) -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var showSelector by remember { mutableStateOf(false) }

    if (showSelector) {
        // Show character selector popup
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .background(Color(0xFF34495e), RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            letters.replace("_", " ").forEach { char ->
                Button(
                    onClick = {
                        onClick(char)
                        showSelector = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27ae60)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(50.dp)
                ) {
                    Text(
                        text = if (char == ' ') "_" else char.toString(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    } else {
        // Show main button
        Button(
            onClick = { showSelector = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFf39c12)),
            modifier = Modifier.size(100.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = letters.replace("_", ""),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
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
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.scale(animatedScale)
    ) {
        Text(
            text = "💥 SCORE 💥",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFf39c12)
        )

        Text(
            text = formatScore(score),
            fontSize = 120.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )

        // Retry button
        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF27ae60)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .padding(top = 24.dp)
                .height(70.dp)
                .width(280.dp)
        ) {
            Text(
                text = "🔄 RETRY",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
