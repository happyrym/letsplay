package com.rymin.punch

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.rymin.punch.data.AbuserDetector
import com.rymin.punch.data.FirebaseRepository
import com.rymin.punch.data.LeaderboardRepository
import com.rymin.punch.network.NearbyConnectionsManager
import com.rymin.punch.ui.theme.DartPartyTheme
import kotlinx.coroutines.launch

class DartGameActivity : ComponentActivity() {
    private lateinit var leaderboardRepository: LeaderboardRepository
    private var nearbyManager: NearbyConnectionsManager? = null

    companion object {
        private const val TAG = "DartGameActivity"
        private const val MAX_BASE_SCORE = 180 // 3 darts * 60 (triple 20)
        private const val MAX_SINGLE_DART_SCORE = 60 // Triple 20
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 뒤로가기 버튼 막기
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@DartGameActivity, "😝 메롱~", Toast.LENGTH_SHORT).show()
            }
        })

        leaderboardRepository = LeaderboardRepository(this)

        // Get NearbyConnectionsManager from MainActivity if available
        // For now, we'll create a new one if needed
        nearbyManager = NearbyConnectionsManager(this)

        setContent {
            DartPartyTheme {
                var currentScore by remember { mutableStateOf(0) }
                val isTopScore = leaderboardRepository.isTopDartScore(currentScore)

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = Color(0xFF1a1a2e)
                ) {
                    DartGameScreen(
                        isTopDartScore = isTopScore,
                        onSaveScore = { name, score ->
                            saveScoreWithValidation(name, score)
                        },
                        onLeaderboardUpdated = {
                            nearbyManager?.sendLeaderboardData(leaderboardRepository.getLeaderboardData())
                        },
                        onResetLeaderboard = {
                            leaderboardRepository.clearDartLeaderboard()
                            nearbyManager?.sendLeaderboardData(leaderboardRepository.getLeaderboardData())
                            // Also clear Firebase (optional)
                            lifecycleScope.launch {
                                FirebaseRepository.clearAllScores()
                            }
                        },
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    private fun saveScoreWithValidation(name: String, score: Int) {
        // Skip zero scores
        if (score <= 0) {
            Log.d(TAG, "Skipping zero score")
            return
        }

        // Validate score with abuser detection
        val validation = AbuserDetector.validateScore(score, MAX_BASE_SCORE)

        if (!validation.valid && validation.reason != null) {
            // Abuser detected!
            Log.w(TAG, "Abuser detected: ${validation.reason?.description} - ${validation.detail}")

            lifecycleScope.launch {
                FirebaseRepository.registerAbuser(
                    name = name,
                    reason = validation.reason,
                    attemptedScore = score,
                    detail = validation.detail ?: ""
                )
            }

            Toast.makeText(
                this,
                "🚨 Cheater detected! You've been added to Wall of Shame!",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Valid score - save to local and Firebase
        leaderboardRepository.addDartEntry(name, score)
        nearbyManager?.sendLeaderboardData(leaderboardRepository.getLeaderboardData())

        // Save to Firebase
        lifecycleScope.launch {
            val result = FirebaseRepository.saveScore(name, score)
            if (result.isSuccess) {
                Log.d(TAG, "Score saved to Firebase: $name - $score")
            } else {
                Log.e(TAG, "Failed to save to Firebase", result.exceptionOrNull())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        nearbyManager?.startAdvertising("Dart Game")
    }

    override fun onDestroy() {
        super.onDestroy()
        nearbyManager?.disconnect()
    }
}
