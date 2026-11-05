package com.volumekey.counter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.asImageBitmap
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
    onExportClicked: () -> Unit,
    onPhoto: (com.volumekey.counter.data.InventoryItem) -> Unit,
    onOpenPhoto: (com.volumekey.counter.data.InventoryPhoto) -> Unit
) {
    val lists by vm.lists.collectAsState()
    val items by vm.items.collectAsState()
    val summary by vm.summary.collectAsState()
    val activeListId by vm.activeListId.collectAsState()
    val currentFilter by vm.filter.collectAsState()
    val photoCounts by vm.photoCounts.collectAsState()
    val search by vm.searchQuery.collectAsState()
    val galleryItemId by vm.galleryItemId.collectAsState()
    val galleryPhotos by vm.galleryPhotos.collectAsState()
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var countingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var deleteListConfirm by rememberSaveable { mutableStateOf(false) }
    var showFormatHelp by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        CountRouting.boardOpen.value = true
        onDispose { CountRouting.boardOpen.value = false }
    }

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
                    IconButton(onClick = onExportClicked) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = "导出盘点结果")
                    }
                    IconButton(onClick = { showFormatHelp = true }) {
                        Icon(Icons.Filled.HelpOutline, contentDescription = "导入格式说明")
                    }
                    IconButton(onClick = onImportClicked) {
                        Icon(Icons.Filled.TableChart, contentDescription = "导入CSV清单")
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

            // 检索框（大字，带一键清空）
            OutlinedTextField(
                value = search,
                onValueChange = { vm.searchQuery.value = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 19.sp),
                placeholder = { Text("按名称检索…", fontSize = 19.sp) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { vm.searchQuery.value = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空检索")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 筛选标签页（带数量，替代纯文字汇总条）
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    modifier = Modifier.height(40.dp),
                    selected = currentFilter == InventoryFilter.ALL,
                    onClick = { vm.setFilter(InventoryFilter.ALL) },
                    label = { Text("全部 ${summary.total}", fontSize = 16.sp) }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    modifier = Modifier.height(40.dp),
                    selected = currentFilter == InventoryFilter.UNTOUCHED,
                    onClick = { vm.setFilter(InventoryFilter.UNTOUCHED) },
                    label = { Text("未盘 ${summary.untouched}", fontSize = 16.sp) }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    modifier = Modifier.height(40.dp),
                    selected = currentFilter == InventoryFilter.TOUCHED,
                    onClick = { vm.setFilter(InventoryFilter.TOUCHED) },
                    label = { Text("已盘 ${summary.touched}", fontSize = 16.sp) }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    modifier = Modifier.height(40.dp),
                    selected = currentFilter == InventoryFilter.DIFF,
                    onClick = { vm.setFilter(InventoryFilter.DIFF) },
                    label = {
                        Text(
                            "差异 ${summary.diff}",
                            fontSize = 16.sp,
                            color = if (summary.diff > 0) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified
                        )
                    }
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
                        Text("生成模板表格（存到 下载/Download）", fontSize = 17.sp)
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
                            photoCount = photoCounts[item.id] ?: 0,
                            onCount = { countingItem = item },
                            onEdit = { editingItem = item },
                            onPhoto = { onPhoto(item) },
                            onOpenGallery = { vm.openGallery(item.id) }
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
        val idx = items.indexOfFirst { it.id == live.id }
        val nextUntouched = items.drop(idx + 1).firstOrNull { !it.touched }
            ?: items.take(idx).firstOrNull { !it.touched }
        ItemCountingOverlay(
            item = live,
            photoCount = photoCounts[live.id] ?: 0,
            onAdjust = { vm.adjust(live.id, it, com.volumekey.counter.data.CounterRepository.INVENTORY_SOURCE_SCREEN) },
            onEdit = { editingItem = live },
            onDismiss = { countingItem = null },
            onNext = { nextUntouched?.let { countingItem = it } },
            hasNext = nextUntouched != null,
            onPhoto = { onPhoto(live) }
        )
    }

    // 照片画廊覆盖层
    if (galleryItemId != null) {
        PhotoGalleryOverlay(
            photos = galleryPhotos,
            onOpen = onOpenPhoto,
            onDelete = { vm.deletePhoto(it) },
            onDismiss = { vm.closeGallery() }
        )
    }
    } // Box end

    // 手动编辑（实盘数量 + 备注 + 标记未盘）
    editingItem?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { counted, remark ->
                vm.setCountedAndRemark(item.id, counted, remark)
                editingItem = null
            },
            onMarkUntouched = {
                vm.markUntouched(item.id)
                editingItem = null
            }
        )
    }

    if (showFormatHelp) {
        AlertDialog(
            onDismissRequest = { showFormatHelp = false },
            title = { Text("清单 CSV 格式说明") },
            text = {
                Text(
                    "用 Excel 做一张表，第一行为表头，包含五列：\n\n" +
                        "① 盘点对象名称（必填）\n" +
                        "② 自定义列（规格/型号等，可留空）\n" +
                        "③ 盘点对象数量（账面数）\n" +
                        "④ 实际盘点数量（可先填 0 或留空）\n" +
                        "⑤ 备注\n\n" +
                        "列顺序可以打乱（按表头名识别）；\n" +
                        "「另存为 CSV」后导入，UTF-8 / GBK 编码均可。\n\n" +
                        "示例：\n" +
                        "盘点对象名称,自定义列,盘点对象数量,实际盘点数量,备注\n" +
                        "A区货架,规格X1,100,0,\n" +
                        "B区托盘,规格X2,24,0,待核对",
                    lineHeight = 24.sp
                )
            },
            confirmButton = { TextButton(onClick = { showFormatHelp = false }) { Text("知道了") } }
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
    photoCount: Int,
    onCount: () -> Unit,
    onEdit: () -> Unit,
    onPhoto: () -> Unit,
    onOpenGallery: () -> Unit
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
                        Badge { Text("已盘", fontSize = 13.sp) }
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
                if (photoCount > 0) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable(onClick = onOpenGallery)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                Icons.Filled.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.height(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "已拍 $photoCount 张 · 查看",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                if (item.remark.isNotBlank()) {
                    Text(
                        "备注：${item.remark}",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onEdit)
            ) {
                Text(
                    item.countedQty.toString(),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = diffColor
                )
                Text("实盘（点改）", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPhoto) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = "拍照取证",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 全屏盘点计数覆盖层：音量键直接计入当前条目（Activity 窗口内，非 Dialog） */
@Composable
private fun ItemCountingOverlay(
    item: InventoryItem,
    photoCount: Int,
    onAdjust: (Int) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    hasNext: Boolean,
    onPhoto: () -> Unit
) {
    // 激活音量键路由到本条目
    DisposableEffect(item.id) {
        CountRouting.activeInventoryItemId.value = item.id
        onDispose { CountRouting.activeInventoryItemId.value = null }
    }
    BackHandler { onDismiss() }

    val diff = item.countedQty - item.expectedQty
    val diffColor = if (diff == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

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
                    fontWeight = FontWeight.Black,
                    color = if (item.countedQty < 0) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified
                )
                Text(
                    when {
                        item.countedQty < 0 -> "已减过头（${item.countedQty}），请点改实盘修正"
                        diff == 0L -> "与账面一致"
                        else -> "差异 ${diff}"
                    },
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
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onAdjust(-1)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) { Text("− 1", fontSize = 22.sp) }
                    Spacer(Modifier.width(14.dp))
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onAdjust(+1)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) { Text("+ 1", fontSize = 22.sp) }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onPhoto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (photoCount > 0) "拍照取证（已拍 $photoCount 张）" else "拍照取证（时间/位置水印）",
                        fontSize = 17.sp
                    )
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
                    OutlinedButton(
                        onClick = onNext,
                        enabled = hasNext,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) { Text(if (hasNext) "下一未盘 →" else "全部已盘", fontSize = 17.sp) }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) { Text("完成", fontSize = 19.sp) }
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
    onSave: (Long, String) -> Unit,
    onMarkUntouched: () -> Unit
) {
    var counted by remember { mutableStateOf(item.countedQty.toString()) }
    var remark by remember { mutableStateOf(item.remark) }
    var confirmUntouched by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
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
                if (item.touched) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { confirmUntouched = true }, modifier = Modifier.height(44.dp)) {
                        Text("撤销已盘标记（实盘清零）", fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onSave(counted.toLongOrNull() ?: item.countedQty, remark)
            }) {
                Text("保存", fontSize = 17.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 17.sp) }
        }
    )
    if (confirmUntouched) {
        AlertDialog(
            onDismissRequest = { confirmUntouched = false },
            title = { Text("撤销已盘标记") },
            text = { Text("「${item.name}」的实盘数将从 ${item.countedQty} 清零并回到未盘状态（变更会记入流水）。确定？") },
            confirmButton = {
                TextButton(onClick = { onMarkUntouched() }) {
                    Text("确定清零", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUntouched = false }) { Text("取消") }
            }
        )
    }
}

/** 照片画廊覆盖层：3 列缩略图网格，点开调系统查看器，可删除单张 */
@Composable
private fun PhotoGalleryOverlay(
    photos: List<com.volumekey.counter.data.InventoryPhoto>,
    onOpen: (com.volumekey.counter.data.InventoryPhoto) -> Unit,
    onDelete: (com.volumekey.counter.data.InventoryPhoto) -> Unit,
    onDismiss: () -> Unit
) {
    DisposableEffect(Unit) {
        CountRouting.galleryOpen.value = true
        onDispose { CountRouting.galleryOpen.value = false }
    }
    BackHandler { onDismiss() }
    var deleteTarget by remember { mutableStateOf<com.volumekey.counter.data.InventoryPhoto?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Surface(
        Modifier
            .fillMaxSize()
            .padding(14.dp),
        shape = MaterialTheme.shapes.large,
        shadowElevation = 8.dp,
        tonalElevation = 6.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "取证照片（${photos.size} 张）",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭画廊")
                }
            }
            Text(
                "点照片查看大图 · 点右上角垃圾桶删除（删除后计数自动更新）",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (photos.isEmpty()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("暂无照片", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    modifier = Modifier.weight(1f)
                ) {
                    items(photos.size) { i ->
                        val p = photos[i]
                        Card(
                            Modifier
                                .padding(4.dp)
                                .clickable(onClick = { onOpen(p) })
                        ) {
                            Column {
                                ThumbImage(fileName = p.fileName, uriString = p.uri, ctx = ctx)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
                                ) {
                                    Text(
                                        "#${p.seq}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { deleteTarget = p },
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "删除照片",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.height(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除照片") },
            text = { Text("确定删除「${p.fileName}」？相册文件与登记记录将一并删除。") },
            confirmButton = {
                TextButton(onClick = { onDelete(p); deleteTarget = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

/** 缩略图：IO 线程解码 + LruCache，点击回调由外层处理 */
@Composable
private fun ThumbImage(fileName: String, uriString: String, ctx: android.content.Context) {
    var bmp by remember(fileName) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    LaunchedEffect(fileName) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            bmp = com.volumekey.counter.util.PhotoStore.loadThumbnail(ctx, uriString, fileName)
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(108.dp),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            androidx.compose.foundation.Image(
                bitmap = b.asImageBitmap(),
                contentDescription = fileName,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
