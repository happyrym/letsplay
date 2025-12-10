package com.rymin.punch.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LeaderboardRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("leaderboard", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "LeaderboardRepo"
        private const val KEY_PUNCH_LEADERBOARD = "punch_leaderboard_data"
        private const val KEY_DART_LEADERBOARD = "dart_leaderboard_data"
        private const val KEY_LEADERBOARD = "leaderboard_data"  // Legacy key
        private const val MAX_ENTRIES = 10
    }

    /**
     * Get punch leaderboard entries sorted by score (descending)
     */
    fun getPunchLeaderboard(): List<LeaderboardEntry> {
        // Try new key first, fall back to legacy key
        var jsonString = prefs.getString(KEY_PUNCH_LEADERBOARD, null)
        if (jsonString == null) {
            jsonString = prefs.getString(KEY_LEADERBOARD, null)
        }
        if (jsonString == null) return emptyList()

        return try {
            json.decodeFromString<List<LeaderboardEntry>>(jsonString)
                .sortedByDescending { it.score }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get dart leaderboard entries sorted by score (descending)
     */
    fun getDartLeaderboard(): List<LeaderboardEntry> {
        val jsonString = prefs.getString(KEY_DART_LEADERBOARD, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<LeaderboardEntry>>(jsonString)
                .sortedByDescending { it.score }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get combined leaderboard data for P2P transmission
     */
    fun getLeaderboardData(): LeaderboardData {
        return LeaderboardData(
            punchLeaderboard = getPunchLeaderboard(),
            dartLeaderboard = getDartLeaderboard()
        )
    }

    /**
     * Legacy method - returns punch leaderboard for backward compatibility
     */
    fun getLeaderboard(): List<LeaderboardEntry> = getPunchLeaderboard()

    /**
     * Check if a score qualifies for the punch leaderboard (top 10)
     */
    fun isTopScore(score: Double): Boolean = isTopPunchScore(score)

    fun isTopPunchScore(score: Double): Boolean {
        val leaderboard = getPunchLeaderboard()
        if (leaderboard.size < MAX_ENTRIES) return true
        return score > (leaderboard.lastOrNull()?.score ?: 0.0)
    }

    fun isTopDartScore(score: Int): Boolean {
        val leaderboard = getDartLeaderboard()
        if (leaderboard.size < MAX_ENTRIES) return true
        return score > (leaderboard.lastOrNull()?.score?.toInt() ?: 0)
    }

    /**
     * Add a new entry to the punch leaderboard
     */
    fun addEntry(name: String, score: Double, weaponType: String = "BOXING_GLOVE") {
        addPunchEntry(name, score, weaponType)
    }

    fun addPunchEntry(name: String, score: Double, weaponType: String = "BOXING_GLOVE") {
        val currentLeaderboard = getPunchLeaderboard().toMutableList()
        currentLeaderboard.add(LeaderboardEntry(name, score, weaponType, gameType = "PUNCH"))

        val updatedLeaderboard = currentLeaderboard
            .sortedByDescending { it.score }
            .take(MAX_ENTRIES)

        savePunchLeaderboard(updatedLeaderboard)
    }

    fun addDartEntry(name: String, score: Int) {
        val currentLeaderboard = getDartLeaderboard().toMutableList()
        currentLeaderboard.add(LeaderboardEntry(name, score.toDouble(), "DART", gameType = "DART"))

        val updatedLeaderboard = currentLeaderboard
            .sortedByDescending { it.score }
            .take(MAX_ENTRIES)

        saveDartLeaderboard(updatedLeaderboard)
    }

    private fun savePunchLeaderboard(leaderboard: List<LeaderboardEntry>) {
        val jsonString = json.encodeToString(leaderboard)
        prefs.edit().putString(KEY_PUNCH_LEADERBOARD, jsonString).apply()
    }

    private fun saveDartLeaderboard(leaderboard: List<LeaderboardEntry>) {
        val jsonString = json.encodeToString(leaderboard)
        prefs.edit().putString(KEY_DART_LEADERBOARD, jsonString).apply()
    }

    /**
     * Clear all leaderboard data
     */
    fun clearLeaderboard() {
        clearPunchLeaderboard()
        clearDartLeaderboard()
    }

    fun clearPunchLeaderboard() {
        Log.d(TAG, "Clearing punch leaderboard...")
        prefs.edit()
            .remove(KEY_PUNCH_LEADERBOARD)
            .remove(KEY_LEADERBOARD)
            .commit()
    }

    fun clearDartLeaderboard() {
        Log.d(TAG, "Clearing dart leaderboard...")
        prefs.edit().remove(KEY_DART_LEADERBOARD).commit()
    }
}
