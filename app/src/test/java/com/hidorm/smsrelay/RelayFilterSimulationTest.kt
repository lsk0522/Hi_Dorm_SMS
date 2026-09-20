package com.hidorm.smsrelay

import com.hidorm.smsrelay.util.PhoneNumberNormalizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayFilterSimulationTest {

    @Test
    fun filterSimulation_keywordAndSenderRules() {
        val triggerSenders = "010-1234-5678, 010-8888-9999"
        val requiredKeyword = "[공지]"

        // 1. 발신자 일치 + 키워드 일치 -> 통과
        val sender1 = "+82 10 1234 5678"
        val body1 = "[공지] 오늘 밤 11시 소방점검이 있습니다."
        assertTrue(PhoneNumberNormalizer.isSenderAllowed(sender1, triggerSenders))
        assertTrue(body1.contains(requiredKeyword))

        // 2. 발신자 일치 + 키워드 누락 -> 차단
        val sender2 = "01012345678"
        val body2 = "개인적인 메시지입니다."
        assertTrue(PhoneNumberNormalizer.isSenderAllowed(sender2, triggerSenders))
        assertFalse(body2.contains(requiredKeyword))

        // 3. 발신자 불일치 -> 차단
        val spamSender = "010-0000-0000"
        val spamBody = "[공지] 불법 대출 광고"
        assertFalse(PhoneNumberNormalizer.isSenderAllowed(spamSender, triggerSenders))
    }

    @Test
    fun multiRecipient_relayDistribution() {
        val targetRecipientsRaw = "010-1111-2222, 010-3333-4444, 010-5555-6666"
        val recipients = PhoneNumberNormalizer.parseMultipleNumbers(targetRecipientsRaw)

        assertTrue(recipients.contains("01011112222"))
        assertTrue(recipients.contains("01033334444"))
        assertTrue(recipients.contains("01055556666"))
        assertFalse(recipients.contains("01099999999"))
    }
}
