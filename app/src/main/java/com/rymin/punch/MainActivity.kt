package com.rymin.punch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rymin.punch.data.LeaderboardRepository
import com.rymin.punch.network.NearbyConnectionsManager
import com.rymin.punch.ui.theme.PunchTheme

class MainActivity : ComponentActivity() {
    private lateinit var nearbyManager: NearbyConnectionsManager
    private lateinit var leaderboardRepo: LeaderboardRepository

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startNearbyConnections()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nearbyManager = NearbyConnectionsManager(this)
        leaderboardRepo = LeaderboardRepository(this)

        requestNearbyPermissions()

        setContent {
            PunchTheme {
                PunchGameApp(
                    onLeaderboardUpdated = { sendLeaderboardUpdate() }
                )
            }
        }
    }

    private fun requestNearbyPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startNearbyConnections()
        }
    }

    private fun startNearbyConnections() {
        nearbyManager.startAdvertising("Punch Game")
    }

    private fun sendLeaderboardUpdate() {
        val leaderboard = leaderboardRepo.getLeaderboard()
        nearbyManager.sendLeaderboard(leaderboard)
    }

    override fun onDestroy() {
        super.onDestroy()
        nearbyManager.disconnect()
    }
}

@Composable
fun PunchGameApp(onLeaderboardUpdated: () -> Unit) {
    var currentScreen by remember { mutableStateOf(Screen.SPLASH) }

    // Notify when leaderboard should be updated
    DisposableEffect(Unit) {
        onDispose { }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1a1a2e)
    ) {
        when (currentScreen) {
            Screen.SPLASH -> SplashScreen(onTimeout = { currentScreen = Screen.GAME })
            Screen.GAME -> GameScreen(onLeaderboardUpdated = onLeaderboardUpdated)
        }
    }
}

enum class Screen {
    SPLASH,
    GAME
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🥊 PUNCH!",
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFf39c12)
        )
    }
}
