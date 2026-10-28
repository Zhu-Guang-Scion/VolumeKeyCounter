package com.volumekey.counter.service

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 音量键计数路由：当清单盘点的某条目处于盘点中（全屏盘点页打开）时，
 * 音量键（前台拦截与口袋模式两路）都计入该条目的实盘数量，
 * 否则计入普通计数器。boardOpen 表示盘点板面可见——
 * 此时若无激活条目，两路都应忽略按键（防误改背后的普通计数器）。
 */
object CountRouting {
    val activeInventoryItemId = MutableStateFlow<Long?>(null)
    val boardOpen = MutableStateFlow(false)
}
