package com.callgalli.rules

/**
 * Turns any way of writing an Indian phone number into one key, so
 * "+91 98480 22338", "098480-22338" and "9848022338" are treated as the same person.
 *
 * Rules: keep digits only, drop the "0091" / "+91" country code, drop leading zeros.
 */
object PhoneNumbers {

    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("0091") && digits.length > 4) digits = digits.substring(4)
        // "+91XXXXXXXXXX" -> 12 digits starting with 91. Only strip when what is left is a full
        // 10-digit mobile/landline, so short codes like "911" are left alone.
        if (digits.length == 12 && digits.startsWith("91")) digits = digits.substring(2)
        digits = digits.trimStart('0')
        return digits
    }
}

/** Numbers that are never timed, rejected or blocked. */
object EmergencyNumbers {
    /** 112 = all-in-one emergency, 100 = police, 101 = fire, 102 = ambulance, 108 = emergency ambulance. */
    val ALL: List<String> = listOf("112", "100", "101", "102", "108")

    fun isEmergency(normalized: String): Boolean = normalized in ALL
}
