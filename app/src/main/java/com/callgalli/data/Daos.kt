package com.callgalli.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY displayName COLLATE NOCASE, number")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE alwaysAllowed = 1 ORDER BY displayName COLLATE NOCASE, number")
    fun observeAlwaysAllowed(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE number = :number")
    suspend fun get(number: String): RuleEntity?

    @Upsert
    suspend fun upsert(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE number = :number")
    suspend fun delete(number: String)

    @Query("DELETE FROM rules")
    suspend fun clear()
}

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage WHERE dayKey = :dayKey AND number = :number")
    suspend fun get(dayKey: String, number: String): DailyUsageEntity?

    @Query("SELECT * FROM daily_usage WHERE dayKey = :dayKey ORDER BY lastCallAt DESC")
    fun observeDay(dayKey: String): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE number = :number AND dayKey >= :fromDayKey ORDER BY dayKey DESC")
    fun observeHistory(number: String, fromDayKey: String): Flow<List<DailyUsageEntity>>

    @Upsert
    suspend fun upsert(usage: DailyUsageEntity)

    @Query("DELETE FROM daily_usage WHERE dayKey = :dayKey AND number = :number")
    suspend fun delete(dayKey: String, number: String)

    @Query("DELETE FROM daily_usage")
    suspend fun clear()
}

@Dao
interface CallSessionDao {
    @Insert
    suspend fun insert(session: CallSessionEntity): Long

    @Update
    suspend fun update(session: CallSessionEntity)

    @Query("SELECT * FROM call_sessions WHERE id = :id")
    suspend fun get(id: Long): CallSessionEntity?

    @Query("UPDATE call_sessions SET activeMillis = :activeMillis, lastHeartbeatAt = :at WHERE id = :id")
    suspend fun heartbeat(id: Long, activeMillis: Long, at: Long)

    /** Sessions that never got an end time (e.g. the phone restarted mid-call). */
    @Query("SELECT * FROM call_sessions WHERE endedAt IS NULL")
    suspend fun openSessions(): List<CallSessionEntity>

    @Query("SELECT * FROM call_sessions WHERE dayKey = :dayKey ORDER BY startedAt DESC")
    fun observeDay(dayKey: String): Flow<List<CallSessionEntity>>

    @Query(
        """
        SELECT number, MAX(displayName) AS displayName, COUNT(*) AS count, MAX(startedAt) AS lastAt
        FROM call_sessions
        WHERE dayKey = :dayKey AND outcome = 'BLOCKED_BY_LIMIT'
        GROUP BY number
        ORDER BY lastAt DESC
        """
    )
    fun observeBlockedSummary(dayKey: String): Flow<List<BlockedSummary>>

    @Query("SELECT * FROM call_sessions WHERE number = :number AND dayKey >= :fromDayKey ORDER BY startedAt DESC")
    fun observeNumberSessions(number: String, fromDayKey: String): Flow<List<CallSessionEntity>>

    @Query("DELETE FROM call_sessions")
    suspend fun clear()
}
