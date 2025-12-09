package com.rymin.punch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

        // Speed score: 0-500 points (faster = better)
        val speedScore = (speed * 5.0).coerceIn(0.0, 500.0)

        // Accuracy score: 0-499 points (center = better)
        val accuracyScore = accuracy * 499.0

        val totalScore = speedScore + accuracyScore

        _gameState.value = _gameState.value.copy(
            score = totalScore.coerceIn(0.0, 999.0),
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
