package com.rymin.punch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rymin.punch.data.LeaderboardRepository
import com.rymin.punch.network.NearbyConnectionsManager
import com.rymin.punch.ui.theme.PunchTheme

class DartGameActivity : ComponentActivity() {
    private lateinit var leaderboardRepository: LeaderboardRepository
    private var nearbyManager: NearbyConnectionsManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        leaderboardRepository = LeaderboardRepository(this)

        // Get NearbyConnectionsManager from MainActivity if available
        // For now, we'll create a new one if needed
        nearbyManager = NearbyConnectionsManager(this)

        setContent {
            PunchTheme {
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
                            leaderboardRepository.addDartEntry(name, score)
                            // Send updated leaderboard to connected display
                            nearbyManager?.sendLeaderboardData(leaderboardRepository.getLeaderboardData())
                        },
                        onLeaderboardUpdated = {
                            nearbyManager?.sendLeaderboardData(leaderboardRepository.getLeaderboardData())
                        },
                        onBack = { finish() }
                    )
                }
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
