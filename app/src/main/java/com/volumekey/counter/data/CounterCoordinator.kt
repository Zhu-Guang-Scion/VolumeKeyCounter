package com.volumekey.counter.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 激活计数器的「取或建」统一入口。
 * Activity 的 ViewModel 与前台服务共用，互斥防止首次使用时重复创建计数器。
 */
object CounterCoordinator {

    private val mutex = Mutex()

    suspend fun ensureActiveCounter(context: Context): Long = mutex.withLock {
        val settings = SettingsStore(context)
        settings.getActiveCounterId() ?: run {
            val db = AppDatabase.getInstance(context)
            val repo = CounterRepository(db, db.counterDao())
            val id = repo.createCounter("计数器 1")
            settings.setActiveCounterId(id)
            id
        }
    }
}
