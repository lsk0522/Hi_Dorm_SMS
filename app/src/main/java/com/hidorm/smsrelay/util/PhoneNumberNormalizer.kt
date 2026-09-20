package com.hidorm.smsrelay.util

object PhoneNumberNormalizer {

    /**
     * 전화번호를 대한민국 표준 형식(예: 01012345678)으로 정규화합니다.
     * +82 10-1234-5678, 821012345678, 010-1234-5678, 1012345678 등을 일관되게 변환합니다.
     */
    fun normalize(rawNumber: String?): String {
        if (rawNumber.isNullOrBlank()) return ""

        // 1. 숫자만 추출
        var digits = rawNumber.replace("[^0-9]".toRegex(), "")

        // 2. 대한민국 국가번호(+82) 처리: 82로 시작하고 10자리 이상인 경우 82를 0으로 치환
        if (digits.startsWith("82") && digits.length >= 10) {
            digits = "0" + digits.substring(2)
        }
        // 3. 앞자리 0이 누락된 번호(예: 1012345678)는 0 추가
        else if (digits.startsWith("10") && digits.length == 10) {
            digits = "0$digits"
        }

        return digits
    }

    /**
     * 표시용 하이픈 포맷팅 (010-1234-5678)
     */
    fun formatDisplay(rawNumber: String?): String {
        val clean = normalize(rawNumber)
        return when {
            clean.length == 11 && clean.startsWith("01") -> {
                "${clean.substring(0, 3)}-${clean.substring(3, 7)}-${clean.substring(7)}"
            }
            clean.length == 10 && clean.startsWith("01") -> {
                "${clean.substring(0, 3)}-${clean.substring(3, 6)}-${clean.substring(6)}"
            }
            clean.length == 10 && clean.startsWith("02") -> {
                "${clean.substring(0, 2)}-${clean.substring(2, 6)}-${clean.substring(6)}"
            }
            clean.length == 9 && clean.startsWith("02") -> {
                "${clean.substring(0, 2)}-${clean.substring(2, 5)}-${clean.substring(5)}"
            }
            else -> clean
        }
    }

    /**
     * 쉼표, 줄바꿈, 세미콜론 등으로 구분된 다중 전화번호 문자열을 정규화된 목록으로 분리합니다.
     */
    fun parseMultipleNumbers(rawListString: String?): List<String> {
        if (rawListString.isNullOrBlank()) return emptyList()
        return rawListString
            .split(",", "\n", ";", "/")
            .map { normalize(it.trim()) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * 수신 발신자 번호가 허용된 트리거 목록 중 하나와 일치하는지 검사합니다.
     * 허용 목록이 비어있으면 전체 허용(Any)으로 판단합니다.
     */
    fun isSenderAllowed(incomingSender: String, allowedSendersText: String): Boolean {
        val allowedList = parseMultipleNumbers(allowedSendersText)
        if (allowedList.isEmpty()) return true // 비어있으면 모든 발신자 허용

        val normalizedIncoming = normalize(incomingSender)
        return allowedList.any { allowed ->
            normalizedIncoming == allowed ||
            normalizedIncoming.endsWith(allowed) ||
            allowed.endsWith(normalizedIncoming)
        }
    }
}
