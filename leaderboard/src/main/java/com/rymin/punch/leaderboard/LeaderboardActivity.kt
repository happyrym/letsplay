package com.rymin.punch.leaderboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.random.Random

@Serializable
data class LeaderboardEntry(
    val name: String,
    val score: Double,
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

class LeaderboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LeaderboardTheme {
                LeaderboardScreen()
            }
        }
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
fun LeaderboardScreen() {
    var leaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
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

    // Function to update leaderboard (will be called by P2P connection)
    fun onLeaderboardUpdate(newLeaderboard: List<LeaderboardEntry>) {
        val previousFirstPlace = leaderboard.firstOrNull()
        val newFirstPlace = newLeaderboard.firstOrNull()

        // Check if there's a new first place
        if (newFirstPlace != null &&
            (previousFirstPlace == null ||
             previousFirstPlace.name != newFirstPlace.name ||
             previousFirstPlace.score != newFirstPlace.score)) {
            showCelebration = true
            newFirstPlaceEntry = newFirstPlace

            // Create celebration particles
            val screenWidth = 1080f // Approximate phone width
            val screenHeight = 1920f // Approximate phone height

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
                        i % 4 == 0 -> Color(0xFFFFD700) // Gold
                        i % 4 == 1 -> Color(0xFFf39c12) // Orange
                        i % 4 == 2 -> Color(0xFFff6b6b) // Red
                        else -> Color(0xFFfeca57) // Yellow
                    },
                    createdAt = System.currentTimeMillis()
                )
            }
            particles = celebrationParticles
        }

        leaderboard = newLeaderboard
    }

    // Sample data for testing - TODO: Replace with real P2P data
    LaunchedEffect(Unit) {
        delay(1000)
        onLeaderboardUpdate(
            listOf(
                LeaderboardEntry("Player 1", 999.00),
                LeaderboardEntry("Player 2", 856.47),
                LeaderboardEntry("Player 3", 723.89),
                LeaderboardEntry("Player 4", 687.23),
                LeaderboardEntry("Player 5", 654.12),
                LeaderboardEntry("Player 6", 598.76),
                LeaderboardEntry("Player 7", 532.45),
                LeaderboardEntry("Player 8", 489.34),
                LeaderboardEntry("Player 9", 423.78),
                LeaderboardEntry("Player 10", 367.92)
            )
        )

        // Simulate a new first place after 5 seconds
        delay(5000)
        onLeaderboardUpdate(
            listOf(
                LeaderboardEntry("CHAMPION", 999.99),
                LeaderboardEntry("Player 1", 999.00),
                LeaderboardEntry("Player 2", 856.47),
                LeaderboardEntry("Player 3", 723.89),
                LeaderboardEntry("Player 4", 687.23),
                LeaderboardEntry("Player 5", 654.12),
                LeaderboardEntry("Player 6", 598.76),
                LeaderboardEntry("Player 7", 532.45),
                LeaderboardEntry("Player 8", 489.34),
                LeaderboardEntry("Player 9", 423.78)
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main leaderboard content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp)
        ) {
            // Title
            Text(
                text = "🥊 PUNCH LEADERBOARD",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFf39c12),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
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
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank
        Text(
            text = "#$rank",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.width(60.dp)
        )

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
