package com.rymin.punch.data

import kotlinx.serialization.Serializable

@Serializable
data class LeaderboardEntry(
    val name: String,
    val score: Double,
    val weaponType: String = "BOXING_GLOVE",  // WeaponType.name
    val timestamp: Long = System.currentTimeMillis()
)
