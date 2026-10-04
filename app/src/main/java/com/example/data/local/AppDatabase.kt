package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.BetMatchDao
import com.example.data.local.dao.DayPlanDao
import com.example.data.local.dao.StrategyConfigDao
import com.example.data.local.entity.BetMatchEntity
import com.example.data.local.entity.DayPlanEntity
import com.example.data.local.entity.StrategyConfigEntity

@Database(
    entities = [
        DayPlanEntity::class,
        BetMatchEntity::class,
        StrategyConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dayPlanDao(): DayPlanDao
    abstract fun betMatchDao(): BetMatchDao
    abstract fun strategyConfigDao(): StrategyConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "betting_strategy_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
