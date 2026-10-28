package com.volumekey.counter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volumekey.counter.data.InventoryItem
import com.volumekey.counter.service.CountRouting

/** 清单盘点主板面：清单选择 / 导入 / 检索 / 汇总 / 条目列表 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    vm: InventoryViewModel,
    onClose: () -> Unit,
    onImportClicked: () -> Unit,
    onTemplateClicked: () -> Unit,
    toastMsg: String?
) {
    val lists by vm.lists.collectAsState()
    val items by vm.items.collectAsState()
    val summary by vm.summary.collectAsState()
    val activeListId by vm.activeListId.collectAsState()
    var search by rememberSaveable { mutableStateOf(vm.searchQuery.value) }
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var countingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var deleteListConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(search) { vm.searchQuery.value = search }
    LaunchedEffect(toastMsg) { /* 提示由 Activity Toast 呈现 */ }

    BackHandler(enabled = true) { onClose() }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("清单盘点", fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回计数器")
                    }
                },
                actions = {
                    IconButton(onClick = onImportClicked) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "导入CSV清单")
                    }
                    IconButton(onClick = { deleteListConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除当前清单")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            // 当前清单选择
            if (lists.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "当前清单：", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    lists.forEach { l ->
                        val active = l.id == activeListId
                        TextButton(onClick = { vm.selectList(l.id) }) {
                            Text(
                                l.name,
                                fontSize = 17.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 检索框（大字）
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 19.sp),
                placeholder = { Text("按名称检索…", fontSize = 19.sp) },
                modifier = Modifier.fillMaxWidth()
            )

            // 汇总条
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "共 ${summary.total} 项　·　已盘 ${summary.touched}　·　差异 ${summary.diff}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(6.dp))

            if (lists.isEmpty()) {
                // 空态引导（大字）
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("尚未导入清单", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "准备一个 CSV 表格，包含五列：\n盘点对象名称 / 自定义列 /\n盘点对象数量 / 实际盘点数量 / 备注\n\n（Excel 另存为 CSV 即可，支持 UTF-8 与 GBK）",
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onImportClicked, modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("导入 CSV 清单", fontSize = 19.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onTemplateClicked, modifier = Modifier.height(48.dp)) {
                        Text("生成模板到 Download", fontSize = 17.sp)
                    }
                }
            } else if (items.isEmpty()) {
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (search.isEmpty()) "该清单为空" else "没有匹配「${search}」的条目",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(items, key = { it.id }) { item ->
                        InventoryRow(
                            item = item,
                            onCount = { countingItem = item },
                            onEdit = { editingItem = item }
                        )
                        HorizontalDivider()
                    }
                }
            }

            Text(
                "点击条目开始计数 · 点实盘数或备注可修改",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
    }

    // 全屏盘点计数（覆盖层而非 Dialog 窗口——保证音量键仍走 Activity 的 dispatchKeyEvent）
    countingItem?.let { item ->
        val live = items.firstOrNull { it.id == item.id } ?: item
        ItemCountingOverlay(
            item = live,
            onAdjust = { vm.adjust(live.id, it) },
            onEdit = { editingItem = live },
            onDismiss = { countingItem = null }
        )
    }
    } // Box end

    // 手动编辑（实盘数量 + 备注）
    editingItem?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { counted, remark ->
                vm.setCountedAndRemark(item.id, counted, remark)
                editingItem = null
            }
        )
    }

    if (deleteListConfirm) {
        val name = lists.firstOrNull { it.id == activeListId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { deleteListConfirm = false },
            title = { Text("删除清单") },
            text = { Text("确定删除清单「$name」及其全部条目？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteActiveList(); deleteListConfirm = false }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteListConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun InventoryRow(
    item: InventoryItem,
    onCount: () -> Unit,
    onEdit: () -> Unit
) {
    val diff = item.countedQty - item.expectedQty
    val diffColor = when {
        !item.touched -> MaterialTheme.colorScheme.onSurfaceVariant
        diff == 0L -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onCount)
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.touched) {
                        Spacer(Modifier.width(6.dp))
                        Badge { Text("已盘", fontSize = 11.sp) }
                    }
                }
                if (item.customField.isNotBlank()) {
                    Text(
                        item.customField,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (item.touched) "账面 ${item.expectedQty}　差异 ${diff}" else "账面 ${item.expectedQty}　未盘",
                    fontSize = 16.sp,
                    color = diffColor
                )
                if (item.remark.isNotBlank()) {
                    Text(
                        "备注：${item.remark}",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    item.countedQty.toString(),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = diffColor,
                    modifier = Modifier.clickable(onClick = onEdit)
                )
                Text("实盘（点改）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 全屏盘点计数覆盖层：音量键直接计入当前条目（Activity 窗口内，非 Dialog） */
@Composable
private fun ItemCountingOverlay(
    item: InventoryItem,
    onAdjust: (Int) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    // 激活音量键路由到本条目
    DisposableEffect(item.id) {
        CountRouting.activeInventoryItemId.value = item.id
        onDispose { CountRouting.activeInventoryItemId.value = null }
    }
    BackHandler { onDismiss() }

    val diff = item.countedQty - item.expectedQty
    val diffColor = if (diff == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Surface(
        Modifier
            .fillMaxSize()
            .padding(18.dp),
        shape = MaterialTheme.shapes.large,
        shadowElevation = 8.dp,
        tonalElevation = 8.dp
    ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(6.dp))
                Text(
                    item.name,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (item.customField.isNotBlank()) {
                    Text(item.customField, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "账面数量：${item.expectedQty}",
                    fontSize = 21.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    item.countedQty.toString(),
                    fontSize = 100.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    if (diff == 0L) "与账面一致" else "差异 ${diff}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = diffColor
                )
                if (item.remark.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "备注：${item.remark}",
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onAdjust(-1) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) { Text("− 1", fontSize = 22.sp) }
                    Spacer(Modifier.width(14.dp))
                    Button(
                        onClick = { onAdjust(+1) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) { Text("+ 1", fontSize = 22.sp) }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) { Text("改实盘/备注", fontSize = 17.sp) }
                    Spacer(Modifier.width(14.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) { Text("完成", fontSize = 19.sp) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "音量上键 +1　音量下键 −1",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
}

@Composable
private fun EditItemDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    onSave: (Long, String) -> Unit
) {
    var counted by remember { mutableStateOf(item.countedQty.toString()) }
    var remark by remember { mutableStateOf(item.remark) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改：${item.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = counted,
                    onValueChange = { counted = it.filter { c -> c.isDigit() || c == '-' } },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp),
                    label = { Text("实际盘点数量", fontSize = 16.sp) }
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 19.sp),
                    label = { Text("备注", fontSize = 16.sp) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(counted.toLongOrNull() ?: item.countedQty, remark) }) {
                Text("保存", fontSize = 17.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 17.sp) }
        }
    )
}
