package com.volumekey.counter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volumekey.counter.data.Checkpoint
import com.volumekey.counter.data.CounterRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterScreen(
    uiState: CounterViewModel.UiState,
    keepScreenOn: Boolean,
    pocketRunning: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    onTogglePocketMode: () -> Unit,
    onShareSummary: () -> Unit,
    onOpenCounters: () -> Unit,
    onOpenInventory: () -> Unit,
    onAdjust: (Int, String) -> Unit,
    onSaveCheckpoint: (String) -> Unit,
    onRollback: (Checkpoint) -> Unit,
    onDeleteCheckpoint: (Checkpoint) -> Unit,
    onReset: () -> Unit,
    defaultCheckpointName: () -> String
) {
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var rollbackTarget by remember { mutableStateOf<Checkpoint?>(null) }
    var deleteTarget by remember { mutableStateOf<Checkpoint?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.counter?.name ?: "音量键计数器") },
                actions = {
                    IconButton(onClick = onOpenInventory) {
                        Icon(
                            Icons.Filled.Checklist,
                            contentDescription = "清单盘点"
                        )
                    }
                    IconButton(onClick = onOpenCounters) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "计数器管理"
                        )
                    }
                    IconButton(onClick = onToggleKeepScreenOn) {
                        Icon(
                            imageVector = Icons.Filled.LightMode,
                            contentDescription = if (keepScreenOn) "关闭屏幕常亮" else "开启屏幕常亮"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "音量上键 +1　·　音量下键 −1",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.counter?.value?.toString() ?: "—",
                    fontSize = 104.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Row(Modifier.fillMaxWidth()) {
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onAdjust(-1, CounterRepository.SOURCE_BUTTON)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Text("− 1", fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onAdjust(+1, CounterRepository.SOURCE_BUTTON)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Text("+ 1", fontSize = 22.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                if (pocketRunning) {
                    Button(
                        onClick = onTogglePocketMode,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("结束口袋模式")
                    }
                } else {
                    OutlinedButton(
                        onClick = onTogglePocketMode,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("口袋模式")
                    }
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = onShareSummary,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("分享汇总")
                }
            }

            if (pocketRunning) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "口袋模式运行中：熄屏 / 锁屏 / 切到后台，音量键仍持续计数",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "检查点",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { showResetDialog = true },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("复位")
                }
                Button(onClick = { showSaveDialog = true }) {
                    Text("存为检查点")
                }
            }

            val checkpoints = uiState.checkpoints
            if (checkpoints.isEmpty()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "暂无检查点。计数过程中可随时保存当前计数为命名检查点，用于核对与回退。",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(checkpoints, key = { it.id }) { cp ->
                        CheckpointRow(
                            checkpoint = cp,
                            onRollback = { rollbackTarget = cp },
                            onDelete = { deleteTarget = cp }
                        )
                    }
                }
            }

            // 页脚水印
            Text(
                "code by 逐光",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 10.dp)
            )
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf(defaultCheckpointName()) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存检查点") },
            text = {
                Column {
                    Text("当前计数：${uiState.counter?.value ?: 0L}")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("检查点名称") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveCheckpoint(name)
                        showSaveDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("复位计数器") },
            text = {
                Text(
                    "将执行以下操作：\n" +
                        "① 计数归零（当前 ${uiState.counter?.value ?: 0L} → 0）\n" +
                        "② 清空本计数器的 ${uiState.checkpoints.size} 个检查点\n\n" +
                        "事件日志将完整保留（含本次复位记录）。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        showResetDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("确认复位") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            }
        )
    }

    rollbackTarget?.let { cp ->
        AlertDialog(
            onDismissRequest = { rollbackTarget = null },
            title = { Text("回退到检查点") },
            text = {
                Text(
                    "将计数从当前 ${uiState.counter?.value ?: 0L} 回退到「${cp.name}」的 ${cp.value}？\n" +
                        "此操作会记录一条回退日志，不会删除历史。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRollback(cp)
                        rollbackTarget = null
                    }
                ) { Text("回退") }
            },
            dismissButton = {
                TextButton(onClick = { rollbackTarget = null }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { cp ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除检查点") },
            text = { Text("确定删除检查点「${cp.name}」（计数值 ${cp.value}）？计数本身不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCheckpoint(cp)
                        deleteTarget = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CheckpointRow(
    checkpoint: Checkpoint,
    onRollback: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(checkpoint.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "计数值 ${checkpoint.value} · ${timeFmt.format(Date(checkpoint.createdAt))}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRollback) { Text("回退") }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除检查点",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
