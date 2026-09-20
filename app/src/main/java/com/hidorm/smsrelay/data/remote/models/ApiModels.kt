package com.hidorm.smsrelay.data.remote.models

import com.google.gson.annotations.SerializedName

data class HandshakeRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("app_version") val appVersion: String,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("sim_operator") val simOperator: String?,
    @SerializedName("os_version") val osVersion: String
)

data class HandshakeResponse(
    @SerializedName("status") val status: String,
    @SerializedName("session_token") val sessionToken: String?,
    @SerializedName("config") val config: RelayConfig?
)

data class RelayConfig(
    @SerializedName("heartbeat_interval_sec") val heartbeatIntervalSec: Int = 30,
    @SerializedName("send_delay_ms") val sendDelayMs: Long = 2000L,
    @SerializedName("daily_limit") val dailyLimit: Int = 450,
    @SerializedName("fallback_poll_interval_sec") val fallbackPollIntervalSec: Int = 5
)

data class PendingMessagesResponse(
    @SerializedName("tasks") val tasks: List<SmsTaskItem>
)

data class SmsTaskItem(
    @SerializedName("task_id") val taskId: String,
    @SerializedName("recipient_phone") val recipientPhone: String,
    @SerializedName("content") val content: String,
    @SerializedName("msg_type") val msgType: String = "SMS",
    @SerializedName("priority") val priority: Int = 1
)

data class MessageStatusUpdate(
    @SerializedName("task_id") val taskId: String,
    @SerializedName("status") val status: String, // SENT, DELIVERED, FAILED
    @SerializedName("dispatched_at") val dispatchedAt: String,
    @SerializedName("result_code") val resultCode: String?,
    @SerializedName("carrier_message") val carrierMessage: String?,
    @SerializedName("retry_count") val retryCount: Int
)

data class InboundMessageRequest(
    @SerializedName("sender_phone") val senderPhone: String,
    @SerializedName("content") val content: String,
    @SerializedName("received_at") val receivedAt: String
)

data class HeartbeatRequest(
    @SerializedName("battery_level") val batteryLevel: Int,
    @SerializedName("is_charging") val isCharging: Boolean,
    @SerializedName("network_type") val networkType: String,
    @SerializedName("signal_dbm") val signalDbm: Int,
    @SerializedName("today_sent_count") val todaySentCount: Int,
    @SerializedName("queue_depth") val queueDepth: Int,
    @SerializedName("timestamp") val timestamp: String
)
