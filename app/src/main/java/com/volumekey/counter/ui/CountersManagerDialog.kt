package com.volumekey.counter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Badge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.volumekey.counter.data.Counter

/** 计数器管理：列表 / 新建 / 重命名 / 切换 / 删除 / CSV 导出 / 保活指引入口 */
@Composable
fun CountersManagerDialog(
    counters: List<Counter>,
    activeCounterId: Long?,
    onDismiss: () -> Unit,
    onSwitch: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Counter) -> Unit,
    onExportCsv: () -> Unit,
    onShowKeepAliveGuide: () -> Unit
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Counter?>(null) }
    var deleteTarget by remember { mutableStateOf<Counter?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("计数器管理", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }

                Text(
                    "点选一个计数器切换为当前计数；各计数器的检查点与日志相互独立。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                ) {
                    items(counters, key = { it.id }) { counter ->
                        CounterRow(
                            counter = counter,
                            active = counter.id == activeCounterId,
                            onSwitch = { onSwitch(counter.id) },
                            onRename = { renameTarget = counter },
                            onDelete = { deleteTarget = counter }
                        )
                    }
                }

                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) {
                        Text("＋ 新建计数器")
                    }
                    TextButton(onClick = onExportCsv, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.padding(end = 4.dp).height(18.dp))
                        Text("导出CSV")
                    }
                }
                TextButton(onClick = onShowKeepAliveGuide, modifier = Modifier.fillMaxWidth()) {
                    Text("后台保活设置指引（重要）")
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建计数器") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("名称（如：A区货架）") }
                )
            },
            confirmButton = {
                TextButton(onClick = { onCreate(name); showCreate = false }) { Text("创建并切换") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } }
        )
    }

    renameTarget?.let { target ->
        var name by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名计数器") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { onRename(target.id, name); renameTarget = null }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除计数器") },
            text = { Text("将删除「${target.name}」及其全部检查点与事件日志，不可恢复。确定？") },
            confirmButton = {
                TextButton(onClick = { onDelete(target); deleteTarget = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun CounterRow(
    counter: Counter,
    active: Boolean,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onSwitch)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(counter.name, style = MaterialTheme.typography.titleMedium)
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Badge { Text("当前") }
                    }
                }
                Text(
                    "计数 ${counter.value}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "重命名", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
