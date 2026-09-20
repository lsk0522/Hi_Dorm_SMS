package com.hidorm.smsrelay.modem

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

data class SmsSendResult(
    val isSuccess: Boolean,
    val resultCode: Int,
    val statusString: String,
    val errorMessage: String? = null
)

class SmsSender(private val context: Context) {

    private val TAG = "SmsSender"

    private fun getSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    suspend fun sendSms(
        destinationAddress: String,
        messageText: String,
        taskId: String
    ): SmsSendResult = suspendCancellableCoroutine { continuation ->
        val smsManager = getSmsManager()
        val actionSent = "com.hidorm.smsrelay.SMS_SENT_${UUID.randomUUID()}"
        val actionDelivered = "com.hidorm.smsrelay.SMS_DELIVERED_${UUID.randomUUID()}"

        val sentIntent = Intent(actionSent)
        val sentPendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            sentIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val sentReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                try {
                    context.unregisterReceiver(this)
                } catch (e: Exception) {
                    Log.w(TAG, "Unregister receiver error: ${e.message}")
                }

                val resultCode = resultCode
                val (isSuccess, statusString, errorMsg) = parseResultCode(resultCode)

                if (continuation.isActive) {
                    continuation.resume(
                        SmsSendResult(
                            isSuccess = isSuccess,
                            resultCode = resultCode,
                            statusString = statusString,
                            errorMessage = errorMsg
                        )
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                sentReceiver,
                IntentFilter(actionSent),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(sentReceiver, IntentFilter(actionSent))
        }

        continuation.invokeOnCancellation {
            try {
                context.unregisterReceiver(sentReceiver)
            } catch (e: Exception) {
                // Ignore
            }
        }

        try {
            val parts = smsManager.divideMessage(messageText)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    if (i == parts.size - 1) {
                        sentIntents.add(sentPendingIntent)
                    } else {
                        sentIntents.add(
                            PendingIntent.getBroadcast(
                                context,
                                (taskId + i).hashCode(),
                                Intent(actionSent + "_part"),
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    PendingIntent.FLAG_IMMUTABLE
                                } else 0
                            )
                        )
                    }
                }
                smsManager.sendMultipartTextMessage(
                    destinationAddress,
                    null,
                    parts,
                    sentIntents,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    destinationAddress,
                    null,
                    messageText,
                    sentPendingIntent,
                    null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "SmsManager 발송 예외: ${e.message}", e)
            try {
                context.unregisterReceiver(sentReceiver)
            } catch (ignored: Exception) {}
            if (continuation.isActive) {
                continuation.resume(
                    SmsSendResult(
                        isSuccess = false,
                        resultCode = -1,
                        statusString = "EXCEPTION",
                        errorMessage = e.message
                    )
                )
            }
        }
    }

    private fun parseResultCode(resultCode: Int): Triple<Boolean, String, String?> {
        return when (resultCode) {
            Activity.RESULT_OK -> Triple(true, "SENT_OK", null)
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> Triple(false, "GENERIC_FAILURE", "일반 전송 오류")
            SmsManager.RESULT_ERROR_RADIO_OFF -> Triple(false, "RADIO_OFF", "비행기 탑승 모드 또는 모뎀 꺼짐")
            SmsManager.RESULT_ERROR_NULL_PDU -> Triple(false, "NULL_PDU", "PDU 생성 오류")
            SmsManager.RESULT_ERROR_NO_SERVICE -> Triple(false, "NO_SERVICE", "통신사 서비스 지역 이탈")
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> Triple(false, "LIMIT_EXCEEDED", "일일 전송 한도 초과")
            else -> Triple(false, "UNKNOWN_ERROR", "알 수 없는 오류 코드: $resultCode")
        }
    }
}
