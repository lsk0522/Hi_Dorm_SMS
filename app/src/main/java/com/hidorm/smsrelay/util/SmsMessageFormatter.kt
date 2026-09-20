package com.hidorm.smsrelay.util

import java.nio.charset.Charset

object SmsMessageFormatter {

    private val EUC_KR: Charset by lazy {
        try {
            Charset.forName("EUC-KR")
        } catch (e: Exception) {
            Charset.forName("UTF-8")
        }
    }

    /**
     * 국내 통신사 기준 EUC-KR 바이트 길이를 계산합니다. (한글 2바이트, 영문/숫자 1바이트)
     */
    fun calculateByteLength(text: String): Int {
        return text.toByteArray(EUC_KR).size
    }

    /**
     * 바이트 길이에 따라 SMS(90바이트 이하) 또는 LMS(90바이트 초과) 여부를 판단합니다.
     */
    fun isLms(text: String): Boolean {
        return calculateByteLength(text) > 90
    }

    /**
     * 릴레이 전달용 최종 본문을 포맷팅합니다.
     */
    fun formatRelayBody(
        originalSender: String,
        body: String,
        includePrefix: Boolean = true,
        includeTimestamp: Boolean = false
    ): String {
        val sb = StringBuilder()

        if (includePrefix) {
            val formattedSender = PhoneNumberNormalizer.formatDisplay(originalSender)
            sb.append("[전달: $formattedSender]")
            if (includeTimestamp) {
                val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA).format(java.util.Date())
                sb.append(" ($now)")
            }
            sb.append("\n")
        }

        sb.append(body)
        return sb.toString()
    }
}
