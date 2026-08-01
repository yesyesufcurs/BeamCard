package com.yesyesufcurs.creditcardnumberautofill

import com.yesyesufcurs.creditcardnumberautofill.nfc.CardNetwork
import com.yesyesufcurs.creditcardnumberautofill.nfc.Luhn
import com.yesyesufcurs.creditcardnumberautofill.nfc.TlvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

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

class TlvParserTest {

    private fun tlv(tag: Int, value: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        if (tag <= 0xFF && (tag and 0x1F) != 0x1F) {
            out.write(tag)
        } else {
            out.write(tag shr 8)
            out.write(tag and 0xFF)
        }
        if (value.size < 128) {
            out.write(value.size)
        } else {
            out.write(0x81)
            out.write(value.size)
        }
        out.write(value)
        return out.toByteArray()
    }

    @Test
    fun extractsTagsFromRecord() {
        val pan = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val expiry = byteArrayOf(0x25, 0x12)
        val name = "JOHN DOE".toByteArray(Charsets.US_ASCII)
        val record = tlv(0x5A, pan) + tlv(0x5F24, expiry) + tlv(0x5F20, name)

        assertTrue(TlvParser.findFirst(record, 0x5A).contentEquals(pan))
        assertTrue(TlvParser.findFirst(record, 0x5F24).contentEquals(expiry))
        assertTrue(TlvParser.findFirst(record, 0x5F20).contentEquals(name))
        assertEquals(null, TlvParser.findFirst(record, 0x4F))
    }

    @Test
    fun extractsAidsFromNestedFci() {
        val visa = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10)
        val mc = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x04, 0x10, 0x10)
        val fci = tlv(
            0x6F,
            tlv(0x84, "2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII)) +
                tlv(0xA5, tlv(0xBF0C.toInt(), tlv(0x4F, visa) + tlv(0x4F, mc)))
        )

        val aids = TlvParser.findAll(fci, 0x4F)
        assertEquals(2, aids.size)
        assertTrue(aids[0].contentEquals(visa))
        assertTrue(aids[1].contentEquals(mc))
    }

    @Test
    fun findFirstSearchesNestedTemplates() {
        val pan = byteArrayOf(0x41, 0x11)
        val rec = tlv(0x70, tlv(0x6F, tlv(0xA5, tlv(0x5F24, byteArrayOf(0x25, 0x12)))) + tlv(0x5A, pan))
        assertTrue(TlvParser.findFirst(rec, 0x5F24).contentEquals(byteArrayOf(0x25, 0x12)))
        assertTrue(TlvParser.findFirst(rec, 0x5A).contentEquals(pan))
    }
}
