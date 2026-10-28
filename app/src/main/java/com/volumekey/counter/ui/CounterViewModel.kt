package com.volumekey.counter.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.volumekey.counter.data.AppDatabase
import com.volumekey.counter.data.Checkpoint
import com.volumekey.counter.data.Counter
import com.volumekey.counter.data.CounterCoordinator
import com.volumekey.counter.data.CounterRepository
import com.volumekey.counter.data.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CounterViewModel(
    private val repo: CounterRepository,
    private val settings: SettingsStore,
    private val appContext: Context
) : ViewModel() {

    data class UiState(
        val counter: Counter? = null,
        val checkpoints: List<Checkpoint> = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = settings.activeCounterId
        .flatMapLatest { id ->
            if (id == null) flowOf(UiState())
            else combine(
                repo.observeCounter(id),
                repo.observeCheckpoints(id)
            ) { counter, checkpoints -> UiState(counter, checkpoints) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** 全部计数器列表（管理界面用） */
    val allCounters: StateFlow<List<Counter>> = repo.observeAllCounters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var cachedId: Long? = null

    init {
        viewModelScope.launch { ensureCounter() }
    }

    /** 首次使用时自动创建默认计数器并记住为激活计数器（与前台服务共用协调器） */
    private suspend fun ensureCounter(): Long =
        cachedId ?: CounterCoordinator.ensureActiveCounter(appContext).also { cachedId = it }

    // ---- 计数与检查点 ----

    fun adjust(delta: Int, source: String) {
        if (delta == 0) return
        viewModelScope.launch {
            val id = ensureCounter()
            repo.adjust(id, delta.toLong(), source)
        }
    }

    fun saveCheckpoint(name: String) {
        viewModelScope.launch {
            val id = ensureCounter()
            val current = repo.getCounter(id) ?: return@launch
            val finalName = name.ifBlank { defaultCheckpointName(uiState.value.checkpoints.size) }
            repo.saveCheckpoint(id, finalName, current.value)
        }
    }

    fun rollback(checkpoint: Checkpoint) {
        viewModelScope.launch {
            val id = ensureCounter()
            repo.rollback(id, checkpoint)
        }
    }

    fun deleteCheckpoint(checkpoint: Checkpoint) {
        viewModelScope.launch { repo.deleteCheckpoint(checkpoint) }
    }

    /** 复位当前计数器：归零 + 清空检查点，日志留痕 */
    fun resetCounter() {
        viewModelScope.launch {
            val id = ensureCounter()
            repo.resetCounter(id)
        }
    }

    fun defaultCheckpointName(existing: Int): String = "检查点 ${existing + 1}"

    // ---- 多计数器管理 ----

    fun switchCounter(id: Long) {
        viewModelScope.launch {
            settings.setActiveCounterId(id)
            cachedId = id
        }
    }

    fun createCounter(name: String) {
        viewModelScope.launch {
            val finalName = name.ifBlank { defaultCounterName(allCounters.value.size) }
            val id = repo.createCounter(finalName)
            switchCounter(id)
        }
    }

    fun renameCounter(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.renameCounter(id, name) }
    }

    /** 删除计数器（连同其事件日志与检查点）；删除后与 DataStore 对账，必要时切换/重建激活计数器 */
    fun deleteCounter(counter: Counter) {
        viewModelScope.launch {
            repo.deleteCounterCompletely(counter)
            val dataStoreId = settings.getActiveCounterId()
            val remaining = repo.observeAllCounters().first()
            val activeValid = dataStoreId != null && remaining.any { it.id == dataStoreId }
            if (counter.id == cachedId || !activeValid) {
                val newId = remaining.firstOrNull()?.id ?: repo.createCounter(defaultCounterName(0))
                settings.setActiveCounterId(newId)
                cachedId = newId
            }
        }
    }

    private fun defaultCounterName(existing: Int): String = "计数器 ${existing + 1}"

    companion object {
        /** 导出用：同步取全部数据（挂起调用方负责线程） */
        suspend fun exportData(repo: CounterRepository, settings: SettingsStore, context: Context): Triple<Counter, List<Checkpoint>, List<com.volumekey.counter.data.CountEvent>>? {
            val id = CounterCoordinator.ensureActiveCounter(context)
            val counter = repo.getCounter(id) ?: return null
            return Triple(counter, repo.getAllCheckpoints(id), repo.getAllEvents(id))
        }
    }
}

class CounterViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getInstance(appContext)
        val vm = CounterViewModel(
            repo = CounterRepository(db, db.counterDao()),
            settings = SettingsStore(appContext),
            appContext = appContext
        )
        return vm as T
    }
}
