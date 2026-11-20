package com.callgalli.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun `all common ways of writing the same mobile match`() {
        val expected = "9848022338"
        listOf(
            "9848022338",
            "+91 98480 22338",
            "+91-98480-22338",
            "098480 22338",
            "0091 9848022338",
            "(+91) 98480-22338",
            "919848022338",
        ).forEach { assertEquals("for '$it'", expected, PhoneNumbers.normalize(it)) }
    }

    @Test
    fun `landline with STD code drops the leading zero`() {
        assertEquals("4023456789", PhoneNumbers.normalize("040-2345 6789"))
    }

    @Test
    fun `short codes are left alone`() {
        assertEquals("112", PhoneNumbers.normalize("112"))
        assertEquals("911", PhoneNumbers.normalize("911"))
        assertEquals("108", PhoneNumbers.normalize("108"))
    }

    @Test
    fun `blank or hidden numbers become empty`() {
        assertEquals("", PhoneNumbers.normalize(null))
        assertEquals("", PhoneNumbers.normalize(""))
        assertEquals("", PhoneNumbers.normalize("Private"))
    }

    @Test
    fun `emergency numbers are recognised`() {
        EmergencyNumbers.ALL.forEach { assertTrue(EmergencyNumbers.isEmergency(PhoneNumbers.normalize(it))) }
        assertFalse(EmergencyNumbers.isEmergency("9848022338"))
    }
}
