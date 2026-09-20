package com.hidorm.smsrelay.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import com.hidorm.smsrelay.HiDormRelayApp
import com.hidorm.smsrelay.util.PhoneNumberNormalizer
import com.hidorm.smsrelay.util.SmsMessageFormatter
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
                fullBody.append(sms.messageBody ?: "")
            }

            val body = fullBody.toString()
            Log.i(TAG, "SMS 수신 감지: $sender -> $body")

            // 화면이 꺼져 있어도 전송 완료 시까지 CPU 슬립을 방지하는 WakeLock 획득
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HiDormRelay::SmsReceiverWakeLock"
            )
            wakeLock.acquire(20000L) // 최대 20초간 유지

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 1. [1번 단말 ➔ 2번 단말 ➔ 3번 단말] 직결 자동 포워딩 처리
                    if (repository.isForwardingEnabled) {
                        handleDirectForwarding(app, sender, body)
                    }

                    // 2. 서버 연동 인바운드 웹훅 (서버 설정 시)
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
        val triggerSenders = repository.triggerSenderNumber.trim()
        val targetRecipientsRaw = repository.targetRecipientNumber.trim()
        val keywordFilter = repository.forwardKeywordFilter.trim()

        if (targetRecipientsRaw.isBlank()) {
            Log.w(TAG, "포워딩 대상 3번 폰 번호가 지정되지 않았습니다.")
            return
        }

        // 1. 일일 발송 한도(스팸 락) 체크
        val todayStart = repository.getTodayStartTimestamp()
        val sentToday = app.database.messageDao().getTodaySentCount(todayStart)
        if (sentToday >= repository.dailyLimit) {
            Log.w(TAG, "일일 발송 상한(${repository.dailyLimit}건)에 도달하여 자동 포워딩이 일시 중지되었습니다.")
            return
        }

        // 2. 대한민국 국가번호(+82) 및 포맷 표준화 검증
        val isSenderAllowed = PhoneNumberNormalizer.isSenderAllowed(sender, triggerSenders)
        if (!isSenderAllowed) {
            Log.d(TAG, "발신자($sender)가 등록된 트리거 목록($triggerSenders)과 일치하지 않아 포워딩을 건너뜁니다.")
            return
        }

        // 3. 키워드 필터링 검증
        if (keywordFilter.isNotBlank() && !body.contains(keywordFilter)) {
            Log.d(TAG, "메시지에 지정된 키워드('$keywordFilter')가 포함되지 않아 건너뜁니다.")
            return
        }

        // 4. 수신 대상 목록 파싱 (다중 수신자 그룹 지원)
        val recipientList = PhoneNumberNormalizer.parseMultipleNumbers(targetRecipientsRaw)
        if (recipientList.isEmpty()) {
            Log.w(TAG, "유효한 수신자 번호가 없습니다.")
            return
        }

        // 5. 본문 포맷팅 (발신자 정보 포함 여부)
        val relayContent = SmsMessageFormatter.formatRelayBody(
            originalSender = sender,
            body = body,
            includePrefix = repository.includeSenderPrefix
        )

        Log.i(TAG, "포워딩 시작: 발신자=$sender, 대상자=${recipientList.size}명, 본문길이=${relayContent.length}자")

        // 6. 대상 번호들로 순차 발송
        for (recipient in recipientList) {
            val taskId = "fwd_${UUID.randomUUID().toString().take(8)}"
            val sendResult = app.smsSender.sendSms(
                destinationAddress = recipient,
                messageText = relayContent,
                taskId = taskId
            )

            if (sendResult.isSuccess) {
                Log.i(TAG, "릴레이 발송 성공: $recipient (${sendResult.statusString})")
            } else {
                Log.e(TAG, "릴레이 발송 실패: $recipient (${sendResult.errorMessage})")
            }

            // 로컬 Room DB에 전송 내역 영속화
            val entity = com.hidorm.smsrelay.data.local.MessageEntity(
                taskId = taskId,
                recipientPhone = recipient,
                content = relayContent,
                msgType = if (SmsMessageFormatter.isLms(relayContent)) "LMS" else "SMS",
                status = if (sendResult.isSuccess) "SENT" else "FAILED",
                resultCode = sendResult.statusString,
                errorMessage = sendResult.errorMessage,
                createdAt = System.currentTimeMillis(),
                sentAt = System.currentTimeMillis()
            )
            app.database.messageDao().insert(entity)

            // 다중 수신자 간 최소 딜레이 (1초)
            if (recipientList.size > 1) {
                kotlinx.coroutines.delay(1000L)
            }
        }
    }
}
