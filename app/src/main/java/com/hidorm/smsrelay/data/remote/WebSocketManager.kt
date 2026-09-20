package com.hidorm.smsrelay.data.remote

import android.util.Log
import com.google.gson.Gson
import com.hidorm.smsrelay.data.remote.models.SmsTaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class WsConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class WebSocketManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val gson: Gson = Gson()
) {

    private val TAG = "WebSocketManager"
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var reconnectJob: Job? = null

    private var currentUrl: String = ""
    private var currentDeviceId: String = ""
    private var currentApiKey: String = ""
    private var shouldReconnect = false

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState

    var onTaskReceivedListener: ((SmsTaskItem) -> Unit)? = null
    var onLogListener: ((String) -> Unit)? = null

    fun connect(wsUrl: String, deviceId: String, apiKey: String) {
        currentUrl = wsUrl
        currentDeviceId = deviceId
        currentApiKey = apiKey
        shouldReconnect = true

        disconnect(clearReconnect = false)
        doConnect()
    }

    private fun doConnect() {
        if (currentUrl.isBlank()) {
            log("WebSocket URL이 지정되지 않았습니다.")
            return
        }

        _connectionState.value = WsConnectionState.CONNECTING
        log("WebSocket 연결 시도 중: $currentUrl")

        val request = Request.Builder()
            .url(currentUrl)
            .addHeader("X-Relay-Device-Id", currentDeviceId)
            .addHeader("X-Relay-Api-Key", currentApiKey)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = WsConnectionState.CONNECTED
                log("WebSocket 연결 성공")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                log("WebSocket 메시지 수신: $text")
                try {
                    val task = gson.fromJson(text, SmsTaskItem::class.java)
                    if (task.recipientPhone.isNotBlank() && task.content.isNotBlank()) {
                        onTaskReceivedListener?.invoke(task)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "수신 메시지 파싱 에러: ${e.message}", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = WsConnectionState.DISCONNECTED
                log("WebSocket 종료 중 (code: $code, reason: $reason)")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = WsConnectionState.DISCONNECTED
                log("WebSocket 종료됨")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = WsConnectionState.DISCONNECTED
                log("WebSocket 오류 발생: ${t.message}")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            log("5초 후 WebSocket 재연결 시도...")
            delay(5000L)
            if (shouldReconnect) {
                doConnect()
            }
        }
    }

    fun disconnect(clearReconnect: Boolean = true) {
        if (clearReconnect) {
            shouldReconnect = false
        }
        reconnectJob?.cancel()
        webSocket?.close(1000, "Normal Closure")
        webSocket = null
        _connectionState.value = WsConnectionState.DISCONNECTED
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        onLogListener?.invoke("[WS] $message")
    }
}
