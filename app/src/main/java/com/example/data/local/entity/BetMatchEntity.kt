package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bet_matches")
data class BetMatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayNumber: Int? = null, // Optional connection to a day in the 100-day plan
    val roundNumber: Int = 1, // 1st round, 2nd round, etc.
    val sport: String = "Labdarúgás", // Foci, Tenisz, Kosárlabda, Jégkorong, etc.
    val league: String = "Bajnokság",
    val homeTeam: String,
    val awayTeam: String,
    val matchTime: String = "", // e.g. "2026-10-04 20:45"
    val market: String = "1X2", // 1X2, Gólok száma, Mindkét csapat szerez gólt, Hendikep
    val tip: String = "Hazai",
    val odds: Double = 1.50,
    val stake: Double = 200.0,
    val status: String = "PENDING", // PENDING, LIVE, WON, LOST, VOID
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val liveMinute: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
