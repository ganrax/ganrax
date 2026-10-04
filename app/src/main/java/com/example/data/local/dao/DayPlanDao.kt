package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.DayPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DayPlanDao {
    @Query("SELECT * FROM day_plans ORDER BY dayNumber ASC")
    fun getAllDayPlans(): Flow<List<DayPlanEntity>>

    @Query("SELECT * FROM day_plans WHERE dayNumber = :dayNumber LIMIT 1")
    suspend fun getDayPlan(dayNumber: Int): DayPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<DayPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: DayPlanEntity)

    @Update
    suspend fun update(plan: DayPlanEntity)

    @Query("UPDATE day_plans SET status = :status, actualBalance = :balance, actualProfit = :profit, updatedAt = :timestamp WHERE dayNumber = :dayNumber")
    suspend fun updateDayStatus(dayNumber: Int, status: String, balance: Double?, profit: Double?, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM day_plans")
    suspend fun clearAll()
}
