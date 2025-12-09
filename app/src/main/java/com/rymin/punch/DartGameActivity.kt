package com.rymin.punch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rymin.punch.ui.theme.PunchTheme

class DartGameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PunchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF1a1a2e)
                ) {
                    DartGameScreen(
                        onLeaderboardUpdated = { /* TODO: Nearby sync */ },
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
