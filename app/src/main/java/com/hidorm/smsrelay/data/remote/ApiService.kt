package com.hidorm.smsrelay.data.remote

import com.hidorm.smsrelay.data.remote.models.HandshakeRequest
import com.hidorm.smsrelay.data.remote.models.HandshakeResponse
import com.hidorm.smsrelay.data.remote.models.HeartbeatRequest
import com.hidorm.smsrelay.data.remote.models.InboundMessageRequest
import com.hidorm.smsrelay.data.remote.models.MessageStatusUpdate
import com.hidorm.smsrelay.data.remote.models.PendingMessagesResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @POST("api/v1/relay/auth/handshake")
    suspend fun handshake(
        @Header("X-Relay-Device-Id") deviceId: String,
        @Header("X-Relay-Api-Key") apiKey: String,
        @Body request: HandshakeRequest
    ): Response<HandshakeResponse>

    @GET("api/v1/relay/messages/pending")
    suspend fun getPendingMessages(
        @Header("X-Relay-Device-Id") deviceId: String,
        @Header("X-Relay-Api-Key") apiKey: String
    ): Response<PendingMessagesResponse>

    @POST("api/v1/relay/messages/{task_id}/status")
    suspend fun updateMessageStatus(
        @Header("X-Relay-Device-Id") deviceId: String,
        @Header("X-Relay-Api-Key") apiKey: String,
        @Path("task_id") taskId: String,
        @Body update: MessageStatusUpdate
    ): Response<Unit>

    @POST("api/v1/relay/inbound")
    suspend fun sendInboundMessage(
        @Header("X-Relay-Device-Id") deviceId: String,
        @Header("X-Relay-Api-Key") apiKey: String,
        @Body request: InboundMessageRequest
    ): Response<Unit>

    @POST("api/v1/relay/telemetry/heartbeat")
    suspend fun sendHeartbeat(
        @Header("X-Relay-Device-Id") deviceId: String,
        @Header("X-Relay-Api-Key") apiKey: String,
        @Body heartbeat: HeartbeatRequest
    ): Response<Unit>
}
