package com.volumekey.counter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.VolumeProviderCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.volumekey.counter.MainActivity
import com.volumekey.counter.R
import com.volumekey.counter.data.AppDatabase
import com.volumekey.counter.data.CounterCoordinator
import com.volumekey.counter.data.CounterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 熄屏口袋模式：mediaPlayback 前台服务。
 *
 * 原理（调研验证的 VolumePowerApp 方案）：MediaSessionCompat 注册远程音量
 * （setPlaybackToRemote + VolumeProviderCompat）并处于 STATE_PLAYING、伴随循环
 * 静音 AudioTrack 时，系统把音量键路由给本会话的 onAdjustVolume 回调，而非调节
 * 系统音量——熄屏、锁屏状态下持续有效。
 */
class CountingService : android.app.Service() {

    companion object {
        const val CHANNEL_ID = "pocket_counting"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.volumekey.counter.action.STOP"
        const val ACTION_MINUS = "com.volumekey.counter.action.MINUS"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CountingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CountingService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var repo: CounterRepository? = null
    private var counterId: Long = -1L

    private var mediaSession: MediaSessionCompat? = null
    private var audioTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    /**
     * 连发（长按）去抖：
     * - 手动连按最快约 200ms/次，长按连发 50~200ms/次；
     * - 计数最小间隔 200ms；400ms 窗口内出现 ≥4 次判定为物理长按（50ms 级连发），静默抑制直到 500ms 无事件（松手）。
     */
    private var lastRawAt = 0L
    private var lastCountAt = 0L
    private var burstWindowStart = 0L
    private var burstCount = 0
    private var suppressed = false

    private var lastNotifiedText: String? = null

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        repo = CounterRepository(db, db.counterDao())
        audioManager = getSystemService(AudioManager::class.java)

        createChannel()
        startForegroundWithNotification("口袋模式启动中…")

        // 阻塞取一次激活计数器，避免服务早期收到音量键事件时无计数器可用
        counterId = runBlocking { CounterCoordinator.ensureActiveCounter(this@CountingService) }

        setupMediaSession()
        startSilentLoop()
        requestAudioFocus()
        observeCounterForNotification()
        PocketModeState.isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_MINUS -> {
                val id = counterId
                val repository = repo
                if (id > 0 && repository != null) {
                    serviceScope.launch { repository.adjust(id, -1L, CounterRepository.SOURCE_BUTTON) }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- 音量键路由核心 ----

    private fun setupMediaSession() {
        val session = MediaSessionCompat(this, "VolumeCounterSession")
        val provider = object : VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_RELATIVE, 100, 50) {
            override fun onAdjustVolume(direction: Int) {
                if (direction != 0) onVolumeAdjust(direction)
            }
        }
        session.setPlaybackToRemote(provider)
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                .build()
        )
        session.isActive = true
        mediaSession = session
    }

    private fun onVolumeAdjust(direction: Int) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (suppressed) {
            if (now - lastRawAt > 500) suppressed = false else {
                lastRawAt = now
                return
            }
        }
        lastRawAt = now
        if (now - burstWindowStart <= 400) burstCount++ else {
            burstWindowStart = now
            burstCount = 1
        }
        if (burstCount >= 4) {
            suppressed = true
            return
        }
        if (now - lastCountAt < 200) return
        lastCountAt = now

        vibrateTick()
        val id = counterId
        val repository = repo
        val routingItemId = com.volumekey.counter.service.CountRouting.activeInventoryItemId.value
        if (repository != null) {
            if (routingItemId != null) {
                // 清单盘点条目激活：音量键计入该条目实盘数
                serviceScope.launch { repository.adjustInventoryItem(routingItemId, direction.toLong()) }
            } else if (id > 0) {
                serviceScope.launch {
                    repository.adjust(id, direction.toLong(), CounterRepository.SOURCE_POCKET)
                }
            }
        }
    }

    // ---- 静音音轨：保持媒体会话优先级 + 熄屏时 CPU 不休眠 ----

    private fun startSilentLoop() {
        try {
            val sampleRate = 44100
            val frames = sampleRate / 10 // 100ms 静音
            val bufBytes = frames * 2    // 16bit 单声道
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack(attributes, format, bufBytes, AudioTrack.MODE_STATIC, 0)
            track.write(ByteArray(bufBytes), 0, bufBytes)
            track.setLoopPoints(0, frames, -1)
            track.play()
            audioTrack = track
        } catch (e: Exception) {
            // 静音轨失败时服务仍可运行（前台状态+会话激活），仅熄屏稳定性下降
            android.util.Log.w("CountingService", "silent loop failed", e)
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    // ---- 通知 ----

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "口袋模式计数",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "熄屏/后台用音量键计数时显示当前计数"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, countText: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CountingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val minusIntent = PendingIntent.getService(
            this, 2,
            Intent(this, CountingService::class.java).setAction(ACTION_MINUS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_counter)
            .setContentTitle(countText)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .addAction(0, "−1 修正", minusIntent)
            .addAction(0, "结束", stopIntent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$countText\n$text")
                    .setSummaryText("音量键计数器 · 口袋模式")
            )
            .build()
    }

    private fun startForegroundWithNotification(text: String) {
        val notification = buildNotification(text, "音量键计数器 · 口袋模式")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeCounterForNotification() {
        val repository = repo ?: return
        val id = counterId
        if (id <= 0) return
        serviceScope.launch {
            repository.observeCounter(id).collectLatest { counter ->
                val value = counter?.value ?: 0L
                val countText = "当前计数：$value"
                val subText = "${counter?.name ?: "计数器"} · 音量键计数中"
                if (countText != lastNotifiedText) {
                    lastNotifiedText = countText
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID, buildNotification(subText, countText))
                    // 锁屏媒体卡片标题实时显示计数
                    mediaSession?.setMetadata(
                        android.support.v4.media.MediaMetadataCompat.Builder()
                            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, countText)
                            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, counter?.name ?: "")
                            .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                            .build()
                    )
                }
            }
        }
    }

    // ---- 释放 ----

    override fun onDestroy() {
        PocketModeState.isRunning.value = false
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
        serviceScope.cancel()
        super.onDestroy()
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
}
