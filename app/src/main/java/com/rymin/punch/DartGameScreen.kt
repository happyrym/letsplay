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

@Composable
fun DartGameScreen(
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

    // Dart animation
    var dartProgress by remember { mutableStateOf(0f) }
    var dartScale by remember { mutableStateOf(1f) }
    var landingX by remember { mutableStateOf(0f) }
    var landingY by remember { mutableStateOf(0f) }
    var lastScore by remember { mutableStateOf<DartScore?>(null) }
    var showScorePopup by remember { mutableStateOf(false) }

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

    // Dart throwing animation
    LaunchedEffect(gamePhase) {
        if (gamePhase == DartGamePhase.THROWING) {
            dartProgress = 0f
            dartScale = 1f
            showScorePopup = false

            val duration = 500L
            val startTime = System.currentTimeMillis()

            while (dartProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                dartProgress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                val easedProgress = 1f - (1f - dartProgress).pow(3)
                dartScale = 1f - easedProgress * 0.6f
                delay(16)
            }

            // Calculate score
            val score = calculateRealDartScore(landingX, landingY)
            lastScore = score
            totalScore += score.totalPoints
            thrownDarts = thrownDarts + ThrownDart(landingX, landingY, score)
            dartsThrown++
            showScorePopup = true

            delay(800)
            showScorePopup = false

            if (dartsThrown >= 3) {
                gamePhase = DartGamePhase.RESULT
            } else if (timeRemaining > 0) {
                gamePhase = DartGamePhase.PLAYING
            } else {
                gamePhase = DartGamePhase.RESULT
            }
        }
    }

    // Calculate landing position from swipe
    fun processSwipe(swipeData: SwipeData) {
        if (gamePhase != DartGamePhase.PLAYING || dartsThrown >= 3 || timeRemaining <= 0) return

        // Normalize velocity (pixels per millisecond to 0-1 range)
        val normalizedVelocity = (swipeData.velocity / 3f).coerceIn(0.2f, 1f)

        // Swipe angle determines horizontal position
        // Straight up (-PI/2) = center, angled left = left side, angled right = right side
        val angleFromUp = swipeData.angle + (PI / 2).toFloat()
        val horizontalOffset = sin(angleFromUp.toDouble()).toFloat() * 0.8f

        // Add some randomness based on velocity (faster = more accurate)
        val accuracy = normalizedVelocity * 0.7f + 0.3f
        val randomSpread = (1f - accuracy) * 0.4f

        // Landing position
        val targetX = horizontalOffset + (kotlin.random.Random.nextFloat() - 0.5f) * randomSpread
        val targetY = (1f - normalizedVelocity) * 0.6f - 0.3f + (kotlin.random.Random.nextFloat() - 0.5f) * randomSpread

        landingX = targetX.coerceIn(-0.95f, 0.95f)
        landingY = targetY.coerceIn(-0.95f, 0.95f)

        gamePhase = DartGamePhase.THROWING
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar - Timer, Score, Darts remaining
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timer
                Text(
                    text = if (gamePhase == DartGamePhase.READY) "🎯" else "⏱ ${timeRemaining}s",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timeRemaining <= 3 && gamePhase == DartGamePhase.PLAYING)
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

            // Score popup
            Box(
                modifier = Modifier
                    .height(60.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (showScorePopup && lastScore != null) {
                    Text(
                        text = "${lastScore!!.description} (+${lastScore!!.totalPoints})",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            lastScore!!.multiplier == 3 -> Color(0xFFFFD700)
                            lastScore!!.multiplier == 2 -> Color(0xFF27ae60)
                            lastScore!!.totalPoints >= 50 -> Color(0xFFe74c3c)
                            lastScore!!.totalPoints == 0 -> Color.Gray
                            else -> Color.White
                        }
                    )
                }
            }

            // Dart board area (top half)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                RealDartBoard(
                    thrownDarts = thrownDarts,
                    dartProgress = if (gamePhase == DartGamePhase.THROWING) dartProgress else null,
                    dartScale = dartScale,
                    landingX = landingX,
                    landingY = landingY,
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(1f)
                )
            }

            // Swipe area (bottom half)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF16213e))
                    .pointerInput(gamePhase, timeRemaining, dartsThrown) {
                        if (gamePhase == DartGamePhase.PLAYING && timeRemaining > 0 && dartsThrown < 3) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    swipeStart = offset
                                    swipeStartTime = System.currentTimeMillis()
                                    currentSwipe = offset
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentSwipe = change.position
                                },
                                onDragEnd = {
                                    val start = swipeStart
                                    val end = currentSwipe
                                    if (start != null && end != null) {
                                        val dx = end.x - start.x
                                        val dy = end.y - start.y
                                        val distance = sqrt(dx * dx + dy * dy)
                                        val elapsed = System.currentTimeMillis() - swipeStartTime

                                        // Only process if swipe is upward and has minimum distance
                                        if (dy < -50 && distance > 100 && elapsed > 0) {
                                            val velocity = distance / elapsed
                                            val angle = atan2(dy, dx)

                                            processSwipe(
                                                SwipeData(
                                                    startX = start.x,
                                                    startY = start.y,
                                                    endX = end.x,
                                                    endY = end.y,
                                                    velocity = velocity,
                                                    angle = angle
                                                )
                                            )
                                        }
                                    }
                                    swipeStart = null
                                    currentSwipe = null
                                }
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Swipe visualization
                val start = swipeStart
                val current = currentSwipe

                if (start != null && current != null && gamePhase == DartGamePhase.PLAYING) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Draw swipe trail
                        drawLine(
                            color = Color(0xFFf39c12),
                            start = start,
                            end = current,
                            strokeWidth = 8f
                        )

                        // Draw arrow at end
                        val dx = current.x - start.x
                        val dy = current.y - start.y
                        val angle = atan2(dy, dx)

                        drawCircle(
                            color = Color(0xFFe74c3c),
                            radius = 20f,
                            center = current
                        )
                    }
                }

                // Instructions
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (gamePhase) {
                        DartGamePhase.READY -> {
                            Text(
                                text = "👆 TAP TO START",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFf39c12)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Swipe up to throw darts!\n10 seconds, 3 darts",
                                fontSize = 18.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                        DartGamePhase.PLAYING -> {
                            if (swipeStart == null) {
                                Text(
                                    text = "⬆️ SWIPE UP TO THROW",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Speed = Power, Angle = Direction",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                        DartGamePhase.THROWING -> {
                            // Show nothing during throw
                        }
                        DartGamePhase.RESULT -> {
                            // Handled by overlay
                        }
                    }
                }

                // Tap to start
                if (gamePhase == DartGamePhase.READY) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        gamePhase = DartGamePhase.PLAYING
                                    },
                                    onDrag = { _, _ -> },
                                    onDragEnd = {}
                                )
                            }
                    )
                }
            }
        }

        // Result overlay
        if (gamePhase == DartGamePhase.RESULT) {
            DartResultOverlay(
                totalScore = totalScore,
                thrownDarts = thrownDarts,
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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val boardRadius = minOf(size.width, size.height) / 2 * 0.92f

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

        // Flying dart
        if (dartProgress != null && dartProgress < 1f) {
            val startX = centerX
            val startY = size.height * 1.5f
            val targetX = centerX + landingX * boardRadius
            val targetY = centerY + landingY * boardRadius

            val easedProgress = 1f - (1f - dartProgress).pow(3)
            val currentX = startX + (targetX - startX) * easedProgress

            val arcHeight = size.height * 0.15f * dartScale
            val parabola = -4 * arcHeight * dartProgress * (dartProgress - 1f)
            val linearY = startY + (targetY - startY) * easedProgress
            val currentY = linearY - parabola

            drawFlyingDart(this, currentX, currentY, dartScale)
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
        val dartLength = 80f * scale
        val dartWidth = 20f * scale

        // Body
        drawOval(
            color = Color(0xFF4A4A4A),
            topLeft = Offset(x - dartWidth / 2, y - dartLength * 0.3f),
            size = Size(dartWidth, dartLength * 0.5f)
        )

        // Tip
        val tipPath = Path().apply {
            moveTo(x, y - dartLength * 0.5f)
            lineTo(x - dartWidth * 0.3f, y - dartLength * 0.3f)
            lineTo(x + dartWidth * 0.3f, y - dartLength * 0.3f)
            close()
        }
        drawPath(tipPath, Color(0xFFC0C0C0))

        // Flights
        val flightPath = Path().apply {
            moveTo(x, y + dartLength * 0.1f)
            lineTo(x - dartWidth * 1.2f, y + dartLength * 0.4f)
            lineTo(x, y + dartLength * 0.25f)
            lineTo(x + dartWidth * 1.2f, y + dartLength * 0.4f)
            close()
        }
        drawPath(flightPath, Color(0xFFe74c3c))

        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(x, y + dartLength * 0.1f),
            end = Offset(x, y + dartLength * 0.35f),
            strokeWidth = 2f * scale
        )
    }
}

fun calculateRealDartScore(normalizedX: Float, normalizedY: Float): DartScore {
    val distance = sqrt(normalizedX * normalizedX + normalizedY * normalizedY)

    val doubleOuterR = 1.0f
    val doubleInnerR = 0.95f
    val tripleOuterR = 0.60f
    val tripleInnerR = 0.55f
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

@Composable
fun DartResultOverlay(
    totalScore: Int,
    thrownDarts: List<ThrownDart>,
    onRestart: () -> Unit,
    onBack: () -> Unit
) {
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

            Spacer(modifier = Modifier.height(24.dp))

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
