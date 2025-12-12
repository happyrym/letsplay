package com.rymin.punch.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LeaderboardRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("leaderboard", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "LeaderboardRepo"
        private const val KEY_PUNCH_LEADERBOARD = "punch_leaderboard_data"
        private const val KEY_DART_LEADERBOARD = "dart_leaderboard_data"
        private const val KEY_LEADERBOARD = "leaderboard_data"  // Legacy key
        private const val MAX_ENTRIES = 10
        private const val HISTORY_FILE_NAME = "letsplay_score_history.csv"
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
        val currentLeaderboard = getPunchLeaderboard()
        
        // 등수 계산 후 히스토리에 저장
        val rank = calculateRank(score, currentLeaderboard)
        addToHistory(name, score, rank, "PUNCH", weaponType)
        
        // 리더보드 업데이트
        val updatedLeaderboard = (currentLeaderboard + LeaderboardEntry(name, score, weaponType, gameType = "PUNCH"))
            .sortedByDescending { it.score }
            .take(MAX_ENTRIES)

        savePunchLeaderboard(updatedLeaderboard)
    }

    fun addDartEntry(name: String, score: Int) {
        val currentLeaderboard = getDartLeaderboard()
        
        // 등수 계산 후 히스토리에 저장
        val rank = calculateRank(score.toDouble(), currentLeaderboard)
        addToHistory(name, score.toDouble(), rank, "DART", "")
        
        // 리더보드 업데이트
        val updatedLeaderboard = (currentLeaderboard + LeaderboardEntry(name, score.toDouble(), "DART", gameType = "DART"))
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

    // ==================== 히스토리 기능 ====================

    /**
     * 히스토리 CSV 파일 경로
     * - Android 10+: 앱 전용 외부 저장소 (권한 불필요, 파일 탐색기에서 접근 가능)
     * - 경로: Android/data/com.rymin.punch/files/Documents/
     */
    private fun getHistoryFile(): File {
        // 앱 전용 외부 저장소 사용 (권한 불필요)
        val documentsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: appContext.filesDir  // fallback to internal storage
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }
        return File(documentsDir, HISTORY_FILE_NAME)
    }

    /**
     * 점수 히스토리에 기록 추가 (CSV 파일)
     * 형식: timestamp,datetime,gameType,name,score,rank,weaponType
     */
    private fun addToHistory(name: String, score: Double, rank: Int, gameType: String, weaponType: String = "") {
        try {
            val file = getHistoryFile()
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val datetime = dateFormat.format(Date(timestamp))
            
            // CSV 헤더 추가 (파일이 없거나 비어있을 때)
            if (!file.exists() || file.length() == 0L) {
                file.writeText("timestamp,datetime,gameType,name,score,rank,weaponType\n")
            }
            
            // 데이터 행 추가
            val csvLine = "$timestamp,$datetime,$gameType,$name,$score,$rank,$weaponType\n"
            file.appendText(csvLine)
            
            Log.d(TAG, "History saved: $name - $score (rank: $rank)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save history: ${e.message}")
        }
    }

    /**
     * 특정 점수의 현재 등수 계산
     */
    private fun calculateRank(score: Double, leaderboard: List<LeaderboardEntry>): Int {
        if (leaderboard.isEmpty()) return 1
        
        val sortedScores = leaderboard.map { it.score }.sortedDescending()
        for (i in sortedScores.indices) {
            if (score > sortedScores[i]) return i + 1
        }
        
        return if (sortedScores.size < MAX_ENTRIES) sortedScores.size + 1 else 0
    }

    /**
     * 전체 히스토리 조회 (CSV 파싱)
     */
    fun getScoreHistory(): List<ScoreHistoryEntry> {
        try {
            val file = getHistoryFile()
            if (!file.exists()) return emptyList()

            return file.readLines()
                .drop(1)  // 헤더 스킵
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try {
                        val parts = line.split(",")
                        if (parts.size >= 6) {
                            ScoreHistoryEntry(
                                name = parts[3],
                                score = parts[4].toDouble(),
                                rank = parts[5].toIntOrNull() ?: 0,
                                gameType = parts[2],
                                weaponType = parts.getOrElse(6) { "" },
                                timestamp = parts[0].toLongOrNull() ?: 0L
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read history: ${e.message}")
            return emptyList()
        }
    }

    /**
     * 펀치 히스토리만 조회
     */
    fun getPunchHistory(): List<ScoreHistoryEntry> {
        return getScoreHistory().filter { it.gameType == "PUNCH" }
    }

    /**
     * 다트 히스토리만 조회
     */
    fun getDartHistory(): List<ScoreHistoryEntry> {
        return getScoreHistory().filter { it.gameType == "DART" }
    }

    /**
     * 히스토리 파일 경로 반환 (공유/백업용)
     */
    fun getHistoryFilePath(): String {
        return getHistoryFile().absolutePath
    }

    /**
     * 히스토리 초기화
     */
    fun clearHistory() {
        try {
            val file = getHistoryFile()
            if (file.exists()) {
                file.delete()
            }
            Log.d(TAG, "History cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear history: ${e.message}")
        }
    }
}
