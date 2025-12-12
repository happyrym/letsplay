package com.rymin.punch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
    onResetLeaderboard: () -> Unit = {},
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

    // Game area size (right half)
    var gameAreaSize by remember { mutableStateOf(Size.Zero) }
    var gameAreaOffset by remember { mutableStateOf(Offset.Zero) }

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

    // Name input for result phase
    var playerName by remember { mutableStateOf("") }

    // Timer countdown - 3개 다트 던지는 총 시간 10초 (THROWING 중에도 계속 흐름)
    var timerRunning by remember { mutableStateOf(false) }
    
    LaunchedEffect(gamePhase) {
        if (gamePhase == DartGamePhase.PLAYING && !timerRunning) {
            timerRunning = true
            timeRemaining = 10
        }
    }
    
    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            while (timeRemaining > 0 && gamePhase != DartGamePhase.RESULT && gamePhase != DartGamePhase.READY) {
                delay(1000)
                if (gamePhase != DartGamePhase.RESULT && gamePhase != DartGamePhase.READY) {
                    timeRemaining--
                }
            }
            // 시간 초과 시 결과 화면으로
            if (timeRemaining <= 0 && gamePhase != DartGamePhase.RESULT) {
                gamePhase = DartGamePhase.RESULT
            }
            timerRunning = false
        }
    }

    // Dart throwing animation
    LaunchedEffect(gamePhase) {
        if (gamePhase == DartGamePhase.THROWING) {
            dartProgress = 0f
            dartScale = 1f
            showScorePopup = false
            scorePopupScale = 0f
            scorePopupAlpha = 0f
            trailParticles = emptyList()

            val duration = 400L
            val startTime = System.currentTimeMillis()

            while (dartProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                dartProgress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                dartScale = 1f - (1f - (1f - dartProgress).pow(3)) * 0.6f

                if (dartProgress < 0.9f && trailParticles.size < 10) {
                    if (kotlin.random.Random.nextFloat() < 0.3f) {
                        trailParticles = trailParticles + DartParticle(
                            x = dartProgress,
                            y = kotlin.random.Random.nextFloat() * 0.04f - 0.02f,
                            vx = 0f, vy = 0f,
                            life = 120f, maxLife = 120f, size = 5f,
                            color = Color(0xFFf39c12),
                            type = ParticleType.SPARK
                        )
                    }
                }
                delay(16)
            }

            val score = calculateRealDartScore(landingX, landingY)
            lastScore = score
            totalScore += score.totalPoints
            thrownDarts = thrownDarts + ThrownDart(landingX, landingY, score)
            dartsThrown++

            val particleCount = when {
                score.totalPoints >= 50 -> 6
                score.multiplier == 3 -> 5
                score.multiplier == 2 -> 4
                score.totalPoints > 0 -> 3
                else -> 2
            }
            val hitColor = when {
                score.totalPoints >= 50 -> Color(0xFFFFD700)
                score.multiplier == 3 -> Color(0xFFe74c3c)
                score.multiplier == 2 -> Color(0xFF27ae60)
                else -> Color(0xFFf39c12)
            }
            hitParticles = List(particleCount) {
                val angle = kotlin.random.Random.nextFloat() * 2 * PI.toFloat()
                val speed = kotlin.random.Random.nextFloat() * 5f + 2f
                DartParticle(
                    x = landingX, y = landingY,
                    vx = cos(angle) * speed, vy = sin(angle) * speed,
                    life = 250f, maxLife = 250f,
                    size = kotlin.random.Random.nextFloat() * 6f + 3f,
                    color = hitColor, type = ParticleType.SPARK
                )
            }

            showScorePopup = true
            scorePopupScale = 1.5f
            scorePopupAlpha = 1f
            delay(600)
            showScorePopup = false
            hitParticles = emptyList()
            trailParticles = emptyList()

            if (dartsThrown >= 3 || timeRemaining <= 0) {
                gamePhase = DartGamePhase.RESULT
                playerName = ""  // Reset name for new result
            } else {
                gamePhase = DartGamePhase.PLAYING
            }
        }
    }

    // Particle animation loop
    LaunchedEffect(Unit) {
        while (true) {
            if (hitParticles.isNotEmpty() || trailParticles.isNotEmpty()) {
                hitParticles = hitParticles.mapNotNull { p ->
                    p.life -= 40f
                    p.x += p.vx * 0.02f
                    p.y += p.vy * 0.02f
                    if (p.life > 0) p else null
                }
                trailParticles = trailParticles.mapNotNull { p ->
                    p.life -= 40f
                    if (p.life > 0) p else null
                }
            }
            delay(50)
        }
    }

    // Calculate landing position from swipe
    fun processSwipe(swipeData: SwipeData) {
        if (gamePhase != DartGamePhase.PLAYING || dartsThrown >= 3 || timeRemaining <= 0) return

        val normalizedVelocity = (swipeData.velocity / 5f).coerceIn(0.2f, 1f)
        val angleFromUp = swipeData.angle + (PI / 2).toFloat()
        val horizontalOffset = sin(angleFromUp.toDouble()).toFloat() * 1.2f
        val accuracy = normalizedVelocity.pow(0.5f) * 0.8f + 0.2f
        val randomSpread = (1f - accuracy) * 0.3f
        val targetX = (horizontalOffset * 0.7f + (kotlin.random.Random.nextFloat() - 0.5f) * randomSpread)
        val verticalBias = (1f - normalizedVelocity) * 2.6f - 0.9f
        val targetY = verticalBias + (kotlin.random.Random.nextFloat() - 0.5f) * randomSpread * 1.5f

        landingX = targetX.coerceIn(-1.3f, 1.3f)
        landingY = targetY.coerceIn(-1.3f, 1.3f)
        gamePhase = DartGamePhase.THROWING
    }

    // ========== 가로 모드 태블릿 레이아웃 ==========
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
    ) {
        // ========== 왼쪽 패널: 게임 정보 / 결과 ==========
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF16213e))
                .padding(24.dp)
        ) {
            if (gamePhase == DartGamePhase.RESULT) {
                // 결과 화면: 점수 + 이름 입력 상태 표시
                DartResultLeftPanel(
                    totalScore = totalScore,
                    thrownDarts = thrownDarts,
                    playerName = playerName,
                    isTopScore = isTopDartScore,
                    onRestart = {
                        timerRunning = false
                        gamePhase = DartGamePhase.READY
                        dartsThrown = 0
                        totalScore = 0
                        thrownDarts = emptyList()
                        timeRemaining = 10
                        lastScore = null
                        playerName = ""
                    },
                    onBack = onBack,
                    onResetLeaderboard = onResetLeaderboard
                )
            } else {
                // 게임 진행 중: 타이머, 점수, 다트 현황
                DartGameLeftPanel(
                    gamePhase = gamePhase,
                    timeRemaining = timeRemaining,
                    totalScore = totalScore,
                    dartsThrown = dartsThrown,
                    thrownDarts = thrownDarts,
                    lastScore = lastScore,
                    showScorePopup = showScorePopup,
                    scorePopupScale = scorePopupScale,
                    scorePopupAlpha = scorePopupAlpha
                )
            }
        }

        // ========== 오른쪽 패널: 게임 화면 / 키패드 ==========
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onSizeChanged {
                    gameAreaSize = Size(it.width.toFloat(), it.height.toFloat())
                }
        ) {
            if (gamePhase == DartGamePhase.RESULT) {
                // 결과 화면: ABC 키패드
                DartResultRightPanel(
                    playerName = playerName,
                    isTopScore = isTopDartScore,
                    totalScore = totalScore,
                    onNameChange = { playerName = it },
                    onSaveScore = { name ->
                        onSaveScore(name, totalScore)
                        onLeaderboardUpdated()
                    }
                )
            } else {
                // 게임 화면: 다트보드
                val dartRestX = gameAreaSize.width / 2
                val dartRestY = gameAreaSize.height - 100f
                val dartHitRadius = 100f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(gamePhase, timeRemaining, dartsThrown) {
                            if (gamePhase == DartGamePhase.READY || gamePhase == DartGamePhase.PLAYING) {
                                if (dartsThrown < 3) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val dx = offset.x - dartRestX
                                            val dy = offset.y - dartRestY
                                            if (sqrt(dx * dx + dy * dy) < dartHitRadius) {
                                                isDraggingDart = true
                                                dartDragOffset = offset
                                                swipeStart = offset
                                                swipeStartTime = System.currentTimeMillis()
                                                currentSwipe = offset
                                                if (gamePhase == DartGamePhase.READY) {
                                                    gamePhase = DartGamePhase.PLAYING
                                                }
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
                        }
                ) {
                    // 다트보드
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

                    // 스와이프 시각화
                    val start = swipeStart
                    val current = currentSwipe
                    if (start != null && current != null && isDraggingDart) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val dx = current.x - start.x
                            val dy = current.y - start.y
                            val distance = sqrt(dx * dx + dy * dy)
                            val velocity = distance / 300f

                            drawLine(
                                color = Color(0xFFf39c12).copy(alpha = 0.5f),
                                start = start,
                                end = current,
                                strokeWidth = 8f
                            )
                            val powerColor = when {
                                velocity > 0.8f -> Color(0xFFe74c3c)
                                velocity > 0.5f -> Color(0xFFf39c12)
                                else -> Color(0xFF3498db)
                            }
                            drawCircle(
                                color = powerColor,
                                radius = 20f + velocity * 10f,
                                center = current
                            )
                        }
                    }

                    // 잡을 수 있는 다트
                    if (gamePhase != DartGamePhase.THROWING && gamePhase != DartGamePhase.RESULT && dartsThrown < 3) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val dartX = if (isDraggingDart) dartDragOffset.x else dartRestX
                            val dartY = if (isDraggingDart) dartDragOffset.y else dartRestY

                            if (!isDraggingDart) {
                                drawCircle(
                                    color = Color(0xFFf39c12).copy(alpha = 0.4f),
                                    radius = 45f,
                                    center = Offset(dartX, dartY)
                                )
                            }
                            drawHoldableDart(this, dartX, dartY, if (isDraggingDart) 1.1f else 1f)
                        }
                    }

                    // READY 오버레이
                    if (gamePhase == DartGamePhase.READY) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "🎯 GRAB THE DART",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFf39c12)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "⬆️ Drag upward to throw!",
                                    fontSize = 16.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
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

            // 간소화된 trail 파티클 (성능 최적화)
            trailParticles.forEach { particle ->
                val alpha = (particle.life / particle.maxLife).coerceIn(0f, 1f)
                val trailX = startX + (targetX - startX) * particle.x + particle.y * 50f
                val trailY = startY + (targetY - startY) * particle.x -
                    (-4 * arcHeight * particle.x * (particle.x - 1f))

                // 단순 원으로 통일 (draw call 줄임)
                drawCircle(
                    color = particle.color.copy(alpha = alpha * 0.7f),
                    radius = particle.size * alpha,
                    center = Offset(trailX, trailY)
                )
            }

            drawFlyingDart(this, currentX, currentY, dartScale)
        }

        // 간소화된 hit 파티클 (성능 최적화)
        hitParticles.forEach { particle ->
            val alpha = (particle.life / particle.maxLife).coerceIn(0f, 1f)
            val px = centerX + particle.x * boardRadius
            val py = centerY + particle.y * boardRadius

            // 단순 원 하나로 통일
            drawCircle(
                color = particle.color.copy(alpha = alpha),
                radius = particle.size * alpha,
                center = Offset(px, py)
            )
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

        // 간소화된 motion blur (성능 최적화)
        // 단순 그림자 1개만
        drawOval(
            color = Color(0xFF4A4A4A).copy(alpha = 0.15f),
            topLeft = Offset(x - dartWidth / 2, y - dartLength * 0.3f + 20f),
            size = Size(dartWidth * 0.8f, dartLength * 0.5f)
        )

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

    // Standard dartboard zones
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

enum class DartResultPhase {
    SHOWING_SCORE,
    INPUT_NAME,
    READY_TO_RESTART
}


// ========== 가로 모드 왼쪽 패널: 게임 진행 중 ==========
@Composable
fun DartGameLeftPanel(
    gamePhase: DartGamePhase,
    timeRemaining: Int,
    totalScore: Int,
    dartsThrown: Int,
    thrownDarts: List<ThrownDart>,
    lastScore: DartScore?,
    showScorePopup: Boolean,
    scorePopupScale: Float,
    scorePopupAlpha: Float
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 상단: 타이머
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                text = if (gamePhase == DartGamePhase.READY) "READY" else "TIME",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = if (gamePhase == DartGamePhase.READY) "🎯" else "$timeRemaining",
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                color = if (timeRemaining <= 3 && gamePhase != DartGamePhase.READY)
                    Color(0xFFe74c3c) else Color(0xFFf39c12)
            )
        }

        // 중앙: 총점
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SCORE",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = "$totalScore",
                fontSize = 80.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            // 마지막 점수 팝업
            if (showScorePopup && lastScore != null) {
                val scoreColor = when {
                    lastScore.totalPoints >= 50 -> Color(0xFFFFD700)
                    lastScore.multiplier == 3 -> Color(0xFFe74c3c)
                    lastScore.multiplier == 2 -> Color(0xFF27ae60)
                    lastScore.totalPoints == 0 -> Color.Gray
                    else -> Color(0xFFf39c12)
                }
                Text(
                    text = "+${lastScore.totalPoints}",
                    fontSize = (36 * scorePopupScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor.copy(alpha = scorePopupAlpha)
                )
                Text(
                    text = lastScore.description,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = scoreColor.copy(alpha = scorePopupAlpha)
                )
            }
        }

        // 하단: 다트 현황
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Text(
                text = "DARTS",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 다트 아이콘 (던진 것 / 남은 것)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) { index ->
                    Text(
                        text = if (index < dartsThrown) "✅" else "🎯",
                        fontSize = 32.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 각 다트 점수
            thrownDarts.forEachIndexed { index, dart ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "#${index + 1}",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.5f)
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
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ========== 가로 모드 왼쪽 패널: 결과 화면 ==========
@Composable
fun DartResultLeftPanel(
    totalScore: Int,
    thrownDarts: List<ThrownDart>,
    playerName: String,
    isTopScore: Boolean,
    onRestart: () -> Unit,
    onBack: () -> Unit,
    onResetLeaderboard: () -> Unit
) {
    // Hidden reset sequence
    var resetStep by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    val resetTimeout = 500L

    fun isWithinTimeout(): Boolean = System.currentTimeMillis() - lastTapTime <= resetTimeout
    fun updateTapTime() { lastTapTime = System.currentTimeMillis() }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 상단: 타이틀
        Text(
            text = "🎯 RESULT 🎯",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = if (resetStep == 5) Color.Red else Color(0xFF3498db),
            modifier = Modifier
                .padding(top = 24.dp)
                .clickable {
                    when (resetStep) {
                        0 -> { resetStep = 1; updateTapTime() }
                        2 -> if (isWithinTimeout()) { resetStep = 3; updateTapTime() } else resetStep = 0
                        3 -> if (isWithinTimeout()) { resetStep = 4; updateTapTime() } else resetStep = 0
                        4 -> if (isWithinTimeout()) { resetStep = 5; updateTapTime() } else resetStep = 0
                        else -> resetStep = 0
                    }
                }
        )

        // 중앙: 점수
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TOTAL SCORE",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = "$totalScore",
                fontSize = 80.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFf39c12),
                modifier = Modifier.clickable {
                    if (resetStep == 1 && isWithinTimeout()) {
                        resetStep = 2
                        updateTapTime()
                    } else {
                        resetStep = 0
                    }
                }
            )

            // 이름 입력 중 표시
            if (isTopScore && playerName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "NAME",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Text(
                    text = playerName,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3498db)
                )
            }

            // 각 다트 점수
            Spacer(modifier = Modifier.height(24.dp))
            thrownDarts.forEachIndexed { index, dart ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Dart ${index + 1}:",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${dart.score.description} (${dart.score.totalPoints})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            dart.score.multiplier == 3 -> Color(0xFFFFD700)
                            dart.score.multiplier == 2 -> Color(0xFF27ae60)
                            dart.score.totalPoints >= 50 -> Color(0xFFe74c3c)
                            else -> Color.White
                        }
                    )
                }
            }
        }

        // 하단: 버튼
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(50.dp)
                    .background(Color(0xFF7f8c8d), RoundedCornerShape(12.dp))
                    .clickable { onBack() },
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
                    .width(120.dp)
                    .height(50.dp)
                    .background(
                        if (resetStep == 5) Color.Red else Color(0xFF27ae60),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        if (resetStep == 5) {
                            onResetLeaderboard()
                        }
                        onRestart()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (resetStep == 5) "🗑️ RESET" else "🔄 RETRY",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// ========== 가로 모드 오른쪽 패널: 결과 화면 (키패드) ==========
@Composable
fun DartResultRightPanel(
    playerName: String,
    isTopScore: Boolean,
    totalScore: Int,
    onNameChange: (String) -> Unit,
    onSaveScore: (String) -> Unit
) {
    var isSaved by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!isTopScore || totalScore == 0) {
            // Top 10 아님
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "😢",
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Not in TOP 10",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = "Try again!",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        } else if (isSaved) {
            // 저장 완료
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎉",
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "SAVED!",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF27ae60)
                )
                Text(
                    text = playerName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        } else {
            // 이름 입력 키패드 (이름 표시는 왼쪽 패널에서)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 키패드만 표시
                DartNameKeypad(
                    onString = { str ->
                        if (playerName.length < 10) {
                            onNameChange(playerName + str)
                        }
                    },
                    onDelete = {
                        if (playerName.isNotEmpty()) {
                            val lastChar = playerName.last()
                            onNameChange(
                                if (lastChar.isLowSurrogate() && playerName.length >= 2) {
                                    playerName.dropLast(2)
                                } else {
                                    playerName.dropLast(1)
                                }
                            )
                        }
                    },
                    onConfirm = {
                        if (playerName.isNotEmpty()) {
                            onSaveScore(playerName)
                            isSaved = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DartResultOverlay(
    totalScore: Int,
    thrownDarts: List<ThrownDart>,
    isTopScore: Boolean,
    onSaveScore: (String) -> Unit,
    onRestart: () -> Unit,
    onBack: () -> Unit,
    onResetLeaderboard: () -> Unit = {}
) {
    var phase by remember { mutableStateOf(DartResultPhase.SHOWING_SCORE) }
    var playerName by remember { mutableStateOf("") }

    // Hidden reset sequence: TITLE -> SCORE -> TITLE -> TITLE -> RETRY
    var resetStep by remember { mutableStateOf(0) }  // 0: none, 1: title1, 2: score, 3: title2, 4: title3, 5: ready
    var lastTapTime by remember { mutableStateOf(0L) }
    val resetTimeout = 500L

    fun isWithinTimeout(): Boolean = System.currentTimeMillis() - lastTapTime <= resetTimeout
    fun updateTapTime() { lastTapTime = System.currentTimeMillis() }

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
            // Title - tappable for reset sequence
            Text(
                text = "🎯 RESULT 🎯",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = if (resetStep == 5) Color.Red else Color(0xFF3498db),
                modifier = Modifier.clickable {
                    when (resetStep) {
                        0 -> { resetStep = 1; updateTapTime() }
                        2 -> if (isWithinTimeout()) { resetStep = 3; updateTapTime() } else resetStep = 0
                        3 -> if (isWithinTimeout()) { resetStep = 4; updateTapTime() } else resetStep = 0
                        4 -> if (isWithinTimeout()) { resetStep = 5; updateTapTime() } else resetStep = 0
                        else -> resetStep = 0
                    }
                }
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

            // Score - tappable for reset sequence
            Text(
                text = "$totalScore",
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFf39c12),
                modifier = Modifier.clickable {
                    if (resetStep == 1 && isWithinTimeout()) {
                        resetStep = 2
                        updateTapTime()
                    } else {
                        resetStep = 0
                    }
                }
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
                            // Handle emoji (surrogate pairs) properly
                            val lastChar = playerName.last()
                            playerName = if (lastChar.isLowSurrogate() && playerName.length >= 2) {
                                playerName.dropLast(2)  // Drop surrogate pair
                            } else {
                                playerName.dropLast(1)
                            }
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
                            .clickable { onBack() },
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
                            .background(
                                if (resetStep == 5) Color.Red else Color(0xFF27ae60),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (resetStep == 5) {
                                    onResetLeaderboard()
                                }
                                onRestart()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (resetStep == 5) "🗑️ RESET" else "🔄 RETRY",
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
    // Christmas & fun emojis (9 total: 2 with V-Z, 7 on last row)
    val emojis = listOf("🎅", "🎄", "🐾", "⭐", "❄️", "🎁", "🦌", "☃️", "🔔")

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

        // Row 4: V-Z (5 letters) + 2 emojis = 7
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            alphabet.drop(21).forEach { char ->
                KeyButton(text = char, onClick = { onString(char) })
            }
            emojis.take(2).forEach { emoji ->
                KeyButton(text = emoji, onClick = { onString(emoji) }, isEmoji = true)
            }
        }

        // Row 5: remaining 5 emojis
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
                    .clickable { onDelete() },
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
                    .clickable { onConfirm() },
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
            .clickable { onClick() },
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
