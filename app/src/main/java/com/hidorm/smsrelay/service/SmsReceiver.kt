package com.hidorm.smsrelay.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.hidorm.smsrelay.HiDormRelayApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val TAG = "SmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val app = context.applicationContext as HiDormRelayApp
            val sender = messages[0].originatingAddress ?: "UNKNOWN"
            val fullBody = StringBuilder()

            for (sms in messages) {
                fullBody.append(sms.messageBody)
            }

            val body = fullBody.toString()
            Log.i(TAG, "사생 회신 SMS 수신 감지: $sender -> $body")

            // 비동기로 서버 Inbound Webhook 전송
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    app.repository.sendInboundSms(sender, body)
                } catch (e: Exception) {
                    Log.e(TAG, "인바운드 릴레이 전송 실패: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
