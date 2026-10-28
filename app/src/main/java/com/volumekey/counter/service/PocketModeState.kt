package com.volumekey.counter.service

import kotlinx.coroutines.flow.MutableStateFlow

/** 口袋模式（熄屏计数前台服务）运行状态，同进程内 UI 与服务共享 */
object PocketModeState {
    val isRunning = MutableStateFlow(false)
}
