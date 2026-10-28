package com.volumekey.counter.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.volumekey.counter.data.AppDatabase
import com.volumekey.counter.data.CounterRepository
import com.volumekey.counter.data.InventoryItem
import com.volumekey.counter.data.InventoryList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InventoryViewModel(
    private val repo: CounterRepository
) : ViewModel() {

    val lists: StateFlow<List<InventoryList>> = repo.observeInventoryLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeListId = MutableStateFlow<Long?>(null)
    val searchQuery = MutableStateFlow("")

    init {
        // 默认选中最近一份清单
        viewModelScope.launch {
            lists.collect { ls ->
                if (activeListId.value == null && ls.isNotEmpty()) {
                    activeListId.value = ls.first().id
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<InventoryItem>> = combine(activeListId, searchQuery) { id, q -> id to q.trim() }
        .flatMapLatest { (id, q) ->
            if (id == null) flowOf(emptyList())
            else repo.observeInventoryItems(id).map { list ->
                if (q.isEmpty()) list
                else list.filter { it.name.contains(q, ignoreCase = true) || it.customField.contains(q, ignoreCase = true) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class Summary(val total: Int, val touched: Int, val diff: Int)

    val summary: StateFlow<Summary> = items.map { list ->
        Summary(
            total = list.size,
            touched = list.count { it.touched },
            diff = list.count { it.touched && it.countedQty != it.expectedQty }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Summary(0, 0, 0))

    fun selectList(id: Long) {
        activeListId.value = id
    }

    fun importDone(listId: Long) {
        activeListId.value = listId
    }

    fun deleteActiveList() {
        val id = activeListId.value ?: return
        viewModelScope.launch {
            val list = lists.value.firstOrNull { it.id == id } ?: return@launch
            repo.deleteInventoryList(list)
            activeListId.value = lists.value.firstOrNull { it.id != id }?.id
        }
    }

    fun adjust(itemId: Long, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch { repo.adjustInventoryItem(itemId, delta.toLong()) }
    }

    fun setCountedAndRemark(itemId: Long, counted: Long, remark: String) {
        viewModelScope.launch { repo.setInventoryItemFields(itemId, counted, remark) }
    }

    fun setRemark(itemId: Long, remark: String) {
        viewModelScope.launch { repo.setInventoryItemRemark(itemId, remark) }
    }
}

class InventoryViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getInstance(appContext)
        val vm = InventoryViewModel(CounterRepository(db, db.counterDao()))
        return vm as T
    }
}
