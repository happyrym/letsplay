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

/**
 * 점수 히스토리 엔트리 - 모든 플레이 기록 저장
 */
@Serializable
data class ScoreHistoryEntry(
    val name: String,
    val score: Double,
    val rank: Int,               // 해당 시점의 등수 (1~10, 10위권 밖이면 0)
    val gameType: String,        // "PUNCH" or "DART"
    val weaponType: String = "", // Punch 전용
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 차트용 히스토리 데이터
 */
@Serializable
data class ScoreHistoryData(
    val punchHistory: List<ScoreHistoryEntry> = emptyList(),
    val dartHistory: List<ScoreHistoryEntry> = emptyList()
)
