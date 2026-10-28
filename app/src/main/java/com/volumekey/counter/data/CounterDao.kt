package com.volumekey.counter.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterDao {

    @Query("SELECT * FROM counters WHERE id = :id")
    fun observeCounter(id: Long): Flow<Counter?>

    @Query("SELECT * FROM counters WHERE id = :id")
    suspend fun getCounter(id: Long): Counter?

    @Insert
    suspend fun insertCounter(counter: Counter): Long

    @Query("SELECT * FROM checkpoints WHERE counterId = :counterId ORDER BY createdAt DESC, id DESC")
    fun observeCheckpoints(counterId: Long): Flow<List<Checkpoint>>

    @Insert
    suspend fun insertCheckpoint(checkpoint: Checkpoint): Long

    @Delete
    suspend fun deleteCheckpoint(checkpoint: Checkpoint)

    @Query("SELECT * FROM count_events WHERE counterId = :counterId ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecentEvents(counterId: Long, limit: Int): Flow<List<CountEvent>>

    @Insert
    suspend fun insertEvent(event: CountEvent)

    @Query("UPDATE counters SET value = :value, updatedAt = :at WHERE id = :id")
    suspend fun updateCounterValue(id: Long, value: Long, at: Long)

    @Query("SELECT * FROM counters ORDER BY updatedAt DESC")
    fun observeAllCounters(): Flow<List<Counter>>

    @Query("UPDATE counters SET name = :name, updatedAt = :at WHERE id = :id")
    suspend fun renameCounter(id: Long, name: String, at: Long)

    @Query("DELETE FROM counters WHERE id = :id")
    suspend fun deleteCounter(id: Long)

    @Query("DELETE FROM count_events WHERE counterId = :counterId")
    suspend fun deleteEventsOfCounter(counterId: Long)

    @Query("SELECT * FROM checkpoints WHERE counterId = :counterId ORDER BY createdAt ASC, id ASC")
    suspend fun getAllCheckpoints(counterId: Long): List<Checkpoint>

    @Query("SELECT * FROM count_events WHERE counterId = :counterId ORDER BY timestamp ASC, id ASC")
    suspend fun getAllEvents(counterId: Long): List<CountEvent>
}
