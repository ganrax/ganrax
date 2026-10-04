package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.BetMatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BetMatchDao {
    @Query("SELECT * FROM bet_matches ORDER BY createdAt DESC")
    fun getAllMatches(): Flow<List<BetMatchEntity>>

    @Query("SELECT * FROM bet_matches WHERE dayNumber = :dayNumber ORDER BY roundNumber ASC, createdAt ASC")
    fun getMatchesForDay(dayNumber: Int): Flow<List<BetMatchEntity>>

    @Query("SELECT * FROM bet_matches WHERE status = :status ORDER BY createdAt DESC")
    fun getMatchesByStatus(status: String): Flow<List<BetMatchEntity>>

    @Query("SELECT * FROM bet_matches WHERE id = :id LIMIT 1")
    suspend fun getMatchById(id: Long): BetMatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: BetMatchEntity): Long

    @Update
    suspend fun updateMatch(match: BetMatchEntity)

    @Delete
    suspend fun deleteMatch(match: BetMatchEntity)

    @Query("DELETE FROM bet_matches WHERE id = :id")
    suspend fun deleteMatchById(id: Long)

    @Query("UPDATE bet_matches SET status = :status, homeScore = :homeScore, awayScore = :awayScore, liveMinute = :minute WHERE id = :id")
    suspend fun updateScoreAndStatus(id: Long, status: String, homeScore: Int?, awayScore: Int?, minute: String?)
}
