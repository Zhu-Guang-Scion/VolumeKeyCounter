package com.volumekey.counter

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.volumekey.counter.data.AppDatabase
import com.volumekey.counter.data.CounterCoordinator
import com.volumekey.counter.data.CounterRepository
import com.volumekey.counter.data.SettingsStore
import com.volumekey.counter.oem.OemKeepAlive
import com.volumekey.counter.service.CountRouting
import com.volumekey.counter.service.CountingService
import com.volumekey.counter.service.PocketModeState
import com.volumekey.counter.ui.CounterScreen
import com.volumekey.counter.ui.CounterViewModel
import com.volumekey.counter.ui.CounterViewModelFactory
import com.volumekey.counter.ui.CountersManagerDialog
import com.volumekey.counter.ui.InventoryScreen
import com.volumekey.counter.ui.InventoryViewModel
import com.volumekey.counter.ui.InventoryViewModelFactory
import com.volumekey.counter.ui.theme.VolumeCounterTheme
import com.volumekey.counter.util.CsvExporter
import com.volumekey.counter.util.InventoryImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: CounterViewModel by viewModels { CounterViewModelFactory(applicationContext) }
    private val inventoryViewModel: InventoryViewModel by viewModels { InventoryViewModelFactory(applicationContext) }
    private val settings by lazy { SettingsStore(applicationContext) }

    private val keepScreenOnState = mutableStateOf(false)
    private var longPressHintShown = false
    private val showCountersManager = mutableStateOf(false)
    private val showKeepAliveGuide = mutableStateOf(false)
    private val showInventory = mutableStateOf(false)
    private var startPocketWhenResumed = false

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            startPocketServiceSafely()
            if (!granted) toast("未授予通知权限：仍可计数，但通知栏不显示当前数值")
        }

    private val importCsvLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) handleImportCsv(uri)
        }

    private fun handleImportCsv(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = InventoryImporter.import(applicationContext, uri)
                inventoryViewModel.importDone(result.listId)
                withContext(Dispatchers.Main) {
                    val sb = StringBuilder("导入成功 ${result.rows} 条")
                    if (result.skipped > 0) sb.append("，跳过 ${result.skipped} 条（名称为空）")
                    if (result.qtyInvalid > 0) sb.append("，${result.qtyInvalid} 处数量无法识别按 0 计")
                    if (!result.headerMatched) sb.append("\n未识别到标准表头，已按默认列序解析，请核对前几行")
                    toast(sb.toString())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toast("导入失败：${e.message}")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VolumeCounterTheme {
                val uiState by viewModel.uiState.collectAsState()
                val pocketRunning by PocketModeState.isRunning.collectAsState()
                val allCounters by viewModel.allCounters.collectAsState()

                if (showInventory.value) {
                    InventoryScreen(
                        vm = inventoryViewModel,
                        onClose = {
                            CountRouting.activeInventoryItemId.value = null
                            showInventory.value = false
                        },
                        onImportClicked = {
                            try {
                                importCsvLauncher.launch(
                                    arrayOf(
                                        "text/*",
                                        "application/csv",
                                        "application/vnd.ms-excel",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/octet-stream"
                                    )
                                )
                            } catch (_: Exception) {
                                toast("无法打开文件选择器")
                            }
                        },
                        onTemplateClicked = {
                            val saved = InventoryImporter.writeTemplate(this@MainActivity)
                            if (saved != null) {
                                toast("模板已生成：$saved")
                            } else {
                                val ok = CsvExporter.shareAsFile(this@MainActivity, "盘点清单模板.csv", InventoryImporter.TEMPLATE_CSV)
                                toast(if (ok) "已生成模板并通过分享发出" else "模板生成失败")
                            }
                        },
                        onExportClicked = ::exportInventory
                    )
                } else {
                    CounterScreen(
                        uiState = uiState,
                        keepScreenOn = keepScreenOnState.value,
                        pocketRunning = pocketRunning,
                        onToggleKeepScreenOn = ::toggleKeepScreenOn,
                        onTogglePocketMode = ::togglePocketMode,
                        onShareSummary = ::shareSummary,
                        onOpenCounters = { showCountersManager.value = true },
                        onOpenInventory = { showInventory.value = true },
                        onAdjust = ::adjustCount,
                        onSaveCheckpoint = viewModel::saveCheckpoint,
                        onRollback = viewModel::rollback,
                        onDeleteCheckpoint = viewModel::deleteCheckpoint,
                        onReset = viewModel::resetCounter,
                        defaultCheckpointName = { viewModel.defaultCheckpointName(uiState.checkpoints.size) }
                    )
                }

                if (showCountersManager.value) {
                    CountersManagerDialog(
                        counters = allCounters,
                        activeCounterId = uiState.counter?.id,
                        onDismiss = { showCountersManager.value = false },
                        onSwitch = viewModel::switchCounter,
                        onCreate = viewModel::createCounter,
                        onRename = viewModel::renameCounter,
                        onDelete = viewModel::deleteCounter,
                        onExportCsv = ::exportCsv,
                        onShowKeepAliveGuide = { showKeepAliveGuide.value = true }
                    )
                }

                if (showKeepAliveGuide.value) {
                    KeepAliveGuideDialog(
                        onDismiss = { showKeepAliveGuide.value = false },
                        onOpenSettings = {
                            showKeepAliveGuide.value = false
                            OemKeepAlive.openAppDetails(this)
                        }
                    )
                }
            }
        }
    }

    /**
     * 音量键计数（前台拦截路径）：
     * - 清单盘点条目激活 → 计入该条目实盘数
     * - 盘点板面打开但未选中条目 → 消费但不动计数（防误改背后的普通计数器）
     * - 其他 → 计入普通计数器
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val up = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    if (event.repeatCount == 0) {
                        longPressHintShown = false
                        routeVolumeKey(if (up) +1 else -1, up)
                    } else if (!longPressHintShown) {
                        longPressHintShown = true
                        toast("长按不重复计数，仅计 1 次")
                    }
                    return true // 消费（含连发），系统音量不变化
                } else if (event.action == KeyEvent.ACTION_UP) {
                    longPressHintShown = false
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun routeVolumeKey(delta: Int, up: Boolean) {
        val invId = CountRouting.activeInventoryItemId.value
        when {
            invId != null -> {
                vibrateTick()
                inventoryViewModel.adjust(invId, delta, CounterRepository.INVENTORY_SOURCE_VOLUME)
            }
            showInventory.value -> {
                vibrateTick() // 盘点板面无激活条目：仅震动反馈，不计数
            }
            else -> adjustCount(
                delta,
                if (up) CounterRepository.SOURCE_VOLUME_UP else CounterRepository.SOURCE_VOLUME_DOWN
            )
        }
    }

    private fun adjustCount(delta: Int, source: String) {
        vibrateTick()
        viewModel.adjust(delta, source)
    }

    private fun toggleKeepScreenOn() {
        keepScreenOnState.value = !keepScreenOnState.value
        if (keepScreenOnState.value) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        toast(if (keepScreenOnState.value) "屏幕常亮：已开启" else "屏幕常亮：已关闭")
    }

    private fun togglePocketMode() {
        if (PocketModeState.isRunning.value) {
            CountingService.stop(this)
            toast("口袋模式已结束")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startPocketServiceSafely()
        }
    }

    /**
     * 安全启动口袋模式：Android 12+ 禁止从后台启动前台服务，
     * 权限回调等场景 Activity 可能已非前台，此时挂起待恢复后补启动。
     * @return true=已立即启动
     */
    private fun startPocketServiceSafely(): Boolean {
        val resumed = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        if (resumed) {
            return try {
                CountingService.start(this)
                toast("口袋模式已开启：熄屏/锁屏后音量键继续计数")
                maybeShowKeepAliveHint()
                true
            } catch (e: Exception) {
                startPocketWhenResumed = true
                toast("即将开启口袋模式…")
                false
            }
        }
        startPocketWhenResumed = true
        return false
    }

    override fun onPostResume() {
        super.onPostResume()
        if (startPocketWhenResumed && !PocketModeState.isRunning.value) {
            startPocketWhenResumed = false
            try {
                CountingService.start(this)
                toast("口袋模式已开启：熄屏/锁屏后音量键继续计数")
                maybeShowKeepAliveHint()
            } catch (_: Exception) {
                toast("口袋模式启动失败，请回到应用内重试")
            }
        }
    }

    /** 首次开启口袋模式时弹一次厂商保活指引 */
    private fun maybeShowKeepAliveHint() {
        lifecycleScope.launch {
            if (!settings.keepAliveHintShown.first()) {
                settings.setKeepAliveHintShown()
                showKeepAliveGuide.value = true
            }
        }
    }

    private fun shareSummary() {
        val state = viewModel.uiState.value
        val counter = state.counter
        val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("【音量键计数器】").append(counter?.name ?: "计数器").append('\n')
        sb.append("当前计数：").append(counter?.value ?: 0L).append('\n')
        if (state.checkpoints.isNotEmpty()) {
            sb.append("检查点（${state.checkpoints.size} 个）：")
            state.checkpoints.forEachIndexed { i, cp ->
                sb.append("\n${i + 1}. ${cp.name} —— ${cp.value} · ${fmt.format(Date(cp.createdAt))}")
            }
            sb.append('\n')
        }
        sb.append("导出时间：").append(fmt.format(Date()))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(intent, "分享计数汇总"))
    }

    /** 导出盘点结果 CSV（清单概要+条目表+变更明细）到 Download */
    private fun exportInventory() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val listId = inventoryViewModel.activeListId.value
                if (listId == null) {
                    withContext(Dispatchers.Main) { toast("当前没有清单可导出") }
                    return@launch
                }
                val db = AppDatabase.getInstance(applicationContext)
                val repo = CounterRepository(db, db.counterDao())
                val list = repo.observeInventoryLists().first().firstOrNull { it.id == listId }
                    ?: throw IllegalStateException("清单不存在")
                val items = repo.observeInventoryItems(listId).first()
                val csvText = CsvExporter.buildInventoryCsv(list, items)
                val fileName = CsvExporter.inventoryFileName(list.name)
                val saved = CsvExporter.exportThenShare(applicationContext, fileName, csvText)
                withContext(Dispatchers.Main) {
                    toast(
                        if (saved != null) "已导出（留档：下载/$fileName），请选择转发目标"
                        else "已生成文件，请选择转发目标"
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast("导出失败：${e.message}") }
            }
        }
    }

    private fun exportCsv() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val repo = CounterRepository(db, db.counterDao())
                val id = CounterCoordinator.ensureActiveCounter(applicationContext)
                val counter = repo.getCounter(id) ?: return@launch
                val csv = CsvExporter.buildCsv(counter, repo.getAllCheckpoints(id))
                val fileName = CsvExporter.fileName(counter.name)
                val saved = CsvExporter.exportThenShare(applicationContext, fileName, csv)
                withContext(Dispatchers.Main) {
                    toast(
                        if (saved != null) "已导出（留档：下载/$fileName），请选择转发目标"
                        else "已生成文件，请选择转发目标"
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast("导出失败：${e.message}") }
            }
        }
    }

    private fun vibrateTick() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else {
            VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun KeepAliveGuideDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("让口袋模式更稳定（${OemKeepAlive.brandName()}）") },
        text = {
            Column {
                OemKeepAlive.steps().forEach { step ->
                    Text("• $step", fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "完成设置后，长时间熄屏计数更可靠；不做设置通常也能用，但个别系统可能中途休眠。",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text("去应用设置") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}
