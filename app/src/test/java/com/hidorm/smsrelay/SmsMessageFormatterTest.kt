package com.hidorm.smsrelay

import com.hidorm.smsrelay.util.SmsMessageFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsMessageFormatterTest {

    @Test
    fun calculateByteLength_koreanCharacters() {
        val koreanText = "안녕하세요" // 5글자 * 2바이트 = 10바이트
        assertEquals(10, SmsMessageFormatter.calculateByteLength(koreanText))
    }

    @Test
    fun calculateByteLength_asciiCharacters() {
        val asciiText = "Hello World!" // 12바이트
        assertEquals(12, SmsMessageFormatter.calculateByteLength(asciiText))
    }

    @Test
    fun isLms_boundaryCheck() {
        // 한글 45글자 = 90바이트 (SMS 한계치)
        val boundarySms = "가".repeat(45)
        assertEquals(90, SmsMessageFormatter.calculateByteLength(boundarySms))
        assertFalse(SmsMessageFormatter.isLms(boundarySms))

        // 한글 46글자 = 92바이트 (LMS 초과)
        val lmsText = "가".repeat(46)
        assertEquals(92, SmsMessageFormatter.calculateByteLength(lmsText))
        assertTrue(SmsMessageFormatter.isLms(lmsText))
    }

    @Test
    fun formatRelayBody_withPrefix() {
        val originalSender = "01012345678"
        val body = "테스트 메시지입니다."
        val formatted = SmsMessageFormatter.formatRelayBody(originalSender, body, includePrefix = true)

        assertTrue(formatted.startsWith("[전달: 010-1234-5678]"))
        assertTrue(formatted.contains(body))
    }

    @Test
    fun formatRelayBody_withoutPrefix() {
        val originalSender = "01012345678"
        val body = "원문 그대로 전달."
        val formatted = SmsMessageFormatter.formatRelayBody(originalSender, body, includePrefix = false)

        assertEquals(body, formatted)
    }
}
