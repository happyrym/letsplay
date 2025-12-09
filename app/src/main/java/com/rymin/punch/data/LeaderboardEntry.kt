package com.rymin.punch.data

import kotlinx.serialization.Serializable

@Serializable
data class LeaderboardEntry(
    val name: String,
    val score: Double,
    val timestamp: Long = System.currentTimeMillis()
)
