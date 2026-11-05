package com.volumekey.counter.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 计数器 */
@Entity(tableName = "counters")
data class Counter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val value: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 检查记忆回退点：某个时刻的计数快照，可自定义命名 */
@Entity(
    tableName = "checkpoints",
    foreignKeys = [ForeignKey(
        entity = Counter::class,
        parentColumns = ["id"],
        childColumns = ["counterId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("counterId")]
)
data class Checkpoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val counterId: Long,
    val name: String,
    val value: Long,
    val createdAt: Long = System.currentTimeMillis()
)

/** 计数事件日志：每次 +1/-1/回退都留痕，valueAfter 记录操作后的计数值 */
@Entity(tableName = "count_events", indices = [Index("counterId")])
data class CountEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val counterId: Long,
    val delta: Long,
    val valueAfter: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String
)

/** 清单盘点：清单 */
@Entity(tableName = "inventory_lists")
data class InventoryList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val itemCount: Int = 0
)

/** 清单盘点：条目（五维：名称/自定义列/账面数量/实盘数量/备注） */
@Entity(
    tableName = "inventory_items",
    foreignKeys = [ForeignKey(
        entity = InventoryList::class,
        parentColumns = ["id"],
        childColumns = ["listId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("listId")]
)
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val listId: Long,
    val sortOrder: Int,
    val name: String,
    val customField: String,
    val expectedQty: Long,
    val countedQty: Long,
    val touched: Boolean = false,
    val remark: String = ""
)

/** 清单盘点：实盘数变更流水（音量键/屏幕按钮/手动修改/标记未盘全部留痕） */
@Entity(
    tableName = "inventory_events",
    foreignKeys = [ForeignKey(
        entity = InventoryItem::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("itemId")]
)
data class InventoryEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val itemId: Long,
    val delta: Long,
    val valueAfter: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String // volume / screen / manual
)

/** 清单盘点：水印相机拍摄记录（照片存系统相册 Pictures/盘点取证，此处登记元数据与序号） */
@Entity(
    tableName = "inventory_photos",
    foreignKeys = [ForeignKey(
        entity = InventoryItem::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("itemId")]
)
data class InventoryPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val itemId: Long,
    val seq: Int,
    val fileName: String,
    val takenAt: Long = System.currentTimeMillis(),
    val lat: Double? = null,
    val lng: Double? = null,
    val address: String? = null
)
