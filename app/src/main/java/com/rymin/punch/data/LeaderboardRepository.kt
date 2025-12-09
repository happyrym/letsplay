package com.rymin.punch.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LeaderboardRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("leaderboard", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEY_LEADERBOARD = "leaderboard_data"
        private const val MAX_ENTRIES = 10
    }

    /**
     * Get all leaderboard entries sorted by score (descending)
     */
    fun getLeaderboard(): List<LeaderboardEntry> {
        val jsonString = prefs.getString(KEY_LEADERBOARD, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<LeaderboardEntry>>(jsonString)
                .sortedByDescending { it.score }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if a score qualifies for the leaderboard (top 10)
     */
    fun isTopScore(score: Double): Boolean {
        val leaderboard = getLeaderboard()
        if (leaderboard.size < MAX_ENTRIES) return true
        return score > (leaderboard.lastOrNull()?.score ?: 0.0)
    }

    /**
     * Add a new entry to the leaderboard
     * Automatically keeps only top 10 scores
     */
    fun addEntry(name: String, score: Double) {
        val currentLeaderboard = getLeaderboard().toMutableList()
        currentLeaderboard.add(LeaderboardEntry(name, score))

        // Sort by score descending and keep only top 10
        val updatedLeaderboard = currentLeaderboard
            .sortedByDescending { it.score }
            .take(MAX_ENTRIES)

        saveLeaderboard(updatedLeaderboard)
    }

    /**
     * Save the entire leaderboard to SharedPreferences
     */
    private fun saveLeaderboard(leaderboard: List<LeaderboardEntry>) {
        val jsonString = json.encodeToString(leaderboard)
        prefs.edit().putString(KEY_LEADERBOARD, jsonString).apply()
    }

    /**
     * Clear all leaderboard data
     */
    fun clearLeaderboard() {
        prefs.edit().remove(KEY_LEADERBOARD).apply()
    }
}
