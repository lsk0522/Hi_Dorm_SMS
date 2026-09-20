package com.hidorm.smsrelay.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.hidorm.smsrelay.data.local.MessageDao
import com.hidorm.smsrelay.data.local.MessageEntity
import com.hidorm.smsrelay.data.remote.ApiService
import com.hidorm.smsrelay.data.remote.WebSocketManager
import com.hidorm.smsrelay.data.remote.models.HandshakeRequest
import com.hidorm.smsrelay.data.remote.models.HeartbeatRequest
import com.hidorm.smsrelay.data.remote.models.InboundMessageRequest
import com.hidorm.smsrelay.data.remote.models.MessageStatusUpdate
import com.hidorm.smsrelay.data.remote.models.SmsTaskItem
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class RelayRepository(
    private val context: Context,
    private val messageDao: MessageDao
) {

    private val TAG = "RelayRepository"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hidorm_relay_prefs", Context.MODE_PRIVATE)

    val wsManager: WebSocketManager = WebSocketManager()

    var serverUrl: String
        get() = prefs.getString("server_url", "https://api.hidorm.example.com") ?: "https://api.hidorm.example.com"
        set(value) = prefs.edit().putString("server_url", value).apply()

    var wsUrl: String
        get() = prefs.getString("ws_url", "wss://api.hidorm.example.com/relay/ws") ?: "wss://api.hidorm.example.com/relay/ws"
        set(value) = prefs.edit().putString("ws_url", value).apply()

    var deviceId: String
        get() = prefs.getString("device_id", "hidorm-bridge-01") ?: "hidorm-bridge-01"
        set(value) = prefs.edit().putString("device_id", value).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "secret-relay-key-1234") ?: "secret-relay-key-1234"
        set(value) = prefs.edit().putString("api_key", value).apply()

    var sendDelayMs: Long
        get() = prefs.getLong("send_delay_ms", 2000L)
        set(value) = prefs.edit().putLong("send_delay_ms", value).apply()

    var dailyLimit: Int
        get() = prefs.getInt("daily_limit", 450)
        set(value) = prefs.edit().putInt("daily_limit", value).apply()

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean("service_enabled", false)
        set(value) = prefs.edit().putBoolean("service_enabled", value).apply()

    // 1번 단말 -> 2번 단말 -> 3번 단말 직결 자동 포워딩 설정
    var isForwardingEnabled: Boolean
        get() = prefs.getBoolean("forwarding_enabled", true)
        set(value) = prefs.edit().putBoolean("forwarding_enabled", value).apply()

    var triggerSenderNumber: String
        get() = prefs.getString("trigger_sender_number", "01012345678") ?: "01012345678"
        set(value) = prefs.edit().putString("trigger_sender_number", value).apply()

    var targetRecipientNumber: String
        get() = prefs.getString("target_recipient_number", "01098765432") ?: "01098765432"
        set(value) = prefs.edit().putString("target_recipient_number", value).apply()

    var forwardKeywordFilter: String
        get() = prefs.getString("forward_keyword_filter", "") ?: ""
        set(value) = prefs.edit().putString("forward_keyword_filter", value).apply()

    var includeSenderPrefix: Boolean
        get() = prefs.getBoolean("include_sender_prefix", true)
        set(value) = prefs.edit().putBoolean("include_sender_prefix", value).apply()

    private var cachedApiService: ApiService? = null
    private var lastBaseUrl: String = ""

    private fun getApiService(): ApiService {
        val currentUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        if (cachedApiService == null || lastBaseUrl != currentUrl) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            cachedApiService = Retrofit.Builder()
                .baseUrl(currentUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(Gson()))
                .build()
                .create(ApiService::class.java)
            lastBaseUrl = currentUrl
        }
        return cachedApiService!!
    }

    suspend fun queueTask(task: SmsTaskItem): Boolean {
        val existing = messageDao.getByTaskId(task.taskId)
        if (existing != null) {
            return false
        }
        val entity = MessageEntity(
            taskId = task.taskId,
            recipientPhone = task.recipientPhone,
            content = task.content,
            msgType = task.msgType,
            status = "QUEUED"
        )
        messageDao.insert(entity)
        return true
    }

    suspend fun getNextPending(): MessageEntity? {
        return messageDao.getNextPendingMessage()
    }

    suspend fun updateMessageStatus(
        taskId: String,
        status: String,
        resultCode: String? = null,
        errorMessage: String? = null
    ) {
        val now = System.currentTimeMillis()
        messageDao.updateStatus(taskId, status, resultCode, errorMessage, now)

        try {
            val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(now))
            val update = MessageStatusUpdate(
                taskId = taskId,
                status = status,
                dispatchedAt = isoDate,
                resultCode = resultCode,
                carrierMessage = errorMessage,
                retryCount = 0
            )
            getApiService().updateMessageStatus(deviceId, apiKey, taskId, update)
        } catch (e: Exception) {
            Log.w(TAG, "서버 상태 콜백 전송 실패 (오프라인 등): ${e.message}")
        }
    }

    suspend fun pollPendingTasks(): Int {
        try {
            val response = getApiService().getPendingMessages(deviceId, apiKey)
            if (response.isSuccessful && response.body() != null) {
                val tasks = response.body()!!.tasks
                var count = 0
                for (task in tasks) {
                    if (queueTask(task)) count++
                }
                return count
            }
        } catch (e: Exception) {
            Log.w(TAG, "폴링 실패: ${e.message}")
        }
        return 0
    }

    suspend fun sendInboundSms(senderPhone: String, content: String) {
        try {
            val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            val request = InboundMessageRequest(
                senderPhone = senderPhone,
                content = content,
                receivedAt = isoDate
            )
            getApiService().sendInboundMessage(deviceId, apiKey, request)
        } catch (e: Exception) {
            Log.e(TAG, "인바운드 SMS 서버 전송 실패: ${e.message}", e)
        }
    }

    suspend fun sendHeartbeat(batteryLevel: Int, isCharging: Boolean, networkType: String, signalDbm: Int) {
        try {
            val todayStart = getTodayStartTimestamp()
            val sentToday = messageDao.getTodaySentCount(todayStart)
            val pending = messageDao.getPendingCount()
            val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

            val req = HeartbeatRequest(
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                networkType = networkType,
                signalDbm = signalDbm,
                todaySentCount = sentToday,
                queueDepth = pending,
                timestamp = isoDate
            )
            getApiService().sendHeartbeat(deviceId, apiKey, req)
        } catch (e: Exception) {
            Log.w(TAG, "하트비트 전송 실패: ${e.message}")
        }
    }

    fun getTodayStartTimestamp(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
