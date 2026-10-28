package com.volumekey.counter.data

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 激活计数器的「取或建」统一入口。
 * Activity 的 ViewModel 与前台服务共用，互斥防止首次使用时重复创建计数器。
 */
object CounterCoordinator {

    private val mutex = Mutex()

    /**
     * 取或建激活计数器。对 DataStore 中已有 id 做存在性校验，
     * 指向已删除（僵尸）id 时自动切到最新计数器或重建，避免计数静默丢失。
     */
    suspend fun ensureActiveCounter(context: Context): Long = mutex.withLock {
        val settings = SettingsStore(context)
        val db = AppDatabase.getInstance(context)
        val repo = CounterRepository(db, db.counterDao())
        val stored = settings.getActiveCounterId()
        if (stored != null && repo.getCounter(stored) != null) return@withLock stored
        val existing = repo.observeAllCounters().first().firstOrNull()
        if (existing != null) {
            settings.setActiveCounterId(existing.id)
            return@withLock existing.id
        }
        val id = repo.createCounter("计数器 1")
        settings.setActiveCounterId(id)
        id
    }
}
