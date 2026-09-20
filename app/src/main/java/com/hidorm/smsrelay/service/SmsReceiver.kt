package com.hidorm.smsrelay.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import com.hidorm.smsrelay.HiDormRelayApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class SmsReceiver : BroadcastReceiver() {

    private val TAG = "SmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val app = context.applicationContext as HiDormRelayApp
            val repository = app.repository
            val sender = messages[0].originatingAddress ?: "UNKNOWN"
            val fullBody = StringBuilder()

            for (sms in messages) {
                fullBody.append(sms.messageBody)
            }

            val body = fullBody.toString()
            Log.i(TAG, "SMS 수신 감지: $sender -> $body")

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HiDormRelay::SmsReceiverWakeLock"
            )
            wakeLock.acquire(15000L) // 15초 임시 WakeLock 유지

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 1. [1번 단말 ➔ 2번 단말 ➔ 3번 단말] 직결 자동 포워딩 로직
                    if (repository.isForwardingEnabled) {
                        handleDirectForwarding(app, sender, body)
                    }

                    // 2. 서버 연동 인바운드 웹훅 (서버 설정이 활성화된 경우)
                    if (repository.serverUrl.isNotBlank() && repository.serverUrl.startsWith("http")) {
                        app.repository.sendInboundSms(sender, body)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "SMS 처리/포워딩 중 오류 발생: ${e.message}", e)
                } finally {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun handleDirectForwarding(
        app: HiDormRelayApp,
        sender: String,
        body: String
    ) {
        val repository = app.repository
        val triggerSender = repository.triggerSenderNumber.trim()
        val targetRecipient = repository.targetRecipientNumber.trim()
        val keywordFilter = repository.forwardKeywordFilter.trim()

        if (targetRecipient.isBlank()) {
            Log.w(TAG, "포워딩 대상 3번 폰 번호가 지정되지 않았습니다.")
            return
        }

        // 번호 정규화 (숫자만 추출)
        val cleanSender = sender.replace("[^0-9]".toRegex(), "")
        val cleanTrigger = triggerSender.replace("[^0-9]".toRegex(), "")

        // 1번 폰 번호 일치 검증 (트리거 번호가 비어있으면 모든 수신 SMS 전달)
        val isSenderMatched = cleanTrigger.isBlank() ||
                cleanSender.endsWith(cleanTrigger) ||
                cleanTrigger.endsWith(cleanSender)

        if (!isSenderMatched) {
            Log.d(TAG, "발신자($sender)가 트리거 번호($triggerSender)와 일치하지 않아 포워딩을 건너뜁니다.")
            return
        }

        // 키워드 필터 검증
        if (keywordFilter.isNotBlank() && !body.contains(keywordFilter)) {
            Log.d(TAG, "메시지에 지정된 키워드('$keywordFilter')가 포함되지 않아 포워딩을 건너뜁니다.")
            return
        }

        Log.i(TAG, "1번 폰($sender) 발신 메시지 일치 확인. 3번 폰($targetRecipient)으로 릴레이 발송 시작...")

        // 전달 본문 구성
        val relayContent = if (repository.includeSenderPrefix) {
            "[전달: $sender]\n$body"
        } else {
            body
        }

        val taskId = "fwd_${UUID.randomUUID().toString().take(8)}"
        val sendResult = app.smsSender.sendSms(
            destinationAddress = targetRecipient,
            messageText = relayContent,
            taskId = taskId
        )

        if (sendResult.isSuccess) {
            Log.i(TAG, "3번 폰($targetRecipient)으로 SMS 릴레이 발송 성공!")
        } else {
            Log.e(TAG, "3번 폰으로 SMS 릴레이 발송 실패: ${sendResult.errorMessage}")
        }
    }
}
