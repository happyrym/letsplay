package com.rymin.punch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Score constants
private const val MAX_SCORE = 1000.0
private const val SPEED_SCORE_MAX = 400.0
private const val SPEED_THRESHOLD = 50f  // Speed above this threshold gives full speed score
private const val ACCURACY_SCORE_MAX = 600.0

data class GameState(
    val timeRemaining: Int = 10,
    val score: Double = 0.0,
    val gamePhase: GamePhase = GamePhase.READY,
    val dragStartTime: Long = 0L,
    val dragSpeed: Float = 0f
)

enum class GamePhase {
    READY,
    CHARGING,
    PUNCH,
    RESULT
}

class GameViewModel : ViewModel() {
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    fun startGame() {
        _gameState.value = GameState(gamePhase = GamePhase.CHARGING)
        startTimer()
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (_gameState.value.timeRemaining > 0 && _gameState.value.gamePhase != GamePhase.RESULT) {
                delay(1000)
                _gameState.value = _gameState.value.copy(
                    timeRemaining = _gameState.value.timeRemaining - 1
                )
            }
            if (_gameState.value.gamePhase != GamePhase.RESULT) {
                finishGame()
            }
        }
    }

    fun onDragStart() {
        if (_gameState.value.gamePhase != GamePhase.CHARGING) return
        _gameState.value = _gameState.value.copy(
            dragStartTime = System.currentTimeMillis()
        )
    }

    fun onDragUpdate(speed: Float) {
        if (_gameState.value.gamePhase != GamePhase.CHARGING) return
        _gameState.value = _gameState.value.copy(
            dragSpeed = speed
        )
    }

    fun onPunch(accuracy: Float, speed: Float) {
        if (_gameState.value.gamePhase != GamePhase.CHARGING) return

        // Speed score: 0 or SPEED_SCORE_MAX (400) points
        // If speed >= SPEED_THRESHOLD, give full speed score
        val speedScore = if (speed >= SPEED_THRESHOLD) SPEED_SCORE_MAX else 0.0

        // Accuracy score: 0-600 points (center = better)
        val accuracyScore = accuracy * ACCURACY_SCORE_MAX

        val totalScore = speedScore + accuracyScore

        _gameState.value = _gameState.value.copy(
            score = totalScore.coerceIn(0.0, MAX_SCORE),
            gamePhase = GamePhase.RESULT
        )
    }

    private fun finishGame() {
        _gameState.value = _gameState.value.copy(
            score = 0.0,
            gamePhase = GamePhase.RESULT
        )
    }

    fun resetGame() {
        _gameState.value = GameState()
    }
}
