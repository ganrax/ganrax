package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "day_plans")
data class DayPlanEntity(
    @PrimaryKey val dayNumber: Int,
    val targetBank: Double,
    val minRoll: Double,
    val maxRoll: Double,
    val targetProfit: Double,
    val actualBalance: Double? = null,
    val actualProfit: Double? = null,
    val status: String = "PENDING", // PENDING, COMPLETED, FAILED, IN_PROGRESS
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
