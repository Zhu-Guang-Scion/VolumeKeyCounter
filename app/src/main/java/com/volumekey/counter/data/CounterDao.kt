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

    @Query("DELETE FROM checkpoints WHERE counterId = :counterId")
    suspend fun deleteCheckpointsOfCounter(counterId: Long)

    @Query("SELECT * FROM checkpoints WHERE counterId = :counterId ORDER BY createdAt ASC, id ASC")
    suspend fun getAllCheckpoints(counterId: Long): List<Checkpoint>

    @Query("SELECT * FROM count_events WHERE counterId = :counterId ORDER BY timestamp ASC, id ASC")
    suspend fun getAllEvents(counterId: Long): List<CountEvent>

    // ---- 清单盘点 ----

    @Query("SELECT * FROM inventory_lists ORDER BY createdAt DESC, id DESC")
    fun observeInventoryLists(): Flow<List<InventoryList>>

    @Insert
    suspend fun insertInventoryList(list: InventoryList): Long

    @Query("UPDATE inventory_lists SET itemCount = :count WHERE id = :id")
    suspend fun updateInventoryItemCount(id: Long, count: Int)

    @Query("DELETE FROM inventory_lists WHERE id = :id")
    suspend fun deleteInventoryList(id: Long)

    @Query("SELECT * FROM inventory_items WHERE listId = :listId ORDER BY sortOrder ASC, id ASC")
    fun observeInventoryItems(listId: Long): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun getInventoryItem(id: Long): InventoryItem?

    @Insert
    suspend fun insertInventoryItems(items: List<InventoryItem>)

    @Query("UPDATE inventory_items SET countedQty = :counted, touched = 1 WHERE id = :id")
    suspend fun updateInventoryItemCounted(id: Long, counted: Long)

    @Query("UPDATE inventory_items SET countedQty = :counted, touched = 1, remark = :remark WHERE id = :id")
    suspend fun updateInventoryItemFields(id: Long, counted: Long, remark: String)

    @Query("UPDATE inventory_items SET remark = :remark WHERE id = :id")
    suspend fun updateInventoryItemRemark(id: Long, remark: String)

    @Query("UPDATE inventory_items SET countedQty = 0, touched = 0 WHERE id = :id")
    suspend fun resetInventoryItemTouched(id: Long)

    @Insert
    suspend fun insertInventoryEvent(event: InventoryEvent)

    @Query("SELECT * FROM inventory_events WHERE itemId IN (SELECT id FROM inventory_items WHERE listId = :listId) ORDER BY timestamp ASC, id ASC")
    suspend fun getInventoryEventsOfList(listId: Long): List<InventoryEvent>

    // ---- 水印相机 ----

    @Insert
    suspend fun insertInventoryPhoto(photo: InventoryPhoto): Long

    @Query("SELECT IFNULL(MAX(seq), 0) FROM inventory_photos WHERE itemId = :itemId")
    suspend fun maxPhotoSeq(itemId: Long): Int

    @Query("SELECT itemId, COUNT(*) AS cnt FROM inventory_photos WHERE itemId IN (SELECT id FROM inventory_items WHERE listId = :listId) GROUP BY itemId")
    fun observeInventoryPhotoCounts(listId: Long): Flow<List<ItemPhotoCount>>

    @Query("SELECT * FROM inventory_photos WHERE itemId = :itemId ORDER BY seq ASC, id ASC")
    fun observeInventoryPhotos(itemId: Long): Flow<List<InventoryPhoto>>

    @Query("SELECT * FROM inventory_photos WHERE itemId = :itemId ORDER BY seq ASC, id ASC")
    suspend fun getInventoryPhotos(itemId: Long): List<InventoryPhoto>

    @Query("DELETE FROM inventory_photos WHERE id = :photoId")
    suspend fun deleteInventoryPhoto(photoId: Long)
}

data class ItemPhotoCount(val itemId: Long, val cnt: Int)
