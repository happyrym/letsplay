package com.rymin.punch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*

// Standard dartboard number sequence (clockwise from top)
val DART_NUMBERS = listOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)

// Dartboard colors
val DART_RED = Color(0xFFE63946)
val DART_GREEN = Color(0xFF2D6A4F)
val DART_BLACK = Color(0xFF1A1A1A)
val DART_CREAM = Color(0xFFF5E6D3)
val DART_WIRE = Color(0xFFC0C0C0)

// Dart game state
enum class DartGamePhase {
    READY,       // Before game starts
    PLAYING,     // Game in progress (10 seconds)
    THROWING,    // Dart is flying
    RESULT       // All 3 darts thrown or time up
}

data class DartScore(
    val baseNumber: Int,
    val multiplier: Int,
    val totalPoints: Int,
    val description: String
)

data class ThrownDart(
    val normalizedX: Float,
    val normalizedY: Float,
    val score: DartScore
)

data class SwipeData(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val velocity: Float,
    val angle: Float  // Radians, 0 = right, -PI/2 = up
)

// Particle system for hit effects
data class DartParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var maxLife: Float,
    var size: Float,
    var color: Color,
    var type: ParticleType = ParticleType.SPARK
)

enum class ParticleType {
    SPARK,      // Fast moving sparks
    GLOW,       // Glowing orbs
    STAR,       // Star burst
    SMOKE       // Slow moving smoke
}

@Composable
fun DartGameScreen(
    isTopDartScore: Boolean = true,
    onSaveScore: (String, Int) -> Unit = { _, _ -> },
    onLeaderboardUpdated: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    var gamePhase by remember { mutableStateOf(DartGamePhase.READY) }
    var timeRemaining by remember { mutableStateOf(10) }
    var dartsThrown by remember { mutableStateOf(0) }
    var totalScore by remember { mutableStateOf(0) }
    var thrownDarts by remember { mutableStateOf<List<ThrownDart>>(emptyList()) }

    // Swipe tracking
    var swipeStart by remember { mutableStateOf<Offset?>(null) }
    var currentSwipe by remember { mutableStateOf<Offset?>(null) }
    var swipeStartTime by remember { mutableStateOf(0L) }
    var isDraggingDart by remember { mutableStateOf(false) }
    var dartDragOffset by remember { mutableStateOf(Offset.Zero) }

    // Screen size for dart position calculation
    var screenSize by remember { mutableStateOf(Size.Zero) }

    // Dart animation
    var dartProgress by remember { mutableStateOf(0f) }
    var dartScale by remember { mutableStateOf(1f) }
    var landingX by remember { mutableStateOf(0f) }
    var landingY by remember { mutableStateOf(0f) }
    var lastScore by remember { mutableStateOf<DartScore?>(null) }
    var showScorePopup by remember { mutableStateOf(false) }

    // Particle effects
    var hitParticles by remember { mutableStateOf<List<DartParticle>>(emptyList()) }
    var trailParticles by remember { mutableStateOf<List<DartParticle>>(emptyList()) }

    // Score popup animation
    var scorePopupScale by remember { mutableStateOf(0f) }
    var scorePopupAlpha by remember { mutableStateOf(0f) }

    // Timer countdown
    LaunchedEffect(gamePhase) {
        if (gamePhase == DartGamePhase.PLAYING) {
            timeRemaining = 10
            while (timeRemaining > 0 && gamePhase == DartGamePhase.PLAYING) {
                delay(1000)
                if (gamePhase == DartGamePhase.PLAYING) {
                    timeRemaining--
                }
            }
            // Time's up - show result if still playing
            if (gamePhase == DartGamePhase.PLAYING) {
                gamePhase = DartGamePhase.RESULT
            }
        }
    }

    // Dart throwing animation with particle effects
    LaunchedEffect(gamePhase) {
        if (gamePhase == DartGamePhase.THROWING) {
            dartProgress = 0f
            dartScale = 1f
            showScorePopup = false
            scorePopupScale = 0f
            scorePopupAlpha = 0f
            trailParticles = emptyList()

            val duration = 600L  // Slower dart flight animation
            val startTime = System.currentTimeMillis()

            while (dartProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                dartProgress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                val easedProgress = 1f - (1f - dartProgress).pow(3)
                dartScale = 1f - easedProgress * 0.6f

                // Add trail particles during flight (reduced)
                if (dartProgress < 0.9f && kotlin.random.Random.nextFloat() < 0.15f) {
                    trailParticles = trailParticles + DartParticle(
                        x = dartProgress,
                        y = kotlin.random.Random.nextFloat() * 0.1f - 0.05f,
                        vx = 0f,
                        vy = 0f,
                        life = 150f,
                        maxLife = 150f,
                        size = 6f,
                        color = Color(0xFFf39c12),
                        type = ParticleType.SPARK
                    )
                }

                delay(16)
            }

            // Calculate score
            val score = calculateRealDartScore(landingX, landingY)
            lastScore = score
            totalScore += score.totalPoints
            thrownDarts = thrownDarts + ThrownDart(landingX, landingY, score)
            dartsThrown++

            // Generate hit particles based on score (reduced for performance)
            val particleCount = when {
                score.totalPoints >= 50 -> 15  // Bullseye
                score.multiplier == 3 -> 12    // Triple
                score.multiplier == 2 -> 10    // Double
                score.totalPoints > 0 -> 8     // Single
                else -> 5                       // Miss
            }

            val hitColor = when {
                score.totalPoints >= 50 -> Color(0xFFFFD700)  // Gold
                score.multiplier == 3 -> Color(0xFFe74c3c)    // Red
                score.multiplier == 2 -> Color(0xFF27ae60)    // Green
                else -> Color(0xFFf39c12)                      // Orange
            }

            hitParticles = List(particleCount) {
                val angle = kotlin.random.Random.nextFloat() * 2 * PI.toFloat()
                val speed = kotlin.random.Random.nextFloat() * 6f + 2f
                DartParticle(
                    x = landingX,
                    y = landingY,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    life = 300f,
                    maxLife = 300f,
                    size = kotlin.random.Random.nextFloat() * 8f + 4f,
                    color = hitColor,
                    type = ParticleType.SPARK
                )
            }

            // Score popup animation
            showScorePopup = true
            val popupStart = System.currentTimeMillis()
            while (System.currentTimeMillis() - popupStart < 150) {
                val t = (System.currentTimeMillis() - popupStart) / 150f
                scorePopupScale = 1f + (1f - t) * 0.5f  // Start big, shrink to normal
                scorePopupAlpha = t
                delay(16)
            }
            scorePopupScale = 1f
            scorePopupAlpha = 1f

            delay(650)

            // Fade out
            val fadeStart = System.currentTimeMillis()
            while (System.currentTimeMillis() - fadeStart < 200) {
                scorePopupAlpha = 1f - (System.currentTimeMillis() - fadeStart) / 200f
                delay(16)
            }
            showScorePopup = false
            hitParticles = emptyList()
            trailParticles = emptyList()

            if (dartsThrown >= 3) {
                gamePhase = DartGamePhase.RESULT
            } else if (timeRemaining > 0) {
                gamePhase = DartGamePhase.PLAYING
            } else {
                gamePhase = DartGamePhase.RESULT
            }
        }
    }

    // Particle animation loop - simplified, only run when particles exist
    LaunchedEffect(Unit) {
        while (true) {
            if (hitParticles.isNotEmpty() || trailParticles.isNotEmpty()) {
                hitParticles = hitParticles.filter { p ->
                    p.life -= 32f
                    p.x += p.vx * 0.02f
                    p.y += p.vy * 0.02f
                    p.life > 0
                }
                trailParticles = trailParticles.filter { p ->
                    p.life -= 32f
                    p.life > 0
                }
            }
            delay(32)  // 30fps instead of 60fps for particles
        }
    }

    // Calculate landing position from swipe
    fun processSwipe(swipeData: SwipeData) {
        if (gamePhase != DartGamePhase.PLAYING || dartsThrown >= 3 || timeRemaining <= 0) return

        // Velocity normalization - slower dart flight
        val normalizedVelocity = (swipeData.velocity / 5f).coerceIn(0.2f, 1f)

        // Swipe angle determines horizontal position (more sensitive)
        val angleFromUp = swipeData.angle + (PI / 2).toFloat()
        val horizontalOffset = sin(angleFromUp.toDouble()).toFloat() * 1.2f

        // Better accuracy curve - faster swipes are more accurate
        val accuracy = normalizedVelocity.pow(0.5f) * 0.8f + 0.2f
        val randomSpread = (1f - accuracy) * 0.3f

        // Improved landing calculation - center-biased with skill influence
        val targetX = (horizontalOffset * 0.7f + (kotlin.random.Random.nextFloat() - 0.5f) * randomSpread)
        // Velocity determines vertical position: slow = bottom (miss), fast = top
        // Range: slow (0.2) -> +1.2 (below board), fast (1.0) -> -0.9 (top)
        val verticalBias = (1f - normalizedVelocity) * 2.6f - 0.9f
        val targetY = verticalBias + (kotlin.random.Random.nextFloat() - 0.5f) * randomSpread * 1.5f

        // Allow darts to land outside the board (miss)
        landingX = targetX.coerceIn(-1.3f, 1.3f)
        landingY = targetY.coerceIn(-1.3f, 1.3f)

        gamePhase = DartGamePhase.THROWING
    }

    // Dart rest position (bottom center)
    val dartRestX = screenSize.width / 2
    val dartRestY = screenSize.height - 120f
    val dartHitRadius = 120f  // Touch area for dart (larger for easier grabbing)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(gamePhase, timeRemaining, dartsThrown) {
                when (gamePhase) {
                    DartGamePhase.READY -> {
                        detectDragGestures(
                            onDragStart = { offset ->
                                // Check if touch is on the dart
                                val dx = offset.x - dartRestX
                                val dy = offset.y - dartRestY
                                if (sqrt(dx * dx + dy * dy) < dartHitRadius) {
                                    isDraggingDart = true
                                    dartDragOffset = offset
                                    swipeStart = offset
                                    swipeStartTime = System.currentTimeMillis()
                                    gamePhase = DartGamePhase.PLAYING
                                }
                            },
                            onDrag = { change, _ ->
                                if (isDraggingDart) {
                                    change.consume()
                                    dartDragOffset = change.position
                                    currentSwipe = change.position
                                }
                            },
                            onDragEnd = {
                                if (isDraggingDart) {
                                    val start = swipeStart
                                    val end = currentSwipe
                                    if (start != null && end != null) {
                                        val dx = end.x - start.x
                                        val dy = end.y - start.y
                                        val distance = sqrt(dx * dx + dy * dy)
                                        val elapsed = System.currentTimeMillis() - swipeStartTime

                                        // Throw if dragged upward at all (dy < -30, distance > 40)
                                        if (dy < -30 && distance > 40 && elapsed > 0) {
                                            val velocity = distance / elapsed
                                            val angle = atan2(dy, dx)
                                            processSwipe(SwipeData(start.x, start.y, end.x, end.y, velocity, angle))
                                        }
                                    }
                                    isDraggingDart = false
                                    dartDragOffset = Offset.Zero
                                    swipeStart = null
                                    currentSwipe = null
                                }
                            }
                        )
                    }
                    DartGamePhase.PLAYING -> {
                        if (timeRemaining > 0 && dartsThrown < 3) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    // Check if touch is on the dart
                                    val dx = offset.x - dartRestX
                                    val dy = offset.y - dartRestY
                                    if (sqrt(dx * dx + dy * dy) < dartHitRadius) {
                                        isDraggingDart = true
                                        dartDragOffset = offset
                                        swipeStart = offset
                                        swipeStartTime = System.currentTimeMillis()
                                        currentSwipe = offset
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (isDraggingDart) {
                                        change.consume()
                                        dartDragOffset = change.position
                                        currentSwipe = change.position
                                    }
                                },
                                onDragEnd = {
                                    if (isDraggingDart) {
                                        val start = swipeStart
                                        val end = currentSwipe
                                        if (start != null && end != null) {
                                            val dx = end.x - start.x
                                            val dy = end.y - start.y
                                            val distance = sqrt(dx * dx + dy * dy)
                                            val elapsed = System.currentTimeMillis() - swipeStartTime

                                            // Throw if dragged upward at all (dy < -30, distance > 40)
                                            if (dy < -30 && distance > 40 && elapsed > 0) {
                                                val velocity = distance / elapsed
                                                val angle = atan2(dy, dx)
                                                processSwipe(SwipeData(start.x, start.y, end.x, end.y, velocity, angle))
                                            }
                                        }
                                        isDraggingDart = false
                                        dartDragOffset = Offset.Zero
                                        swipeStart = null
                                        currentSwipe = null
                                    }
                                }
                            )
                        }
                    }
                    else -> { }
                }
            }
    ) {
        // Full screen dart board with particles
        RealDartBoard(
            thrownDarts = thrownDarts,
            dartProgress = if (gamePhase == DartGamePhase.THROWING) dartProgress else null,
            dartScale = dartScale,
            landingX = landingX,
            landingY = landingY,
            hitParticles = hitParticles,
            trailParticles = trailParticles,
            modifier = Modifier.fillMaxSize()
        )

        // Enhanced swipe visualization overlay
        val start = swipeStart
        val current = currentSwipe
        if (start != null && current != null && gamePhase == DartGamePhase.PLAYING) {
            val dx = current.x - start.x
            val dy = current.y - start.y
            val distance = sqrt(dx * dx + dy * dy)
            val velocity = distance / 300f  // Normalize for visual

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Gradient trail effect
                val steps = 15
                for (i in 0 until steps) {
                    val t = i.toFloat() / steps
                    val x = start.x + dx * t
                    val y = start.y + dy * t
                    val alpha = t * 0.6f
                    val radius = 4f + t * 8f

                    drawCircle(
                        color = Color(0xFFf39c12).copy(alpha = alpha),
                        radius = radius,
                        center = Offset(x, y)
                    )
                }

                // Main swipe line with glow
                drawLine(
                    color = Color(0xFFf39c12).copy(alpha = 0.3f),
                    start = start,
                    end = current,
                    strokeWidth = 20f
                )
                drawLine(
                    color = Color(0xFFf39c12),
                    start = start,
                    end = current,
                    strokeWidth = 6f
                )

                // Power indicator at current position
                val powerColor = when {
                    velocity > 0.8f -> Color(0xFFe74c3c)  // Strong - Red
                    velocity > 0.5f -> Color(0xFFf39c12)  // Medium - Orange
                    else -> Color(0xFF3498db)              // Weak - Blue
                }

                // Outer glow
                drawCircle(
                    color = powerColor.copy(alpha = 0.3f),
                    radius = 35f + velocity * 15f,
                    center = current
                )
                // Inner circle
                drawCircle(
                    color = powerColor,
                    radius = 18f + velocity * 8f,
                    center = current
                )
                // Center dot
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = current
                )

                // Direction arrow
                if (dy < -30) {
                    val arrowLen = 40f
                    val angle = atan2(dy, dx)
                    val arrowX = current.x + cos(angle) * arrowLen
                    val arrowY = current.y + sin(angle) * arrowLen

                    drawLine(
                        color = Color.White.copy(alpha = 0.8f),
                        start = current,
                        end = Offset(arrowX, arrowY),
                        strokeWidth = 4f
                    )
                }
            }
        }

        // Draw holdable dart at bottom (when not throwing)
        if (gamePhase != DartGamePhase.THROWING && gamePhase != DartGamePhase.RESULT && dartsThrown < 3) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dartX = if (isDraggingDart) dartDragOffset.x else dartRestX
                val dartY = if (isDraggingDart) dartDragOffset.y else dartRestY

                // Draw dart shadow/glow when at rest
                if (!isDraggingDart) {
                    drawCircle(
                        color = Color(0xFFf39c12).copy(alpha = 0.3f),
                        radius = 50f,
                        center = Offset(dartX, dartY)
                    )
                }

                // Draw the dart
                drawHoldableDart(this, dartX, dartY, if (isDraggingDart) 1.2f else 1f)
            }
        }

        // Top bar - Timer, Score, Darts remaining
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timer
            Text(
                text = if (gamePhase == DartGamePhase.READY) "🎯" else "⏱ ${timeRemaining}s",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (timeRemaining <= 3 && gamePhase != DartGamePhase.READY)
                    Color.Red else Color(0xFFf39c12)
            )

            // Score
            Text(
                text = "$totalScore",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFf39c12)
            )

            // Darts remaining
            Row {
                repeat(3) { index ->
                    Text(
                        text = if (index < 3 - dartsThrown) "🎯" else "⚫",
                        fontSize = 20.sp,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }

        // Animated score popup (center top)
        if (showScorePopup && lastScore != null) {
            val scoreColor = when {
                lastScore!!.totalPoints >= 50 -> Color(0xFFFFD700)  // Bullseye - Gold
                lastScore!!.multiplier == 3 -> Color(0xFFe74c3c)    // Triple - Red
                lastScore!!.multiplier == 2 -> Color(0xFF27ae60)    // Double - Green
                lastScore!!.totalPoints == 0 -> Color.Gray
                else -> Color.White
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                Text(
                    text = lastScore!!.description,
                    fontSize = (28 * scorePopupScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor.copy(alpha = scorePopupAlpha),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "+${lastScore!!.totalPoints}",
                    fontSize = (42 * scorePopupScale).sp,
                    fontWeight = FontWeight.Black,
                    color = scoreColor.copy(alpha = scorePopupAlpha),
                    textAlign = TextAlign.Center
                )
            }
        }


        // Ready screen overlay
        if (gamePhase == DartGamePhase.READY) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎯 GRAB THE DART",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFf39c12)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "⬆️ Drag dart upward to throw!",
                        fontSize = 20.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "10 seconds, 3 darts",
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Result overlay
        if (gamePhase == DartGamePhase.RESULT) {
            DartResultOverlay(
                totalScore = totalScore,
                thrownDarts = thrownDarts,
                isTopScore = isTopDartScore,
                onSaveScore = { name ->
                    onSaveScore(name, totalScore)
                },
                onRestart = {
                    gamePhase = DartGamePhase.READY
                    dartsThrown = 0
                    totalScore = 0
                    thrownDarts = emptyList()
                    timeRemaining = 10
                    lastScore = null
                    showScorePopup = false
                },
                onBack = onBack
            )
        }
    }
}

@Composable
fun RealDartBoard(
    thrownDarts: List<ThrownDart>,
    dartProgress: Float?,
    dartScale: Float,
    landingX: Float,
    landingY: Float,
    hitParticles: List<DartParticle> = emptyList(),
    trailParticles: List<DartParticle> = emptyList(),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        // Position dartboard in upper portion of screen
        val centerY = size.height * 0.35f
        val boardRadius = minOf(size.width, size.height * 0.6f) / 2 * 0.85f

        // Radius ratios
        val doubleOuterR = boardRadius
        val doubleInnerR = boardRadius * 0.95f
        val tripleOuterR = boardRadius * 0.60f
        val tripleInnerR = boardRadius * 0.55f
        val bullOuterR = boardRadius * 0.16f
        val bullInnerR = boardRadius * 0.065f

        // Outer black ring
        drawCircle(
            color = Color(0xFF2C2C2C),
            radius = boardRadius * 1.02f,
            center = Offset(centerX, centerY)
        )

        val segmentAngle = 360f / 20
        val startAngle = -99f

        for (i in 0 until 20) {
            val angle = startAngle + i * segmentAngle
            val isEvenSegment = i % 2 == 0
            val segmentLight = if (isEvenSegment) DART_CREAM else DART_BLACK
            val segmentDark = if (isEvenSegment) DART_RED else DART_GREEN

            // Outer single
            drawSegment(
                center = Offset(centerX, centerY),
                startAngle = angle,
                sweepAngle = segmentAngle,
                innerRadius = tripleOuterR,
                outerRadius = doubleInnerR,
                color = segmentLight
            )

            // Double ring
            drawSegment(
                center = Offset(centerX, centerY),
                startAngle = angle,
                sweepAngle = segmentAngle,
                innerRadius = doubleInnerR,
                outerRadius = doubleOuterR,
                color = segmentDark
            )

            // Inner single
            drawSegment(
                center = Offset(centerX, centerY),
                startAngle = angle,
                sweepAngle = segmentAngle,
                innerRadius = bullOuterR,
                outerRadius = tripleInnerR,
                color = segmentLight
            )

            // Triple ring
            drawSegment(
                center = Offset(centerX, centerY),
                startAngle = angle,
                sweepAngle = segmentAngle,
                innerRadius = tripleInnerR,
                outerRadius = tripleOuterR,
                color = segmentDark
            )
        }

        // Bull
        drawCircle(
            color = DART_GREEN,
            radius = bullOuterR,
            center = Offset(centerX, centerY)
        )

        // Bullseye
        drawCircle(
            color = DART_RED,
            radius = bullInnerR,
            center = Offset(centerX, centerY)
        )

        // Wire rings
        val wireColor = DART_WIRE
        val wireWidth = 1.5f

        listOf(doubleOuterR, doubleInnerR, tripleOuterR, tripleInnerR, bullOuterR, bullInnerR).forEach { radius ->
            drawCircle(
                color = wireColor,
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = wireWidth)
            )
        }

        // Segment dividers
        for (i in 0 until 20) {
            val angle = Math.toRadians((startAngle + i * segmentAngle).toDouble())
            drawLine(
                color = wireColor,
                start = Offset(
                    centerX + bullOuterR * cos(angle).toFloat(),
                    centerY + bullOuterR * sin(angle).toFloat()
                ),
                end = Offset(
                    centerX + doubleOuterR * cos(angle).toFloat(),
                    centerY + doubleOuterR * sin(angle).toFloat()
                ),
                strokeWidth = wireWidth
            )
        }

        // Numbers
        val numberRadius = boardRadius * 1.08f
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = boardRadius * 0.07f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }

        for (i in 0 until 20) {
            val number = DART_NUMBERS[i]
            val angle = Math.toRadians((startAngle + i * segmentAngle + segmentAngle / 2).toDouble())
            val x = centerX + numberRadius * cos(angle).toFloat()
            val y = centerY + numberRadius * sin(angle).toFloat() + textPaint.textSize / 3

            drawContext.canvas.nativeCanvas.drawText(number.toString(), x, y, textPaint)
        }

        // Landed darts
        thrownDarts.forEach { dart ->
            val dartX = centerX + dart.normalizedX * boardRadius
            val dartY = centerY + dart.normalizedY * boardRadius
            drawLandedDart(this, dartX, dartY)
        }

        // Flying dart - starts from bottom of screen
        if (dartProgress != null && dartProgress < 1f) {
            val startX = centerX
            val startY = size.height + 100f  // Start below screen
            val targetX = centerX + landingX * boardRadius
            val targetY = centerY + landingY * boardRadius

            val easedProgress = 1f - (1f - dartProgress).pow(3)
            val currentX = startX + (targetX - startX) * easedProgress

            // Parabolic arc - dart goes up then comes down to target
            val arcHeight = size.height * 0.1f
            val parabola = -4 * arcHeight * dartProgress * (dartProgress - 1f)
            val linearY = startY + (targetY - startY) * easedProgress
            val currentY = linearY - parabola

            // Draw trail particles behind dart
            trailParticles.forEach { particle ->
                val alpha = (particle.life / particle.maxLife).coerceIn(0f, 1f)
                val trailX = startX + (targetX - startX) * particle.x
                val trailY = startY + (targetY - startY) * particle.x -
                    (-4 * arcHeight * particle.x * (particle.x - 1f))

                drawCircle(
                    color = particle.color.copy(alpha = alpha * 0.8f),
                    radius = particle.size * alpha,
                    center = Offset(trailX + particle.y * 50f, trailY)
                )
            }

            drawFlyingDart(this, currentX, currentY, dartScale)
        }

        // Draw hit particles
        hitParticles.forEach { particle ->
            val alpha = (particle.life / particle.maxLife).coerceIn(0f, 1f)
            val px = centerX + particle.x * boardRadius
            val py = centerY + particle.y * boardRadius

            when (particle.type) {
                ParticleType.SPARK -> {
                    // Fast moving spark with trail
                    drawLine(
                        color = particle.color.copy(alpha = alpha),
                        start = Offset(px, py),
                        end = Offset(px - particle.vx * 3, py - particle.vy * 3),
                        strokeWidth = particle.size * 0.5f * alpha
                    )
                }
                ParticleType.GLOW -> {
                    // Glowing orb with soft edge
                    drawCircle(
                        color = particle.color.copy(alpha = alpha * 0.3f),
                        radius = particle.size * 2f,
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = particle.color.copy(alpha = alpha),
                        radius = particle.size * alpha,
                        center = Offset(px, py)
                    )
                }
                ParticleType.STAR -> {
                    // Simple star shape
                    val starSize = particle.size * alpha
                    drawLine(
                        color = particle.color.copy(alpha = alpha),
                        start = Offset(px - starSize, py),
                        end = Offset(px + starSize, py),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = particle.color.copy(alpha = alpha),
                        start = Offset(px, py - starSize),
                        end = Offset(px, py + starSize),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = particle.color.copy(alpha = alpha),
                        start = Offset(px - starSize * 0.7f, py - starSize * 0.7f),
                        end = Offset(px + starSize * 0.7f, py + starSize * 0.7f),
                        strokeWidth = 1.5f
                    )
                }
                ParticleType.SMOKE -> {
                    // Soft smoke puff
                    drawCircle(
                        color = Color.White.copy(alpha = alpha * 0.2f),
                        radius = particle.size * (2f - alpha),
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}

fun DrawScope.drawSegment(
    center: Offset,
    startAngle: Float,
    sweepAngle: Float,
    innerRadius: Float,
    outerRadius: Float,
    color: Color
) {
    val path = Path().apply {
        val startRad = Math.toRadians(startAngle.toDouble())
        val endRad = Math.toRadians((startAngle + sweepAngle).toDouble())

        moveTo(
            center.x + innerRadius * cos(startRad).toFloat(),
            center.y + innerRadius * sin(startRad).toFloat()
        )

        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                center.x - innerRadius,
                center.y - innerRadius,
                center.x + innerRadius,
                center.y + innerRadius
            ),
            startAngleDegrees = startAngle,
            sweepAngleDegrees = sweepAngle,
            forceMoveTo = false
        )

        lineTo(
            center.x + outerRadius * cos(endRad).toFloat(),
            center.y + outerRadius * sin(endRad).toFloat()
        )

        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                center.x - outerRadius,
                center.y - outerRadius,
                center.x + outerRadius,
                center.y + outerRadius
            ),
            startAngleDegrees = startAngle + sweepAngle,
            sweepAngleDegrees = -sweepAngle,
            forceMoveTo = false
        )

        close()
    }

    drawPath(path, color)
}

fun drawLandedDart(drawScope: DrawScope, x: Float, y: Float) {
    with(drawScope) {
        val dartSize = 12f

        drawCircle(
            color = Color(0xFFC0C0C0),
            radius = dartSize * 0.8f,
            center = Offset(x, y)
        )

        drawCircle(
            color = Color(0xFF333333),
            radius = dartSize * 0.4f,
            center = Offset(x, y)
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.6f),
            radius = dartSize * 0.2f,
            center = Offset(x - dartSize * 0.2f, y - dartSize * 0.2f)
        )
    }
}

fun drawFlyingDart(drawScope: DrawScope, x: Float, y: Float, scale: Float) {
    with(drawScope) {
        val dartLength = 90f * scale
        val dartWidth = 22f * scale

        // Motion blur / speed lines
        val blurCount = 5
        for (i in 1..blurCount) {
            val blurAlpha = 0.15f - (i * 0.025f)
            val blurOffset = i * 12f
            drawOval(
                color = Color(0xFF4A4A4A).copy(alpha = blurAlpha),
                topLeft = Offset(x - dartWidth / 2, y - dartLength * 0.3f + blurOffset),
                size = Size(dartWidth * 0.8f, dartLength * 0.4f)
            )
        }

        // Metallic body with gradient effect
        drawOval(
            color = Color(0xFF5A5A5A),
            topLeft = Offset(x - dartWidth / 2, y - dartLength * 0.3f),
            size = Size(dartWidth, dartLength * 0.5f)
        )
        // Highlight
        drawOval(
            color = Color(0xFF8A8A8A),
            topLeft = Offset(x - dartWidth / 3, y - dartLength * 0.25f),
            size = Size(dartWidth * 0.4f, dartLength * 0.35f)
        )

        // Sharp metallic tip
        val tipPath = Path().apply {
            moveTo(x, y - dartLength * 0.55f)
            lineTo(x - dartWidth * 0.25f, y - dartLength * 0.3f)
            lineTo(x + dartWidth * 0.25f, y - dartLength * 0.3f)
            close()
        }
        drawPath(tipPath, Color(0xFFD0D0D0))
        // Tip highlight
        val tipHighlight = Path().apply {
            moveTo(x - dartWidth * 0.05f, y - dartLength * 0.5f)
            lineTo(x - dartWidth * 0.15f, y - dartLength * 0.32f)
            lineTo(x, y - dartLength * 0.35f)
            close()
        }
        drawPath(tipHighlight, Color(0xFFFFFFFF).copy(alpha = 0.5f))

        // Enhanced flights with 3D effect
        val flightColor1 = Color(0xFFe74c3c)
        val flightColor2 = Color(0xFFc0392b)

        // Left flight
        val leftFlight = Path().apply {
            moveTo(x, y + dartLength * 0.1f)
            lineTo(x - dartWidth * 1.4f, y + dartLength * 0.45f)
            lineTo(x - dartWidth * 0.3f, y + dartLength * 0.25f)
            close()
        }
        drawPath(leftFlight, flightColor1)

        // Right flight
        val rightFlight = Path().apply {
            moveTo(x, y + dartLength * 0.1f)
            lineTo(x + dartWidth * 1.4f, y + dartLength * 0.45f)
            lineTo(x + dartWidth * 0.3f, y + dartLength * 0.25f)
            close()
        }
        drawPath(rightFlight, flightColor2)

        // Flight center stripe
        drawLine(
            color = Color.White.copy(alpha = 0.4f),
            start = Offset(x, y + dartLength * 0.08f),
            end = Offset(x, y + dartLength * 0.4f),
            strokeWidth = 3f * scale
        )

        // Shaft rings
        for (i in 0..2) {
            val ringY = y - dartLength * 0.1f + i * dartLength * 0.08f
            drawLine(
                color = Color(0xFF888888),
                start = Offset(x - dartWidth * 0.4f, ringY),
                end = Offset(x + dartWidth * 0.4f, ringY),
                strokeWidth = 1.5f
            )
        }
    }
}

fun drawHoldableDart(drawScope: DrawScope, x: Float, y: Float, scale: Float) {
    with(drawScope) {
        val dartLength = 140f * scale
        val dartWidth = 36f * scale

        // Metallic body
        drawOval(
            color = Color(0xFF5A5A5A),
            topLeft = Offset(x - dartWidth / 2, y - dartLength * 0.3f),
            size = Size(dartWidth, dartLength * 0.5f)
        )
        // Highlight
        drawOval(
            color = Color(0xFF8A8A8A),
            topLeft = Offset(x - dartWidth / 3, y - dartLength * 0.25f),
            size = Size(dartWidth * 0.4f, dartLength * 0.35f)
        )

        // Sharp metallic tip
        val tipPath = Path().apply {
            moveTo(x, y - dartLength * 0.55f)
            lineTo(x - dartWidth * 0.25f, y - dartLength * 0.3f)
            lineTo(x + dartWidth * 0.25f, y - dartLength * 0.3f)
            close()
        }
        drawPath(tipPath, Color(0xFFD0D0D0))

        // Flights
        val flightColor1 = Color(0xFFe74c3c)
        val flightColor2 = Color(0xFFc0392b)

        val leftFlight = Path().apply {
            moveTo(x, y + dartLength * 0.1f)
            lineTo(x - dartWidth * 1.4f, y + dartLength * 0.45f)
            lineTo(x - dartWidth * 0.3f, y + dartLength * 0.25f)
            close()
        }
        drawPath(leftFlight, flightColor1)

        val rightFlight = Path().apply {
            moveTo(x, y + dartLength * 0.1f)
            lineTo(x + dartWidth * 1.4f, y + dartLength * 0.45f)
            lineTo(x + dartWidth * 0.3f, y + dartLength * 0.25f)
            close()
        }
        drawPath(rightFlight, flightColor2)

        // Flight center stripe
        drawLine(
            color = Color.White.copy(alpha = 0.4f),
            start = Offset(x, y + dartLength * 0.08f),
            end = Offset(x, y + dartLength * 0.4f),
            strokeWidth = 3f * scale
        )
    }
}

fun calculateRealDartScore(normalizedX: Float, normalizedY: Float): DartScore {
    val distance = sqrt(normalizedX * normalizedX + normalizedY * normalizedY)

    // Double and Triple zones expanded by 5%
    val doubleOuterR = 1.0f
    val doubleInnerR = 0.90f    // was 0.95f (5% wider)
    val tripleOuterR = 0.65f    // was 0.60f (5% wider)
    val tripleInnerR = 0.50f    // was 0.55f (5% wider)
    val bullOuterR = 0.16f
    val bullInnerR = 0.065f

    if (distance > doubleOuterR) {
        return DartScore(0, 0, 0, "Miss!")
    }

    if (distance <= bullInnerR) {
        return DartScore(50, 1, 50, "BULLSEYE!")
    }

    if (distance <= bullOuterR) {
        return DartScore(25, 1, 25, "Bull")
    }

    var angle = atan2(normalizedY.toDouble(), normalizedX.toDouble())
    angle = Math.toDegrees(angle)
    angle += 99
    if (angle < 0) angle += 360
    if (angle >= 360) angle -= 360

    val segmentIndex = (angle / 18).toInt() % 20
    val baseNumber = DART_NUMBERS[segmentIndex]

    val (multiplier, zone) = when {
        distance <= tripleInnerR -> Pair(1, "")
        distance <= tripleOuterR -> Pair(3, "Triple ")
        distance <= doubleInnerR -> Pair(1, "")
        else -> Pair(2, "Double ")
    }

    val totalPoints = baseNumber * multiplier
    val description = "$zone$baseNumber"

    return DartScore(baseNumber, multiplier, totalPoints, description)
}

enum class DartResultPhase {
    SHOWING_SCORE,
    INPUT_NAME,
    READY_TO_RESTART
}

@Composable
fun DartResultOverlay(
    totalScore: Int,
    thrownDarts: List<ThrownDart>,
    isTopScore: Boolean,
    onSaveScore: (String) -> Unit,
    onRestart: () -> Unit,
    onBack: () -> Unit
) {
    var phase by remember { mutableStateOf(DartResultPhase.SHOWING_SCORE) }
    var playerName by remember { mutableStateOf("") }

    // Auto transition to name input if top score
    LaunchedEffect(Unit) {
        delay(1500)
        phase = if (isTopScore && totalScore > 0) DartResultPhase.INPUT_NAME else DartResultPhase.READY_TO_RESTART
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🎯 RESULT 🎯",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3498db)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (thrownDarts.isEmpty()) {
                Text(
                    text = "No darts thrown!",
                    fontSize = 20.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            } else {
                thrownDarts.forEachIndexed { index, dart ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dart ${index + 1}:",
                            fontSize = 18.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = dart.score.description,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                dart.score.multiplier == 3 -> Color(0xFFFFD700)
                                dart.score.multiplier == 2 -> Color(0xFF27ae60)
                                dart.score.totalPoints >= 50 -> Color(0xFFe74c3c)
                                else -> Color.White
                            }
                        )
                        Text(
                            text = "(${dart.score.totalPoints})",
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "TOTAL",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Text(
                text = "$totalScore",
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFf39c12)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Name input phase
            if (phase == DartResultPhase.INPUT_NAME) {
                Text(
                    text = "🏆 TOP 10! Enter your name:",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Name display
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(48.dp)
                        .background(Color(0xFF16213e), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = playerName.ifEmpty { "_" },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Alphabet Keypad
                DartNameKeypad(
                    onString = { str ->
                        if (playerName.length < 10) {
                            playerName += str
                        }
                    },
                    onDelete = {
                        if (playerName.isNotEmpty()) {
                            playerName = playerName.dropLast(1)
                        }
                    },
                    onConfirm = {
                        if (playerName.isNotEmpty()) {
                            onSaveScore(playerName)
                            phase = DartResultPhase.READY_TO_RESTART
                        }
                    }
                )
            }

            // Buttons (show after name input or if not top score)
            if (phase == DartResultPhase.READY_TO_RESTART) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(52.dp)
                            .background(Color(0xFF7f8c8d), RoundedCornerShape(12.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { onBack() },
                                    onDrag = { _, _ -> },
                                    onDragEnd = {}
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🏠 HOME",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(52.dp)
                            .background(Color(0xFF27ae60), RoundedCornerShape(12.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { onRestart() },
                                    onDrag = { _, _ -> },
                                    onDragEnd = {}
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔄 RETRY",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DartNameKeypad(
    onString: (String) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit
) {
    // A-Z alphabet
    val alphabet = ('A'..'Z').map { it.toString() }
    // Christmas & fun emojis
    val emojis = listOf("🎅", "🎄", "🐾", "⭐")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: A-G (7 letters)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            alphabet.take(7).forEach { char ->
                KeyButton(text = char, onClick = { onString(char) })
            }
        }

        // Row 2: H-N (7 letters)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            alphabet.drop(7).take(7).forEach { char ->
                KeyButton(text = char, onClick = { onString(char) })
            }
        }

        // Row 3: O-U (7 letters)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            alphabet.drop(14).take(7).forEach { char ->
                KeyButton(text = char, onClick = { onString(char) })
            }
        }

        // Row 4: V-Z (5 letters) + 2 emojis
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            alphabet.drop(21).forEach { char ->
                KeyButton(text = char, onClick = { onString(char) })
            }
            emojis.take(2).forEach { emoji ->
                KeyButton(text = emoji, onClick = { onString(emoji) }, isEmoji = true)
            }
        }

        // Row 5: 2 more emojis
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            emojis.drop(2).forEach { emoji ->
                KeyButton(text = emoji, onClick = { onString(emoji) }, isEmoji = true)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom row: DEL and OK
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // DEL button
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(44.dp)
                    .background(Color(0xFFe74c3c), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDelete() },
                            onDrag = { _, _ -> },
                            onDragEnd = {}
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⌫ DEL",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // OK button
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(44.dp)
                    .background(Color(0xFF27ae60), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onConfirm() },
                            onDrag = { _, _ -> },
                            onDragEnd = {}
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓ OK",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun KeyButton(text: String, onClick: () -> Unit, isEmoji: Boolean = false) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                if (isEmoji) Color(0xFFe67e22) else Color(0xFF3498db),
                RoundedCornerShape(6.dp)
            )
            .pointerInput(text) {
                detectDragGestures(
                    onDragStart = { onClick() },
                    onDrag = { _, _ -> },
                    onDragEnd = {}
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = if (isEmoji) 20.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
