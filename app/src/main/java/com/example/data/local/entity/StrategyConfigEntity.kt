package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "strategy_config")
data class StrategyConfigEntity(
    @PrimaryKey val id: Int = 1,
    val initialBank: Double = 10000.0,
    val dailyProfitPercent: Double = 10.0,
    val currentBank: Double = 10000.0,
    val activeDay: Int = 1,
    val currency: String = "Ft",
    val baseStake: Double = 200.0,
    val autoUpdateEnabled: Boolean = true,
    val customUpdateUrl: String = ""
)
