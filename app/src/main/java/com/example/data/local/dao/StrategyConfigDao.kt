package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.StrategyConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StrategyConfigDao {
    @Query("SELECT * FROM strategy_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<StrategyConfigEntity?>

    @Query("SELECT * FROM strategy_config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): StrategyConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: StrategyConfigEntity)

    @Update
    suspend fun update(config: StrategyConfigEntity)
}
