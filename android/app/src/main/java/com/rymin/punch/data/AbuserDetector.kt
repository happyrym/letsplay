package com.rymin.punch.data

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Abuser Detection System for Android
 * Detects cheating patterns like bots, scripts, and impossible scores
 */
object AbuserDetector {

    enum class AbuseReason(val code: String, val description: String) {
        HONEYPOT("honeypot", "Used cheat function"),
        IMPOSSIBLE_SCORE("impossible_score", "Impossible score"),
        NO_GAMEPLAY("no_gameplay", "No gameplay detected"),
        SPEED_HACK("speed_hack", "Speed hack"),
        BOT_TOUCH_PATTERN("bot_touch_pattern", "Bot touch pattern"),
        BOT_DRAG_PATTERN("bot_drag_pattern", "Bot drag pattern"),
        VIRTUAL_TOUCH("virtual_touch", "Virtual touch")
    }

    data class TouchPoint(
        val x: Float,
        val y: Float,
        val time: Long = System.currentTimeMillis(),
        val radiusX: Float = 0f,
        val radiusY: Float = 0f
    )

    data class DragPath(val points: List<TouchPoint>)

    data class GameState(
        var started: Boolean = false,
        var startTime: Long = 0,
        var interactions: Int = 0,
        var dragEvents: Int = 0,
        var touchPoints: MutableList<TouchPoint> = mutableListOf(),
        var dragPaths: MutableList<DragPath> = mutableListOf(),
        var touchRadii: MutableList<Pair<Float, Float>> = mutableListOf()
    )

    data class ValidationResult(
        val valid: Boolean,
        val reason: AbuseReason? = null,
        val detail: String? = null
    )

    private var gameState = GameState()

    fun resetGameState() {
        gameState = GameState()
    }

    fun startGame() {
        gameState.started = true
        gameState.startTime = System.currentTimeMillis()
    }

    fun recordInteraction() {
        gameState.interactions++
    }

    fun recordDrag() {
        gameState.dragEvents++
    }

    fun recordTouch(x: Float, y: Float, radiusX: Float = 0f, radiusY: Float = 0f) {
        gameState.touchPoints.add(TouchPoint(x, y, System.currentTimeMillis(), radiusX, radiusY))
        gameState.touchRadii.add(Pair(radiusX, radiusY))
    }

    fun recordDragPath(points: List<TouchPoint>) {
        if (points.isNotEmpty()) {
            gameState.dragPaths.add(DragPath(points))
        }
    }

    /**
     * Calculate touch coordinate variance
     * If touches are always at the same position, it's likely a bot
     */
    private fun calculateTouchVariance(): ValidationResult {
        val points = gameState.touchPoints
        if (points.size < 3) return ValidationResult(true)

        val xValues = points.map { it.x }
        val yValues = points.map { it.y }

        val xMean = xValues.average().toFloat()
        val yMean = yValues.average().toFloat()

        val xVariance = xValues.map { (it - xMean).pow(2) }.average()
        val yVariance = yValues.map { (it - yMean).pow(2) }.average()

        // If variance is almost 0, suspect bot (always touching same position)
        if (xVariance < 1 && yVariance < 1) {
            return ValidationResult(
                valid = false,
                reason = AbuseReason.BOT_TOUCH_PATTERN,
                detail = "Touch variance too low (x: %.2f, y: %.2f)".format(xVariance, yVariance)
            )
        }

        return ValidationResult(true)
    }

    /**
     * Analyze drag trajectory linearity
     * Perfect straight lines indicate bot usage
     */
    private fun analyzeDragLinearity(): ValidationResult {
        val paths = gameState.dragPaths
        if (paths.isEmpty()) return ValidationResult(true)

        var perfectLineCount = 0

        for (path in paths) {
            if (path.points.size < 5) continue

            val start = path.points.first()
            val end = path.points.last()

            val lineLength = sqrt(
                (end.x - start.x).pow(2) + (end.y - start.y).pow(2)
            )

            if (lineLength < 50) continue // Ignore short drags

            var totalDeviation = 0f
            for (i in 1 until path.points.size - 1) {
                val point = path.points[i]
                // Distance from point to line
                val deviation = kotlin.math.abs(
                    (end.y - start.y) * point.x -
                    (end.x - start.x) * point.y +
                    end.x * start.y - end.y * start.x
                ) / lineLength

                totalDeviation += deviation
            }

            val avgDeviation = totalDeviation / (path.points.size - 2)

            // Average deviation less than 2 pixels = perfect line (bot suspected)
            if (avgDeviation < 2) {
                perfectLineCount++
            }
        }

        // If all drags are perfect lines, it's a bot
        if (paths.size >= 2 && perfectLineCount == paths.size) {
            return ValidationResult(
                valid = false,
                reason = AbuseReason.BOT_DRAG_PATTERN,
                detail = "All ${paths.size} drags are perfect lines"
            )
        }

        return ValidationResult(true)
    }

    /**
     * Detect virtual/simulated touches
     * Real touches have non-zero touch radius
     */
    private fun detectVirtualTouch(): ValidationResult {
        val radii = gameState.touchRadii
        if (radii.size < 3) return ValidationResult(true)

        val zeroRadiusCount = radii.count { it.first == 0f && it.second == 0f }
        val zeroRatio = zeroRadiusCount.toFloat() / radii.size

        // All touches have zero radius = simulated (bot suspected)
        if (zeroRatio == 1f && radii.size >= 5) {
            return ValidationResult(
                valid = false,
                reason = AbuseReason.VIRTUAL_TOUCH,
                detail = "All ${radii.size} touches have zero radius (simulated)"
            )
        }

        return ValidationResult(true)
    }

    /**
     * Validate score and gameplay
     */
    fun validateScore(score: Int, maxScore: Int): ValidationResult {
        // 1. Impossible score check
        if (score > maxScore) {
            return ValidationResult(
                valid = false,
                reason = AbuseReason.IMPOSSIBLE_SCORE,
                detail = "Score $score exceeds max $maxScore"
            )
        }

        // 2. Negative score check
        if (score < 0) {
            return ValidationResult(
                valid = false,
                reason = AbuseReason.IMPOSSIBLE_SCORE,
                detail = "Negative score: $score"
            )
        }

        // 3. Game started check
        if (!gameState.started) {
            return ValidationResult(
                valid = false,
                reason = AbuseReason.NO_GAMEPLAY,
                detail = "Score submitted without starting game"
            )
        }

        // 4. Play time check (minimum 2 seconds)
        val playTime = System.currentTimeMillis() - gameState.startTime
        if (playTime < 2000 && gameState.started) {
            return ValidationResult(
                valid = false,
                reason = AbuseReason.SPEED_HACK,
                detail = "Game completed in ${playTime}ms"
            )
        }

        // 5. Interaction check (minimum 1)
        if (gameState.interactions < 1 && gameState.started) {
            return ValidationResult(
                valid = false,
                reason = AbuseReason.NO_GAMEPLAY,
                detail = "No interactions recorded"
            )
        }

        // 6. Touch variance check
        val touchVariance = calculateTouchVariance()
        if (!touchVariance.valid) return touchVariance

        // 7. Drag linearity check
        val dragLinearity = analyzeDragLinearity()
        if (!dragLinearity.valid) return dragLinearity

        // 8. Virtual touch detection
        val virtualTouch = detectVirtualTouch()
        if (!virtualTouch.valid) return virtualTouch

        return ValidationResult(true)
    }
}
