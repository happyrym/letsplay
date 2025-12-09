package com.rymin.punch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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
    AIMING,
    THROWING,
    LANDED,
    RESULT
}

data class DartScore(
    val baseNumber: Int,      // 1-20, 25(bull), 50(bullseye)
    val multiplier: Int,      // 1, 2(double), 3(triple)
    val totalPoints: Int,
    val description: String   // e.g., "Triple 20", "Double Bull"
)

data class ThrownDart(
    val normalizedX: Float,   // -1 to 1 (center = 0)
    val normalizedY: Float,   // -1 to 1 (center = 0)
    val score: DartScore
)

@Composable
fun DartGameScreen(
    onLeaderboardUpdated: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    var gamePhase by remember { mutableStateOf(DartGamePhase.AIMING) }
    var dartsThrown by remember { mutableStateOf(0) }
    var totalScore by remember { mutableStateOf(0) }
    var thrownDarts by remember { mutableStateOf<List<ThrownDart>>(emptyList()) }

    // Power and angle controls
    var power by remember { mutableStateOf(0.5f) }
    var angle by remember { mutableStateOf(0f) } // -1 to 1 (left to right)

    // Power bar oscillation
    var powerDirection by remember { mutableStateOf(1) }

    // Dart animation
    var dartProgress by remember { mutableStateOf(0f) }
    var dartScale by remember { mutableStateOf(1f) }
    var landingX by remember { mutableStateOf(0f) }
    var landingY by remember { mutableStateOf(0f) }
    var lastScore by remember { mutableStateOf<DartScore?>(null) }

    // Auto-oscillating power bar
    LaunchedEffect(gamePhase) {
        if (gamePhase == DartGamePhase.AIMING) {
            while (gamePhase == DartGamePhase.AIMING) {
                delay(16)
                power += 0.015f * powerDirection
                if (power >= 1f) {
                    power = 1f
                    powerDirection = -1
                } else if (power <= 0f) {
                    power = 0f
                    powerDirection = 1
                }
            }
        }
    }

    // Dart throwing animation
    LaunchedEffect(gamePhase) {
        if (gamePhase == DartGamePhase.THROWING) {
            dartProgress = 0f
            dartScale = 1f

            // Calculate landing position based on power and angle
            // Power affects how close to center (high power = more center)
            // Angle affects left/right
            val randomSpread = 0.15f * (1f - power * 0.7f)  // Less spread with more power
            val targetX = angle * 0.6f + (kotlin.random.Random.nextFloat() - 0.5f) * randomSpread
            val targetY = (1f - power) * 0.7f - 0.35f + (kotlin.random.Random.nextFloat() - 0.5f) * randomSpread

            landingX = targetX.coerceIn(-0.95f, 0.95f)
            landingY = targetY.coerceIn(-0.95f, 0.95f)

            // Animate dart flying
            val duration = 600L
            val startTime = System.currentTimeMillis()

            while (dartProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                dartProgress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

                // Ease out curve
                val easedProgress = 1f - (1f - dartProgress).pow(3)

                // Scale decreases as dart flies away (perspective)
                dartScale = 1f - easedProgress * 0.6f

                delay(16)
            }

            // Calculate score based on landing position
            val score = calculateRealDartScore(landingX, landingY)
            lastScore = score
            totalScore += score.totalPoints
            thrownDarts = thrownDarts + ThrownDart(landingX, landingY, score)
            dartsThrown++

            gamePhase = DartGamePhase.LANDED
            delay(1500)

            if (dartsThrown >= 3) {
                gamePhase = DartGamePhase.RESULT
            } else {
                gamePhase = DartGamePhase.AIMING
                power = 0.5f
                angle = 0f
                lastScore = null
            }
        }
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
            // Top bar - Score and darts remaining
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯 DART",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3498db)
                )

                Text(
                    text = "$totalScore",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFf39c12)
                )

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
            if (lastScore != null && (gamePhase == DartGamePhase.LANDED || gamePhase == DartGamePhase.THROWING)) {
                Text(
                    text = "${lastScore!!.description}\n+${lastScore!!.totalPoints}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        lastScore!!.multiplier == 3 -> Color(0xFFFFD700)
                        lastScore!!.multiplier == 2 -> Color(0xFF27ae60)
                        lastScore!!.totalPoints >= 50 -> Color(0xFFe74c3c)
                        else -> Color.White
                    },
                    modifier = Modifier.padding(8.dp),
                    lineHeight = 32.sp
                )
            } else {
                Spacer(modifier = Modifier.height(68.dp))
            }

            // Dart board area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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

            // Control area
            if (gamePhase == DartGamePhase.AIMING || gamePhase == DartGamePhase.THROWING || gamePhase == DartGamePhase.LANDED) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Angle control (horizontal drag)
                    Text(
                        text = "◀ DRAG TO AIM ▶",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(Color(0xFF2C3E50), RoundedCornerShape(8.dp))
                            .pointerInput(gamePhase == DartGamePhase.AIMING) {
                                if (gamePhase == DartGamePhase.AIMING) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        angle = (angle + dragAmount.x / 400f).coerceIn(-1f, 1f)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Angle indicator
                        Box(
                            modifier = Modifier
                                .offset(x = (angle * 100).dp)
                                .size(36.dp)
                                .background(Color(0xFF3498db), RoundedCornerShape(18.dp))
                        )

                        // Center line
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(30.dp)
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Power bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "POWER",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.width(60.dp)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .background(Color(0xFF2C3E50), RoundedCornerShape(12.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(power)
                                    .fillMaxHeight()
                                    .background(
                                        when {
                                            power < 0.3f -> Color(0xFF27ae60)
                                            power < 0.7f -> Color(0xFFf39c12)
                                            else -> Color(0xFFe74c3c)
                                        },
                                        RoundedCornerShape(12.dp)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Throw button
                    Button(
                        onClick = {
                            if (gamePhase == DartGamePhase.AIMING) {
                                gamePhase = DartGamePhase.THROWING
                            }
                        },
                        enabled = gamePhase == DartGamePhase.AIMING,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFe74c3c),
                            disabledContainerColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "🎯 THROW!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
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
                        gamePhase = DartGamePhase.AIMING
                        dartsThrown = 0
                        totalScore = 0
                        thrownDarts = emptyList()
                        power = 0.5f
                        angle = 0f
                        lastScore = null
                    },
                    onBack = onBack
                )
            }
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
        val boardRadius = minOf(size.width, size.height) / 2 * 0.95f

        // Radius ratios (based on real dartboard proportions)
        val doubleOuterR = boardRadius
        val doubleInnerR = boardRadius * 0.95f
        val tripleOuterR = boardRadius * 0.60f
        val tripleInnerR = boardRadius * 0.55f
        val bullOuterR = boardRadius * 0.16f
        val bullInnerR = boardRadius * 0.065f

        // Draw outer black ring
        drawCircle(
            color = Color(0xFF2C2C2C),
            radius = boardRadius * 1.02f,
            center = Offset(centerX, centerY)
        )

        // Draw the 20 segments
        val segmentAngle = 360f / 20
        val startAngle = -99f  // Rotate so 20 is at top

        for (i in 0 until 20) {
            val number = DART_NUMBERS[i]
            val angle = startAngle + i * segmentAngle

            // Determine colors based on segment position
            val isEvenSegment = i % 2 == 0
            val segmentLight = if (isEvenSegment) DART_CREAM else DART_BLACK
            val segmentDark = if (isEvenSegment) DART_RED else DART_GREEN

            // Draw outer single (between double and triple)
            drawSegment(
                center = Offset(centerX, centerY),
                startAngle = angle,
                sweepAngle = segmentAngle,
                innerRadius = tripleOuterR,
                outerRadius = doubleInnerR,
                color = segmentLight
            )

            // Draw double ring
            drawSegment(
                center = Offset(centerX, centerY),
                startAngle = angle,
                sweepAngle = segmentAngle,
                innerRadius = doubleInnerR,
                outerRadius = doubleOuterR,
                color = segmentDark
            )

            // Draw inner single (between triple and bull)
            drawSegment(
                center = Offset(centerX, centerY),
                startAngle = angle,
                sweepAngle = segmentAngle,
                innerRadius = bullOuterR,
                outerRadius = tripleInnerR,
                color = segmentLight
            )

            // Draw triple ring
            drawSegment(
                center = Offset(centerX, centerY),
                startAngle = angle,
                sweepAngle = segmentAngle,
                innerRadius = tripleInnerR,
                outerRadius = tripleOuterR,
                color = segmentDark
            )
        }

        // Draw bull (outer bull - 25 points)
        drawCircle(
            color = DART_GREEN,
            radius = bullOuterR,
            center = Offset(centerX, centerY)
        )

        // Draw bullseye (inner bull - 50 points)
        drawCircle(
            color = DART_RED,
            radius = bullInnerR,
            center = Offset(centerX, centerY)
        )

        // Draw wire rings
        val wireColor = DART_WIRE
        val wireWidth = 1.5f

        // Outer ring
        drawCircle(
            color = wireColor,
            radius = doubleOuterR,
            center = Offset(centerX, centerY),
            style = Stroke(width = wireWidth)
        )

        // Double inner
        drawCircle(
            color = wireColor,
            radius = doubleInnerR,
            center = Offset(centerX, centerY),
            style = Stroke(width = wireWidth)
        )

        // Triple outer
        drawCircle(
            color = wireColor,
            radius = tripleOuterR,
            center = Offset(centerX, centerY),
            style = Stroke(width = wireWidth)
        )

        // Triple inner
        drawCircle(
            color = wireColor,
            radius = tripleInnerR,
            center = Offset(centerX, centerY),
            style = Stroke(width = wireWidth)
        )

        // Bull outer
        drawCircle(
            color = wireColor,
            radius = bullOuterR,
            center = Offset(centerX, centerY),
            style = Stroke(width = wireWidth)
        )

        // Bullseye
        drawCircle(
            color = wireColor,
            radius = bullInnerR,
            center = Offset(centerX, centerY),
            style = Stroke(width = wireWidth)
        )

        // Draw segment dividers
        for (i in 0 until 20) {
            val angle = Math.toRadians((startAngle + i * segmentAngle).toDouble())
            val innerX = centerX + bullOuterR * cos(angle).toFloat()
            val innerY = centerY + bullOuterR * sin(angle).toFloat()
            val outerX = centerX + doubleOuterR * cos(angle).toFloat()
            val outerY = centerY + doubleOuterR * sin(angle).toFloat()

            drawLine(
                color = wireColor,
                start = Offset(innerX, innerY),
                end = Offset(outerX, outerY),
                strokeWidth = wireWidth
            )
        }

        // Draw numbers around the board
        val numberRadius = boardRadius * 1.08f
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = boardRadius * 0.08f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }

        for (i in 0 until 20) {
            val number = DART_NUMBERS[i]
            val angle = Math.toRadians((startAngle + i * segmentAngle + segmentAngle / 2).toDouble())
            val x = centerX + numberRadius * cos(angle).toFloat()
            val y = centerY + numberRadius * sin(angle).toFloat() + textPaint.textSize / 3

            drawContext.canvas.nativeCanvas.drawText(
                number.toString(),
                x,
                y,
                textPaint
            )
        }

        // Draw landed darts
        thrownDarts.forEach { dart ->
            val dartX = centerX + dart.normalizedX * boardRadius
            val dartY = centerY + dart.normalizedY * boardRadius
            drawLandedDart(this, dartX, dartY)
        }

        // Draw flying dart
        if (dartProgress != null && dartProgress < 1f) {
            val startX = centerX
            val startY = size.height * 1.3f

            val targetX = centerX + landingX * boardRadius
            val targetY = centerY + landingY * boardRadius

            val easedProgress = 1f - (1f - dartProgress).pow(3)
            val currentX = startX + (targetX - startX) * easedProgress

            // Parabolic arc
            val arcHeight = size.height * 0.2f * dartScale
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

        // Start at inner radius
        moveTo(
            center.x + innerRadius * cos(startRad).toFloat(),
            center.y + innerRadius * sin(startRad).toFloat()
        )

        // Arc to end of inner
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

        // Line to outer radius
        lineTo(
            center.x + outerRadius * cos(endRad).toFloat(),
            center.y + outerRadius * sin(endRad).toFloat()
        )

        // Arc back on outer
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

        // Dart point (silver)
        drawCircle(
            color = Color(0xFFC0C0C0),
            radius = dartSize * 0.8f,
            center = Offset(x, y)
        )

        // Dart center (dark)
        drawCircle(
            color = Color(0xFF333333),
            radius = dartSize * 0.4f,
            center = Offset(x, y)
        )

        // Highlight
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

        // Dart body (barrel)
        drawOval(
            color = Color(0xFF4A4A4A),
            topLeft = Offset(x - dartWidth / 2, y - dartLength * 0.3f),
            size = Size(dartWidth, dartLength * 0.5f)
        )

        // Dart tip
        val tipPath = Path().apply {
            moveTo(x, y - dartLength * 0.5f)
            lineTo(x - dartWidth * 0.3f, y - dartLength * 0.3f)
            lineTo(x + dartWidth * 0.3f, y - dartLength * 0.3f)
            close()
        }
        drawPath(tipPath, Color(0xFFC0C0C0))

        // Dart flights
        val flightPath = Path().apply {
            moveTo(x, y + dartLength * 0.1f)
            lineTo(x - dartWidth * 1.2f, y + dartLength * 0.4f)
            lineTo(x, y + dartLength * 0.25f)
            lineTo(x + dartWidth * 1.2f, y + dartLength * 0.4f)
            close()
        }
        drawPath(flightPath, Color(0xFFe74c3c))

        // Flight details
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

    // Radius ratios (matching the drawing)
    val doubleOuterR = 1.0f
    val doubleInnerR = 0.95f
    val tripleOuterR = 0.60f
    val tripleInnerR = 0.55f
    val bullOuterR = 0.16f
    val bullInnerR = 0.065f

    // Check if outside the board
    if (distance > doubleOuterR) {
        return DartScore(0, 0, 0, "Miss!")
    }

    // Check bullseye
    if (distance <= bullInnerR) {
        return DartScore(50, 1, 50, "BULLSEYE!")
    }

    // Check bull (outer bull)
    if (distance <= bullOuterR) {
        return DartScore(25, 1, 25, "Bull")
    }

    // Calculate which segment (1-20)
    var angle = atan2(normalizedY.toDouble(), normalizedX.toDouble())
    angle = Math.toDegrees(angle)

    // Adjust angle to match our board layout (20 at top)
    angle += 99  // Offset to align with segment 0 (which is 20)
    if (angle < 0) angle += 360
    if (angle >= 360) angle -= 360

    val segmentIndex = (angle / 18).toInt() % 20
    val baseNumber = DART_NUMBERS[segmentIndex]

    // Determine multiplier based on distance
    val (multiplier, zone) = when {
        distance <= tripleInnerR -> Pair(1, "")           // Inner single
        distance <= tripleOuterR -> Pair(3, "Triple ")    // Triple
        distance <= doubleInnerR -> Pair(1, "")           // Outer single
        else -> Pair(2, "Double ")                         // Double
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
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🎯 RESULT 🎯",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3498db)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Individual dart scores
            thrownDarts.forEachIndexed { index, dart ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dart ${index + 1}:",
                        fontSize = 20.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = dart.score.description,
                        fontSize = 20.sp,
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
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "TOTAL",
                fontSize = 20.sp,
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
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7f8c8d)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .width(130.dp)
                        .height(52.dp)
                ) {
                    Text(
                        text = "🏠 HOME",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27ae60)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .width(130.dp)
                        .height(52.dp)
                ) {
                    Text(
                        text = "🔄 RETRY",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
