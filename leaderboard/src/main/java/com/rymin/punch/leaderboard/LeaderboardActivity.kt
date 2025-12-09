package com.rymin.punch.leaderboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.random.Random
import androidx.compose.runtime.collectAsState

@Serializable
data class LeaderboardEntry(
    val name: String,
    val score: Double,
    val weaponType: String = "BOXING_GLOVE",
    val timestamp: Long = System.currentTimeMillis()
)

data class CelebrationParticle(
    val id: Int,
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val color: Color,
    val createdAt: Long
)

// Map weapon type string to drawable resource
fun getWeaponDrawable(weaponType: String): Int {
    return when (weaponType) {
        "BOXING_GLOVE" -> R.drawable.ic_boxing_glove
        "FIST" -> R.drawable.ic_fist
        "SANTA_GLOVE" -> R.drawable.ic_santa_glove
        "REINDEER_HOOF" -> R.drawable.ic_reindeer_hoof
        "DOG_PAW" -> R.drawable.ic_dog_paw
        "CAT_PAW" -> R.drawable.ic_cat_paw
        else -> R.drawable.ic_boxing_glove
    }
}

class LeaderboardActivity : ComponentActivity() {
    private lateinit var nearbyClient: NearbyConnectionsClient

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startNearbyDiscovery()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nearbyClient = NearbyConnectionsClient(this)
        requestNearbyPermissions()

        setContent {
            LeaderboardTheme {
                val leaderboard by nearbyClient.leaderboardFlow.collectAsState()
                val isConnected by nearbyClient.connectionStatus.collectAsState()

                LeaderboardScreen(
                    leaderboard = leaderboard,
                    isConnected = isConnected
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
            startNearbyDiscovery()
        }
    }

    private fun startNearbyDiscovery() {
        nearbyClient.startDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        nearbyClient.disconnect()
    }
}

@Composable
fun LeaderboardTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF1a1a2e)
        ) {
            content()
        }
    }
}

@Composable
fun LeaderboardScreen(
    leaderboard: List<LeaderboardEntry> = emptyList(),
    isConnected: Boolean = false
) {
    var previousLeaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var showCelebration by remember { mutableStateOf(false) }
    var newFirstPlaceEntry by remember { mutableStateOf<LeaderboardEntry?>(null) }
    var particles by remember { mutableStateOf<List<CelebrationParticle>>(emptyList()) }

    // Particle animation
    LaunchedEffect(particles) {
        if (particles.isNotEmpty()) {
            delay(16)
            val currentTime = System.currentTimeMillis()
            particles = particles.filter { currentTime - it.createdAt < 3000 }
        }
    }

    // Detect new first place when leaderboard updates
    LaunchedEffect(leaderboard) {
        if (leaderboard.isNotEmpty()) {
            val previousFirstPlace = previousLeaderboard.firstOrNull()
            val newFirstPlace = leaderboard.firstOrNull()

            // Check if there's a new first place
            if (newFirstPlace != null &&
                (previousFirstPlace == null ||
                 previousFirstPlace.name != newFirstPlace.name ||
                 previousFirstPlace.score != newFirstPlace.score)) {
                showCelebration = true
                newFirstPlaceEntry = newFirstPlace

                // Create celebration particles
                val screenWidth = 1080f
                val screenHeight = 1920f

                val celebrationParticles = (0..100).map { i ->
                    val angle = (i * 360.0 / 100.0) * Math.PI / 180.0
                    val speed = Random.nextFloat() * 400f + 200f
                    CelebrationParticle(
                        id = Random.nextInt(),
                        startX = screenWidth / 2,
                        startY = screenHeight / 3,
                        velocityX = (Math.cos(angle) * speed).toFloat(),
                        velocityY = (Math.sin(angle) * speed).toFloat(),
                        color = when {
                            i % 4 == 0 -> Color(0xFFFFD700)
                            i % 4 == 1 -> Color(0xFFf39c12)
                            i % 4 == 2 -> Color(0xFFff6b6b)
                            else -> Color(0xFFfeca57)
                        },
                        createdAt = System.currentTimeMillis()
                    )
                }
                particles = celebrationParticles
            }
        }
        // Always update previousLeaderboard (including empty list for reset)
        previousLeaderboard = leaderboard
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main leaderboard content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp)
        ) {
            // Connection status indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (isConnected) Color(0xFF27ae60) else Color(0xFFe74c3c),
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isConnected) "CONNECTED" else "SEARCHING...",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isConnected) Color(0xFF27ae60) else Color(0xFFe74c3c)
                )
            }

            // Title
            Text(
                text = "🥊 PUNCH",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFf39c12),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Leaderboard List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(leaderboard.take(10)) { index, entry ->
                    LeaderboardItem(
                        entry = entry,
                        rank = index + 1,
                        isNewFirstPlace = showCelebration && index == 0
                    )
                }
            }
        }

        // Celebration particles
        particles.forEach { particle ->
            val elapsed = System.currentTimeMillis() - particle.createdAt
            if (elapsed >= 0) {
                val progress = (elapsed / 3000f).coerceIn(0f, 1f)
                val alpha = (1f - progress).coerceIn(0f, 1f)

                val gravity = 500f * progress * progress // Add gravity effect
                val size = (16.dp.value * (1f - progress * 0.5f)).coerceAtLeast(4f)

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (particle.startX + particle.velocityX * progress).roundToInt(),
                                (particle.startY + particle.velocityY * progress + gravity).roundToInt()
                            )
                        }
                        .size(size.dp)
                        .alpha(alpha)
                        .background(
                            particle.color,
                            shape = CircleShape
                        )
                )
            }
        }

        // Celebration overlay
        if (showCelebration && newFirstPlaceEntry != null) {
            CelebrationOverlay(
                entry = newFirstPlaceEntry!!,
                onDismiss = {
                    showCelebration = false
                    newFirstPlaceEntry = null
                }
            )
        }
    }
}

@Composable
fun LeaderboardItem(
    entry: LeaderboardEntry,
    rank: Int,
    isNewFirstPlace: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isNewFirstPlace) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val backgroundColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> Color(0xFF16213e)
    }

    val textColor = when (rank) {
        1, 2, 3 -> Color.Black
        else -> Color.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .background(backgroundColor, shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank
        Text(
            text = "#$rank",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.width(50.dp)
        )

        // Weapon icon
        Image(
            painter = painterResource(id = getWeaponDrawable(entry.weaponType)),
            contentDescription = entry.weaponType,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.2f))
                .padding(4.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Name
        Text(
            text = entry.name,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.weight(1f)
        )

        // Score
        Text(
            text = String.format("%.2f", entry.score),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun CelebrationOverlay(
    entry: LeaderboardEntry,
    onDismiss: () -> Unit
) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        delay(3000)
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(500)
        )
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha.value)
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.scale(scale.value)
        ) {
            Text(
                text = "🏆 NEW CHAMPION! 🏆",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFFD700)
            )

            // Weapon icon
            Image(
                painter = painterResource(id = getWeaponDrawable(entry.weaponType)),
                contentDescription = entry.weaponType,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(8.dp)
            )

            Text(
                text = entry.name,
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Text(
                text = String.format("%.2f", entry.score),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFf39c12)
            )
        }
    }
}
