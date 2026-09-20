package com.hidorm.smsrelay

import com.hidorm.smsrelay.util.PhoneNumberNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberNormalizerTest {

    @Test
    fun normalize_standardKoreanMobileNumber() {
        val input = "010-1234-5678"
        val expected = "01012345678"
        assertEquals(expected, PhoneNumberNormalizer.normalize(input))
    }

    @Test
    fun normalize_internationalPrefixWithPlus() {
        val input = "+82 10 1234 5678"
        val expected = "01012345678"
        assertEquals(expected, PhoneNumberNormalizer.normalize(input))
    }

    @Test
    fun normalize_internationalPrefixWithoutPlus() {
        val input = "821012345678"
        val expected = "01012345678"
        assertEquals(expected, PhoneNumberNormalizer.normalize(input))
    }

    @Test
    fun normalize_missingLeadingZero() {
        val input = "1012345678"
        val expected = "01012345678"
        assertEquals(expected, PhoneNumberNormalizer.normalize(input))
    }

    @Test
    fun formatDisplay_formatsHyphenProperly() {
        val input = "01012345678"
        val expected = "010-1234-5678"
        assertEquals(expected, PhoneNumberNormalizer.formatDisplay(input))
    }

    @Test
    fun parseMultipleNumbers_supportsCommaAndNewline() {
        val raw = "010-1111-2222, +82 10 3333 4444\n01055556666"
        val parsed = PhoneNumberNormalizer.parseMultipleNumbers(raw)
        assertEquals(3, parsed.size)
        assertEquals("01011112222", parsed[0])
        assertEquals("01033334444", parsed[1])
        assertEquals("01055556666", parsed[2])
    }

    @Test
    fun isSenderAllowed_matchesVariousFormats() {
        val allowedList = "01012345678, 01099998888"

        // +82 표기로 들어와도 허용되어야 함
        assertTrue(PhoneNumberNormalizer.isSenderAllowed("+82 10-1234-5678", allowedList))
        assertTrue(PhoneNumberNormalizer.isSenderAllowed("010-1234-5678", allowedList))
        assertTrue(PhoneNumberNormalizer.isSenderAllowed("01099998888", allowedList))

        // 허용되지 않은 번호는 차단되어야 함
        assertFalse(PhoneNumberNormalizer.isSenderAllowed("010-5555-5555", allowedList))
    }

    @Test
    fun isSenderAllowed_emptyListAllowsAll() {
        assertTrue(PhoneNumberNormalizer.isSenderAllowed("01012345678", ""))
        assertTrue(PhoneNumberNormalizer.isSenderAllowed("01099998888", "   "))
    }
}
