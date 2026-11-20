package com.callgalli.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.callgalli.rules.NumberDay

/** Per-number rule. v1 only stores "Always Allowed". [number] is normalized digits. */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val number: String,
    val displayName: String?,
    val alwaysAllowed: Boolean,
    val updatedAt: Long,
)

/** One call (answered, missed, cut by limit or blocked). */
@Entity(
    tableName = "call_sessions",
    indices = [Index("dayKey"), Index("number"), Index("endedAt")],
)
data class CallSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val displayName: String?,
    /** [com.callgalli.rules.Direction] name. */
    val direction: String,
    val dayKey: String,
    val startedAt: Long,
    val connectedAt: Long? = null,
    val endedAt: Long? = null,
    /** Talk time while active (hold time excluded). */
    val activeMillis: Long = 0,
    val limitMillis: Long? = null,
    /** [CallOutcome] name. */
    val outcome: String = CallOutcome.IN_PROGRESS.name,
    /** Saved every few seconds during a call so a reboot can close the session cleanly. */
    val lastHeartbeatAt: Long = startedAt,
)

enum class CallOutcome {
    IN_PROGRESS,
    ENDED,
    ENDED_BY_LIMIT,
    NOT_CONNECTED,
    BLOCKED_BY_LIMIT,
    /** The phone restarted or the app was killed during the call. */
    INTERRUPTED,
}

/** One number's counts for one day. The midnight reset is just a new [dayKey]. */
@Entity(tableName = "daily_usage", primaryKeys = ["dayKey", "number"], indices = [Index("number")])
data class DailyUsageEntity(
    val dayKey: String,
    val number: String,
    val displayName: String?,
    val answeredCalls: Int,
    val blockedCalls: Int,
    val talkMillis: Long,
    val lastCallAt: Long,
) {
    fun toNumberDay() = NumberDay(number, dayKey, answeredCalls, blockedCalls, talkMillis)

    companion object {
        fun from(day: NumberDay, displayName: String?, lastCallAt: Long) = DailyUsageEntity(
            dayKey = day.dayKey,
            number = day.number,
            displayName = displayName,
            answeredCalls = day.answeredCalls,
            blockedCalls = day.blockedCalls,
            talkMillis = day.talkMillis,
            lastCallAt = lastCallAt,
        )
    }
}

/** Row for the Blocked Log: one per number, today. */
data class BlockedSummary(
    val number: String,
    val displayName: String?,
    val count: Int,
    val lastAt: Long,
)
