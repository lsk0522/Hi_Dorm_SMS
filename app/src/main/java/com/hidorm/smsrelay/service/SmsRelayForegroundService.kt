package com.hidorm.smsrelay.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hidorm.smsrelay.HiDormRelayApp
import com.hidorm.smsrelay.R
import com.hidorm.smsrelay.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SmsRelayForegroundService : Service() {

    private val TAG = "SmsRelayService"
    private val NOTIFICATION_ID = 1001

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null

    private val app by lazy { application as HiDormRelayApp }
    private val repository by lazy { app.repository }
    private val smsSender by lazy { app.smsSender }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HiDormRelay::ServiceWakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRelayService()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startRelayService()
                return START_STICKY
            }
        }
        return START_STICKY
    }

    private fun startRelayService() {
        if (isRunning) return
        isRunning = true
        repository.isServiceEnabled = true

        startForeground(NOTIFICATION_ID, buildNotification("서비스 시작 중..."))

        // 1. WebSocket 연결
        connectWebSocket()

        // 2. 메시지 발송 루프 시작
        startMessageProcessingLoop()

        // 3. 대체 폴링 루프 시작 (백업용)
        startFallbackPollingLoop()

        // 4. 하트비트 주기 발송 루프 시작
        startHeartbeatLoop()

        Log.i(TAG, "Hi_Dorm SMS Relay 서비스 가동 완료")
    }

    private fun stopRelayService() {
        isRunning = false
        repository.isServiceEnabled = false
        repository.wsManager.disconnect()
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Hi_Dorm SMS Relay 서비스 중지됨")
    }

    private fun connectWebSocket() {
        repository.wsManager.onTaskReceivedListener = { task ->
            serviceScope.launch {
                Log.d(TAG, "WS를 통해 새 작업 수신: ${task.taskId}")
                repository.queueTask(task)
            }
        }
        repository.wsManager.connect(
            wsUrl = repository.wsUrl,
            deviceId = repository.deviceId,
            apiKey = repository.apiKey
        )
    }

    private fun startMessageProcessingLoop() {
        serviceScope.launch {
            while (isActive && isRunning) {
                try {
                    // 일일 한도 체크 (스팸 방지)
                    val todayStart = repository.getTodayStartTimestamp()
                    val todaySent = app.database.messageDao().getTodaySentCount(todayStart)
                    if (todaySent >= repository.dailyLimit) {
                        updateNotification("일일 발송 한도(${repository.dailyLimit}건) 도달 - 발송 일시 정지")
                        delay(60000L) // 1분 대기 후 재점검
                        continue
                    }

                    val pending = repository.getNextPending()
                    if (pending != null) {
                        wakeLock?.acquire(10000L) // 10초 임시 WakeLock 획득
                        updateNotification("전송 중: ${pending.recipientPhone} (금일 $todaySent/${repository.dailyLimit}건)")

                        repository.updateMessageStatus(pending.taskId, "DISPATCHED")

                        val result = smsSender.sendSms(
                            destinationAddress = pending.recipientPhone,
                            messageText = pending.content,
                            taskId = pending.taskId
                        )

                        if (result.isSuccess) {
                            Log.i(TAG, "발송 성공: ${pending.taskId} -> ${pending.recipientPhone}")
                            repository.updateMessageStatus(
                                taskId = pending.taskId,
                                status = "SENT",
                                resultCode = result.statusString
                            )
                        } else {
                            Log.e(TAG, "발송 실패: ${pending.taskId}, 사유: ${result.errorMessage}")
                            repository.updateMessageStatus(
                                taskId = pending.taskId,
                                status = "FAILED",
                                resultCode = result.statusString,
                                errorMessage = result.errorMessage
                            )
                        }

                        // 통신사 스팸 방지 딜레이 적용 (기본 2초)
                        delay(repository.sendDelayMs)
                    } else {
                        updateNotification("대기 중 (금일 누적 발송: $todaySent/${repository.dailyLimit}건)")
                        delay(2000L) // 대기 큐 없을 시 2초 대기
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "메시지 처리 루프 오류: ${e.message}", e)
                    delay(3000L)
                }
            }
        }
    }

    private fun startFallbackPollingLoop() {
        serviceScope.launch {
            while (isActive && isRunning) {
                delay(10000L) // 10초 주기
                try {
                    // 웹소켓이 끊어졌거나 추가 태스크가 있을 경우 폴링
                    repository.pollPendingTasks()
                } catch (e: Exception) {
                    Log.w(TAG, "폴링 중 오류: ${e.message}")
                }
            }
        }
    }

    private fun startHeartbeatLoop() {
        serviceScope.launch {
            while (isActive && isRunning) {
                try {
                    val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0

                    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    val networkType = getNetworkType()
                    repository.sendHeartbeat(
                        batteryLevel = batteryPct,
                        isCharging = isCharging,
                        networkType = networkType,
                        signalDbm = -75
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "하트비트 루프 오류: ${e.message}")
                }
                delay(30000L) // 30초 간격
            }
        }
    }

    private fun getNetworkType(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return "NONE"
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "NONE"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            else -> "OTHER"
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, HiDormRelayApp.CHANNEL_ID)
            .setContentTitle("Hi_Dorm SMS Relay 중계기")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRelayService()
        super.onDestroy()
    }
}
