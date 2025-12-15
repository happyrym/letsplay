package com.rymin.punch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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
                val connectionStatus by nearbyManager.connectionStatus.collectAsState()

                PunchGameApp(
                    onLeaderboardUpdated = { sendLeaderboardUpdate() },
                    connectionStatus = connectionStatus,
                    onStartDartGame = {
                        startActivity(Intent(this@MainActivity, DartGameActivity::class.java))
                    }
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
fun PunchGameApp(
    onLeaderboardUpdated: () -> Unit,
    connectionStatus: NearbyConnectionsManager.ConnectionStatus,
    onStartDartGame: () -> Unit = {}
) {
    // Splash 없이 바로 다트 게임 시작
    LaunchedEffect(Unit) {
        onStartDartGame()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1a1a2e)
    ) {
        // 로딩 화면 (다트 게임 Activity가 시작되기 전 잠깐 보임)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎯",
                fontSize = 80.sp
            )
        }
    }
}

@Composable
fun ConnectionStatusIndicator(
    status: NearbyConnectionsManager.ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (status) {
        NearbyConnectionsManager.ConnectionStatus.CONNECTED -> Color(0xFF27ae60) to "📱 연결됨"
        NearbyConnectionsManager.ConnectionStatus.ADVERTISING -> Color(0xFFf39c12) to "📡 대기중"
        NearbyConnectionsManager.ConnectionStatus.CONNECTING -> Color(0xFF3498db) to "🔄 연결중"
        NearbyConnectionsManager.ConnectionStatus.DISCONNECTED -> Color(0xFFe74c3c) to "❌ 미연결"
    }

    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color.White
        )
    }
}

enum class Screen {
    SPLASH,
    GAME_SELECT,
    PUNCH_GAME,
    DART_GAME
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🎮",
                fontSize = 80.sp
            )
            Text(
                text = "LET'S PLAY!",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFf39c12)
            )
        }
    }
}

@Composable
fun GameSelectScreen(
    onPunchSelected: () -> Unit,
    onDartSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🎮 SELECT GAME",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFf39c12),
            modifier = Modifier.padding(bottom = 48.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Punch Game Card
            GameCard(
                emoji = "🥊",
                title = "PUNCH",
                description = "펀치머신에\n강펀치!",
                backgroundColor = Color(0xFFe74c3c),
                onClick = onPunchSelected
            )

            // Dart Game Card
            GameCard(
                emoji = "🎯",
                title = "DART",
                description = "다트를 던져\n과녁 맞추기!",
                backgroundColor = Color(0xFF3498db),
                onClick = onDartSelected
            )
        }
    }
}

@Composable
fun GameCard(
    emoji: String,
    title: String,
    description: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .height(350.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji,
                fontSize = 80.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = description,
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}
