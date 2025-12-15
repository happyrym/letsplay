package com.rymin.punch.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Firestore Repository for Leaderboard
 */
object FirebaseRepository {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private const val COLLECTION_DART_SCORES = "dart-scores"
    private const val COLLECTION_WALL_OF_SHAME = "wall-of-shame"

    data class ScoreEntry(
        val name: String = "",
        val score: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class AbuserEntry(
        val name: String = "",
        val reason: String = "",
        val attemptedScore: Int = 0,
        val detail: String = "",
        val game: String = "dart",
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Save score to Firestore
     */
    suspend fun saveScore(name: String, score: Int): Result<Unit> {
        return try {
            val entry = hashMapOf(
                "name" to name,
                "score" to score,
                "timestamp" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_DART_SCORES).add(entry).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get top 10 scores as a Flow (real-time updates)
     */
    fun getTopScoresFlow(limit: Int = 10): Flow<List<ScoreEntry>> = callbackFlow {
        val listener = db.collection(COLLECTION_DART_SCORES)
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val scores = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        ScoreEntry(
                            name = doc.getString("name") ?: "",
                            score = (doc.getLong("score") ?: 0).toInt(),
                            timestamp = doc.getLong("timestamp") ?: 0
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                trySend(scores)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get top scores once (not real-time)
     */
    suspend fun getTopScores(limit: Int = 10): List<ScoreEntry> {
        return try {
            val snapshot = db.collection(COLLECTION_DART_SCORES)
                .orderBy("score", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    ScoreEntry(
                        name = doc.getString("name") ?: "",
                        score = (doc.getLong("score") ?: 0).toInt(),
                        timestamp = doc.getLong("timestamp") ?: 0
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if score qualifies for top 10
     */
    suspend fun isTopScore(score: Int, limit: Int = 10): Boolean {
        val topScores = getTopScores(limit)
        if (topScores.size < limit) return true
        return score > (topScores.lastOrNull()?.score ?: 0)
    }

    /**
     * Register abuser to Wall of Shame
     */
    suspend fun registerAbuser(
        name: String,
        reason: AbuserDetector.AbuseReason,
        attemptedScore: Int = 0,
        detail: String = ""
    ): Result<Unit> {
        return try {
            val entry = hashMapOf(
                "name" to name.ifEmpty { "Anonymous Cheater" },
                "reason" to reason.code,
                "attemptedScore" to attemptedScore,
                "detail" to detail,
                "game" to "dart",
                "timestamp" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_WALL_OF_SHAME).add(entry).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear all scores (admin function)
     */
    suspend fun clearAllScores(): Result<Unit> {
        return try {
            val snapshot = db.collection(COLLECTION_DART_SCORES).get().await()
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
