package com.shibill.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RawEventDao {

    @Insert
    suspend fun insert(event: RawEventEntity): Long

    @Query("DELETE FROM raw_events WHERE created_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM raw_events")
    suspend fun count(): Int

    @Query("DELETE FROM raw_events")
    suspend fun clear()
}
