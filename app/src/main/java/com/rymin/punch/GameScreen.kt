package com.rymin.punch

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.random.Random

data class Particle(
    val id: Int,
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val color: Color,
    val createdAt: Long
)

@Composable
fun GameScreen(onLeaderboardUpdated: () -> Unit = {}) {
    val viewModel = remember { GameViewModel() }
    val gameState by viewModel.gameState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startGame()
    }

    PlayScreen(
        gameState = gameState,
        onDragStart = viewModel::onDragStart,
        onDragUpdate = viewModel::onDragUpdate,
        onPunchHit = viewModel::onPunch,
        onRestart = {
            viewModel.resetGame()
            viewModel.startGame()
        },
        onLeaderboardUpdated = onLeaderboardUpdated
    )
}

@Composable
fun PlayScreen(
    gameState: GameState,
    onDragStart: () -> Unit,
    onDragUpdate: (Float) -> Unit,
    onPunchHit: (Float, Float) -> Unit,
    onRestart: () -> Unit,
    onLeaderboardUpdated: () -> Unit
) {
    var gloveOffset by remember { mutableStateOf(Offset.Zero) }
    var isDraggingGlove by remember { mutableStateOf(false) }
    var gloveRotation by remember { mutableStateOf(0f) }
    var shakeIntensity by remember { mutableStateOf(0f) }
    var particles by remember { mutableStateOf<List<Particle>>(emptyList()) }
    var dragSpeed by remember { mutableStateOf(0f) }

    // Particle animation
    LaunchedEffect(particles) {
        if (particles.isNotEmpty()) {
            kotlinx.coroutines.delay(16)
            val currentTime = System.currentTimeMillis()
            particles = particles.filter { currentTime - it.createdAt < 500 }
        }
    }

    // Shake intensity decay
    LaunchedEffect(shakeIntensity) {
        if (shakeIntensity > 0f) {
            kotlinx.coroutines.delay(50)
            shakeIntensity = (shakeIntensity - 0.1f).coerceAtLeast(0f)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
    ) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        
        // Hammer base position (center of left half)
        val hammerBaseX = screenWidth * 0.25f
        val hammerBaseY = screenHeight * 0.5f
        val hammerWidth = 200f
        val hammerHeight = 300f

        // Draw particles (Unity-style with size variation and fade)
        particles.forEach { particle ->
            val elapsed = System.currentTimeMillis() - particle.createdAt
            if (elapsed >= 0) {
                val progress = (elapsed / 500f).coerceIn(0f, 1f)
                val alpha = (1f - progress).coerceIn(0f, 1f)

                // Size shrinks over time
                val size = (12.dp.value * (1f - progress * 0.5f)).coerceAtLeast(2f)

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (particle.startX + particle.velocityX * progress).roundToInt(),
                                (particle.startY + particle.velocityY * progress).roundToInt()
                            )
                        }
                        .size(size.dp)
                        .background(
                            particle.color.copy(alpha = alpha),
                            shape = CircleShape
                        )
                )
            }
        }
        // Timer at top
        Text(
            text = "⏱ ${gameState.timeRemaining}s",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = if (gameState.timeRemaining <= 3) Color.Red else Color(0xFFf39c12),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
        )

        // Punch Machine (behind glove)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 100.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_punch_machine),
                contentDescription = "Punch Machine",
                modifier = Modifier
                    .width(300.dp)
                    .height(600.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Glove
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Gauge
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SPEED",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .height(40.dp)
                                .background(Color(0xFF16213e), shape = MaterialTheme.shapes.medium)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((dragSpeed / 100f).coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(
                                        Color(0xFFf39c12),
                                        shape = MaterialTheme.shapes.medium
                                    )
                            )

                            Text(
                                text = "${dragSpeed.toInt()}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    // Hammer
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    gloveOffset.x.roundToInt(),
                                    gloveOffset.y.roundToInt()
                                )
                            }
                            .width(200.dp)
                            .height(300.dp)
                            .scale(1f + shakeIntensity * 0.2f)
                            .rotate(gloveRotation)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        isDraggingGlove = true
                                        gloveOffset = Offset.Zero
                                        gloveRotation = 0f
                                        onDragStart()
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()

                                        // Calculate drag speed
                                        val currentDragSpeed = kotlin.math.sqrt(
                                            dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y
                                        )
                                        dragSpeed = currentDragSpeed
                                        shakeIntensity = (currentDragSpeed / 50f).coerceIn(0f, 1f)
                                        onDragUpdate(currentDragSpeed)

                                        // Create trail particles at screen center
                                        if (currentDragSpeed > 5f) {
                                            // Trail at center of screen
                                            val trailX = screenWidth * 0.5f + gloveOffset.x * 0.3f
                                            val trailY = screenHeight * 0.5f + gloveOffset.y * 0.3f

                                            val newParticles = (0..2).map {
                                                Particle(
                                                    id = Random.nextInt(),
                                                    startX = trailX + Random.nextFloat() * 30f - 15f,
                                                    startY = trailY + Random.nextFloat() * 30f - 15f,
                                                    velocityX = -dragAmount.x * 1.5f + Random.nextFloat() * 60f - 30f,
                                                    velocityY = -dragAmount.y * 1.5f + Random.nextFloat() * 60f - 30f,
                                                    color = Color(0xFFf39c12),
                                                    createdAt = System.currentTimeMillis()
                                                )
                                            }
                                            particles = particles + newParticles
                                        }

                                        // Update hammer position (allow moving across the screen)
                                        val newOffset = Offset(
                                            (gloveOffset.x + dragAmount.x).coerceIn(-300f, screenWidth * 0.4f),
                                            (gloveOffset.y + dragAmount.y).coerceIn(-screenHeight * 0.3f, screenHeight * 0.3f)
                                        )
                                        gloveOffset = newOffset

                                        // Calculate rotation: 0 to 90 degrees based on X position
                                        val maxDistance = screenWidth * 0.4f
                                        val progress = (newOffset.x / maxDistance).coerceIn(0f, 1f)
                                        gloveRotation = progress * 90f
                                    },
                                    onDragEnd = {
                                        isDraggingGlove = false

                                        // Check if glove hit the punch machine target
                                        // Target is on the right side of screen
                                        val hitThreshold = screenWidth * 0.3f
                                        if (gloveOffset.x > hitThreshold) {
                                            // Calculate accuracy based on Y position (center is best)
                                            val maxYOffset = screenHeight * 0.3f
                                            val accuracy = 1f - (abs(gloveOffset.y) / maxYOffset).coerceIn(0f, 1f)

                                            // Create massive impact explosion particles (Unity style)
                                            // Impact at punch machine target (right side of screen)
                                            val impactX = screenWidth * 0.75f
                                            val impactY = screenHeight * 0.5f

                                            // Primary explosion - 80 particles
                                            val impactParticles = (0..80).map { i ->
                                                val angle = (i * 360.0 / 80.0) * Math.PI / 180.0
                                                val speed = Random.nextFloat() * 400f + 150f
                                                Particle(
                                                    id = Random.nextInt(),
                                                    startX = impactX + Random.nextFloat() * 40f - 20f,
                                                    startY = impactY + Random.nextFloat() * 40f - 20f,
                                                    velocityX = (Math.cos(angle) * speed).toFloat(),
                                                    velocityY = (Math.sin(angle) * speed).toFloat(),
                                                    color = when {
                                                        i % 4 == 0 -> Color(0xFFff6b6b) // Red
                                                        i % 4 == 1 -> Color(0xFFfeca57) // Yellow
                                                        i % 4 == 2 -> Color(0xFFf39c12) // Orange
                                                        else -> Color.White
                                                    },
                                                    createdAt = System.currentTimeMillis()
                                                )
                                            }

                                            // Secondary wave - 50 particles
                                            val secondaryParticles = (0..50).map {
                                                Particle(
                                                    id = Random.nextInt(),
                                                    startX = impactX,
                                                    startY = impactY,
                                                    velocityX = Random.nextFloat() * 600f - 300f,
                                                    velocityY = Random.nextFloat() * 600f - 300f,
                                                    color = Color(0xFFff9ff3),
                                                    createdAt = System.currentTimeMillis() + 30
                                                )
                                            }

                                            // Third wave - sparkles going up
                                            val sparkleParticles = (0..30).map {
                                                Particle(
                                                    id = Random.nextInt(),
                                                    startX = impactX + Random.nextFloat() * 100f - 50f,
                                                    startY = impactY,
                                                    velocityX = Random.nextFloat() * 100f - 50f,
                                                    velocityY = -Random.nextFloat() * 500f - 200f, // Going up
                                                    color = Color(0xFFFFD700), // Gold
                                                    createdAt = System.currentTimeMillis() + 60
                                                )
                                            }

                                            particles = particles + impactParticles + secondaryParticles + sparkleParticles

                                            onPunchHit(accuracy, dragSpeed)
                                        }

                                        gloveOffset = Offset.Zero
                                        gloveRotation = 0f
                                        shakeIntensity = 0f
                                        dragSpeed = 0f
                                    }
                                )
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_hammer),
                            contentDescription = "Hammer",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Text(
                        text = if (isDraggingGlove) "🔥 SHAKE IT!" else "👆 DRAG TO HIT!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Right side - Empty space (punch machine is in background)
            Spacer(modifier = Modifier.weight(1f))
        }

        // Score display overlay (shows on RESULT phase)
        if (gameState.gamePhase == GamePhase.RESULT) {
            ScoreOverlay(
                score = gameState.score,
                onRestart = onRestart,
                onLeaderboardUpdated = onLeaderboardUpdated
            )
        }
    }
}
