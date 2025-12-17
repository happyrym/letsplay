package com.rymin.punch.leaderboard

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Firestore Repository for Leaderboard (Real-time)
 */
object FirebaseRepository {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private const val COLLECTION_DART_SCORES = "dart-scores"

    data class ScoreEntry(
        val name: String = "",
        val score: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Get top 10 scores as a Flow (real-time updates)
     * This will automatically update whenever data changes in Firestore
     */
    fun getTopScoresFlow(limit: Int = 10): Flow<List<ScoreEntry>> = callbackFlow {
        val listener = db.collection(COLLECTION_DART_SCORES)
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Don't close, just send empty list on error
                    trySend(emptyList())
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
     * Clear all scores from the leaderboard
     * Returns true if successful, false otherwise
     */
    suspend fun clearAllScores(): Boolean {
        return try {
            val snapshot = db.collection(COLLECTION_DART_SCORES).get().await()
            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
