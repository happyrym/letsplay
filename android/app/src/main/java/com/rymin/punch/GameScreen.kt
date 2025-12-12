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

// Random weapon types
enum class WeaponType(val drawableRes: Int, val displayName: String) {
    // Regular
    BOXING_GLOVE(R.drawable.ic_boxing_glove, "GLOVE"),
    FIST(R.drawable.ic_fist, "FIST"),
    // Event - Christmas
    SANTA_GLOVE(R.drawable.ic_santa_glove, "SANTA"),
    REINDEER_HOOF(R.drawable.ic_reindeer_hoof, "RUDOLPH"),
    // Event - Animals
    DOG_PAW(R.drawable.ic_dog_paw, "DOGGY"),
    CAT_PAW(R.drawable.ic_cat_paw, "KITTY")
}

@Composable
fun GameScreen(
    onLeaderboardUpdated: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val viewModel = remember { GameViewModel() }
    val gameState by viewModel.gameState.collectAsState()

    // Random weapon for each game
    var currentWeapon by remember { mutableStateOf(WeaponType.values().random()) }

    LaunchedEffect(Unit) {
        viewModel.startGame()
    }

    PlayScreen(
        gameState = gameState,
        currentWeaponType = currentWeapon,
        onDragStart = viewModel::onDragStart,
        onDragUpdate = viewModel::onDragUpdate,
        onPunchHit = viewModel::onPunch,
        onRestart = {
            // Change weapon on restart
            currentWeapon = WeaponType.values().random()
            viewModel.resetGame()
            viewModel.startGame()
        },
        onLeaderboardUpdated = onLeaderboardUpdated
    )
}

@Composable
fun PlayScreen(
    gameState: GameState,
    currentWeaponType: WeaponType,
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

        // Speed Gauge (top left)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 32.dp, top = 100.dp)
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

        // Instruction text (bottom left)
        Text(
            text = if (isDraggingGlove) "🔥 ${currentWeaponType.displayName}!" else "👆 DRAG TO HIT!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 50.dp)
        )

        // Hammer/Glove - absolute positioned, draggable
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (hammerBaseX + gloveOffset.x - hammerWidth / 2).roundToInt(),
                        (hammerBaseY + gloveOffset.y - hammerHeight / 2).roundToInt()
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

                            // Create trail particles following the glove
                            if (currentDragSpeed > 5f) {
                                val gloveScreenX = hammerBaseX + gloveOffset.x + hammerWidth / 2
                                val gloveScreenY = hammerBaseY + gloveOffset.y

                                val newParticles = (0..2).map {
                                    Particle(
                                        id = Random.nextInt(),
                                        startX = gloveScreenX + Random.nextFloat() * 20f - 10f,
                                        startY = gloveScreenY + Random.nextFloat() * 40f - 20f,
                                        velocityX = -dragAmount.x * 2f + Random.nextFloat() * 40f - 20f,
                                        velocityY = -dragAmount.y * 2f + Random.nextFloat() * 40f - 20f,
                                        color = Color(0xFFf39c12),
                                        createdAt = System.currentTimeMillis()
                                    )
                                }
                                particles = particles + newParticles
                            }

                            // Update position
                            val newOffset = Offset(
                                (gloveOffset.x + dragAmount.x).coerceIn(-hammerBaseX + 100f, screenWidth * 0.5f),
                                (gloveOffset.y + dragAmount.y).coerceIn(-screenHeight * 0.4f, screenHeight * 0.4f)
                            )
                            gloveOffset = newOffset

                            // Calculate rotation based on X movement
                            val maxDistance = screenWidth * 0.5f
                            val progress = (newOffset.x / maxDistance).coerceIn(0f, 1f)
                            gloveRotation = progress * 45f
                        },
                        onDragEnd = {
                            isDraggingGlove = false

                            // Hit detection
                            val hitThreshold = screenWidth * 0.25f
                            if (gloveOffset.x > hitThreshold) {
                                val maxYOffset = screenHeight * 0.4f
                                val accuracy = 1f - (abs(gloveOffset.y) / maxYOffset).coerceIn(0f, 1f)

                                // Impact at punch machine
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
                                            i % 4 == 0 -> Color(0xFFff6b6b)
                                            i % 4 == 1 -> Color(0xFFfeca57)
                                            i % 4 == 2 -> Color(0xFFf39c12)
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

                                // Sparkles going up
                                val sparkleParticles = (0..30).map {
                                    Particle(
                                        id = Random.nextInt(),
                                        startX = impactX + Random.nextFloat() * 100f - 50f,
                                        startY = impactY,
                                        velocityX = Random.nextFloat() * 100f - 50f,
                                        velocityY = -Random.nextFloat() * 500f - 200f,
                                        color = Color(0xFFFFD700),
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
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = currentWeaponType.drawableRes),
                contentDescription = currentWeaponType.displayName,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Score display overlay (shows on RESULT phase)
        if (gameState.gamePhase == GamePhase.RESULT) {
            ScoreOverlay(
                score = gameState.score,
                weaponType = currentWeaponType.name,
                onRestart = onRestart,
                onLeaderboardUpdated = onLeaderboardUpdated
            )
        }
    }
}
