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
