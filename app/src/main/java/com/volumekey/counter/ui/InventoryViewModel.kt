package com.volumekey.counter.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.volumekey.counter.data.AppDatabase
import com.volumekey.counter.data.CounterRepository
import com.volumekey.counter.data.InventoryItem
import com.volumekey.counter.data.InventoryList
import com.volumekey.counter.data.InventoryPhoto
import com.volumekey.counter.util.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 清单筛选维度 */
enum class InventoryFilter { ALL, UNTOUCHED, TOUCHED, DIFF }

class InventoryViewModel(
    private val repo: CounterRepository,
    private val appContext: Context
) : ViewModel() {

    val lists: StateFlow<List<InventoryList>> = repo.observeInventoryLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeListId = MutableStateFlow<Long?>(null)
    val searchQuery = MutableStateFlow("")
    val filter = MutableStateFlow(InventoryFilter.ALL)

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

    private fun applyFilter(list: List<InventoryItem>, q: String, f: InventoryFilter): List<InventoryItem> {
        var r = list
        if (q.isNotEmpty()) r = r.filter {
            it.name.contains(q, ignoreCase = true) || it.customField.contains(q, ignoreCase = true)
        }
        r = when (f) {
            InventoryFilter.ALL -> r
            InventoryFilter.UNTOUCHED -> r.filter { !it.touched }
            InventoryFilter.TOUCHED -> r.filter { it.touched }
            InventoryFilter.DIFF -> r.filter { it.touched && it.countedQty != it.expectedQty }
        }
        return r
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<InventoryItem>> = combine(
        activeListId, searchQuery, filter
    ) { id, q, f -> Triple(id, q.trim(), f) }
        .flatMapLatest { (id, q, f) ->
            if (id == null) flowOf(emptyList())
            else repo.observeInventoryItems(id).map { applyFilter(it, q, f) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class Summary(val total: Int, val touched: Int, val diff: Int) {
        val untouched: Int get() = total - touched
    }

    /** 全量统计（不受检索/筛选影响，用于筛选标签角标） */
    @OptIn(ExperimentalCoroutinesApi::class)
    val summary: StateFlow<Summary> = activeListId
        .flatMapLatest { id ->
            if (id == null) flowOf(Summary(0, 0, 0))
            else repo.observeInventoryItems(id).map { list ->
                Summary(
                    total = list.size,
                    touched = list.count { it.touched },
                    diff = list.count { it.touched && it.countedQty != it.expectedQty }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Summary(0, 0, 0))

    /** 各条目已拍水印照片数（itemId -> count） */
    @OptIn(ExperimentalCoroutinesApi::class)
    val photoCounts: StateFlow<Map<Long, Int>> = activeListId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyMap())
            else repo.observeInventoryPhotoCounts(id).map { rows ->
                rows.associate { it.itemId to it.cnt }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun selectList(id: Long) {
        activeListId.value = id
    }

    fun importDone(listId: Long) {
        activeListId.value = listId
        filter.value = InventoryFilter.ALL
        searchQuery.value = ""
    }

    fun setFilter(f: InventoryFilter) {
        filter.value = f
    }

    fun deleteActiveList() {
        val id = activeListId.value ?: return
        viewModelScope.launch {
            val list = repo.observeInventoryLists().first().firstOrNull { it.id == id } ?: return@launch
            repo.deleteInventoryList(list)
            // 以数据库最新状态选择下一个清单（避免 StateFlow 缓存滞后）
            activeListId.value = repo.observeInventoryLists().first().firstOrNull()?.id
        }
    }

    fun adjust(itemId: Long, delta: Int, source: String) {
        if (delta == 0) return
        viewModelScope.launch { repo.adjustInventoryItem(itemId, delta.toLong(), source) }
    }

    fun setCountedAndRemark(itemId: Long, counted: Long, remark: String) {
        viewModelScope.launch { repo.setInventoryItemFields(itemId, counted, remark) }
    }

    fun markUntouched(itemId: Long) {
        viewModelScope.launch { repo.markInventoryItemUntouched(itemId) }
    }

    // ---- 照片画廊 ----

    /** 画廊当前条目（null=关闭） */
    val galleryItemId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val galleryPhotos: StateFlow<List<InventoryPhoto>> = galleryItemId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repo.observeInventoryPhotos(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openGallery(itemId: Long) {
        galleryItemId.value = itemId
        // 对账：照片若已在系统相册被删，同步清掉登记记录，保证计数一致
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val records = repo.getInventoryPhotos(itemId)
                records.forEach { r ->
                    if (PhotoStore.findUri(appContext, r.fileName) == null) {
                        repo.deleteInventoryPhotoRecord(r.id)
                    }
                }
            }
        }
    }

    fun closeGallery() {
        galleryItemId.value = null
    }

    /** 删除一张：先删相册文件，再删登记记录（缩略图缓存失效） */
    fun deletePhoto(photo: InventoryPhoto) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                PhotoStore.findUri(appContext, photo.fileName)?.let { uri ->
                    PhotoStore.deleteByUri(appContext, uri)
                }
                PhotoStore.invalidateThumb(photo.fileName)
                repo.deleteInventoryPhotoRecord(photo.id)
            }
        }
    }
}

class InventoryViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getInstance(appContext)
        val vm = InventoryViewModel(CounterRepository(db, db.counterDao()), appContext)
        return vm as T
    }
}
