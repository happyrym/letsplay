package com.rymin.punch.data

import kotlinx.serialization.Serializable

enum class GameType {
    PUNCH,
    DART
}

@Serializable
data class LeaderboardEntry(
    val name: String,
    val score: Double,
    val weaponType: String = "BOXING_GLOVE",  // WeaponType.name for Punch, "DART" for Dart
    val timestamp: Long = System.currentTimeMillis(),
    val gameType: String = "PUNCH"  // GameType.name
)

@Serializable
data class LeaderboardData(
    val punchLeaderboard: List<LeaderboardEntry> = emptyList(),
    val dartLeaderboard: List<LeaderboardEntry> = emptyList()
)
