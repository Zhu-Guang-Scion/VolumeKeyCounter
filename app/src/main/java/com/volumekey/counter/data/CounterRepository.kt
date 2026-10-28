package com.volumekey.counter.data

import androidx.room.withTransaction

/**
 * 计数仓库。所有改变计数值的操作都在数据库事务内完成，
 * 保证「计数器数值」与「事件日志」原子一致，进程被杀不丢数。
 */
class CounterRepository(
    private val db: AppDatabase,
    private val dao: CounterDao
) {

    fun observeCounter(id: Long) = dao.observeCounter(id)

    fun observeCheckpoints(counterId: Long) = dao.observeCheckpoints(counterId)

    fun observeRecentEvents(counterId: Long, limit: Int = 50) = dao.observeRecentEvents(counterId, limit)

    suspend fun getCounter(id: Long): Counter? = dao.getCounter(id)

    suspend fun createCounter(name: String): Long = dao.insertCounter(Counter(name = name))

    /** 加/减计数（事务内更新数值 + 落事件），返回新计数值 */
    suspend fun adjust(counterId: Long, delta: Long, source: String): Long? = db.withTransaction {
        val c = dao.getCounter(counterId) ?: return@withTransaction null
        val newValue = c.value + delta
        val now = System.currentTimeMillis()
        dao.updateCounterValue(counterId, newValue, now)
        dao.insertEvent(
            CountEvent(counterId = counterId, delta = delta, valueAfter = newValue, timestamp = now, source = source)
        )
        newValue
    }

    suspend fun saveCheckpoint(counterId: Long, name: String, value: Long): Long =
        dao.insertCheckpoint(Checkpoint(counterId = counterId, name = name, value = value))

    /** 回退到检查点数值：以反向事件记录，不抹除历史 */
    suspend fun rollback(counterId: Long, checkpoint: Checkpoint): Long? = db.withTransaction {
        val c = dao.getCounter(counterId) ?: return@withTransaction null
        val now = System.currentTimeMillis()
        dao.updateCounterValue(counterId, checkpoint.value, now)
        dao.insertEvent(
            CountEvent(
                counterId = counterId,
                delta = checkpoint.value - c.value,
                valueAfter = checkpoint.value,
                timestamp = now,
                source = SOURCE_ROLLBACK
            )
        )
        checkpoint.value
    }

    suspend fun deleteCheckpoint(checkpoint: Checkpoint) = dao.deleteCheckpoint(checkpoint)

    fun observeAllCounters() = dao.observeAllCounters()

    suspend fun renameCounter(id: Long, name: String) =
        dao.renameCounter(id, name, System.currentTimeMillis())

    /** 连同事件日志一并删除（检查点由外键级联删除） */
    suspend fun deleteCounterCompletely(counter: Counter) = db.withTransaction {
        dao.deleteEventsOfCounter(counter.id)
        dao.deleteCounter(counter.id)
    }

    suspend fun getAllCheckpoints(counterId: Long) = dao.getAllCheckpoints(counterId)

    suspend fun getAllEvents(counterId: Long) = dao.getAllEvents(counterId)

    companion object {
        const val SOURCE_VOLUME_UP = "volume_up"
        const val SOURCE_VOLUME_DOWN = "volume_down"
        const val SOURCE_BUTTON = "button"
        const val SOURCE_POCKET = "pocket"
        const val SOURCE_ROLLBACK = "rollback"
    }
}
