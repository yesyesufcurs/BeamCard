package com.yesyesufcurs.creditcardnumberautofill

import com.yesyesufcurs.creditcardnumberautofill.nfc.CardNetwork
import com.yesyesufcurs.creditcardnumberautofill.nfc.Luhn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuhnTest {

    @Test
    fun validNumbers() {
        assertTrue(Luhn.isValid("4111111111111111"))
        assertTrue(Luhn.isValid("5500005555555559"))
        assertTrue(Luhn.isValid("371449635398431"))
        assertTrue(Luhn.isValid("6011111111111117"))
    }

    @Test
    fun invalidNumbers() {
        assertFalse(Luhn.isValid("4111111111111112"))
        assertFalse(Luhn.isValid(""))
        assertFalse(Luhn.isValid("1234abcd"))
        assertFalse(Luhn.isValid("1234"))
    }
}

class CardNetworkTest {

    @Test
    fun detectsNetworks() {
        assertEquals(CardNetwork.VISA, CardNetwork.detect("4111111111111111"))
        assertEquals(CardNetwork.MASTERCARD, CardNetwork.detect("5111111111111111"))
        assertEquals(CardNetwork.MASTERCARD, CardNetwork.detect("2221000000000009"))
        assertEquals(CardNetwork.AMEX, CardNetwork.detect("371449635398431"))
        assertEquals(CardNetwork.DISCOVER, CardNetwork.detect("6011111111111117"))
        assertEquals(CardNetwork.JCB, CardNetwork.detect("3530111333300000"))
        assertEquals(CardNetwork.MAESTRO, CardNetwork.detect("5018000000000000"))
        assertEquals(CardNetwork.UNKNOWN, CardNetwork.detect("12"))
    }
}
