package com.yesyesufcurs.creditcardnumberautofill.nfc

/**
 * Minimal BER-TLV parser sufficient for EMV data on payment cards.
 */
object TlvParser {

    data class Tlv(
        val tag: Int,
        val value: ByteArray,
        val children: List<Tlv>? = null
    )

    fun parseAll(data: ByteArray): List<Tlv> {
        val result = mutableListOf<Tlv>()
        var offset = 0
        while (offset < data.size) {
            val tag = readTag(data, offset) ?: break
            offset += tag.byteLength
            if (offset >= data.size) break
            val length = readLength(data, offset) ?: break
            offset += length.byteLength
            if (offset + length.value > data.size) break
            val value = data.copyOfRange(offset, offset + length.value)
            offset += length.value
            val children = if (tag.isConstructed) parseAll(value) else null
            result.add(Tlv(tag.number, value, children))
        }
        return result
    }

    /** Depth-first search for the first occurrence of any of [tags]. */
    fun findFirst(data: ByteArray, vararg tags: Int): ByteArray? {
        for (tlv in parseAll(data)) {
            if (tlv.tag in tags) return tlv.value
            tlv.children?.let { childList ->
                for (child in childList) {
                    if (child.tag in tags) return child.value
                    child.children?.let { nested ->
                        findFirstValue(nested, tags)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    private fun findFirstValue(tlvs: List<Tlv>, tags: IntArray): ByteArray? {
        for (tlv in tlvs) {
            if (tlv.tag in tags) return tlv.value
            tlv.children?.let { findFirstValue(it, tags)?.let { value -> return value } }
        }
        return null
    }

    /** Collect every value of [tag] anywhere in the tree. */
    fun findAll(data: ByteArray, tag: Int): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        fun walk(tlvs: List<Tlv>) {
            for (tlv in tlvs) {
                if (tlv.tag == tag) result.add(tlv.value)
                tlv.children?.let { walk(it) }
            }
        }
        walk(parseAll(data))
        return result
    }

    private class TagInfo(val number: Int, val byteLength: Int, val isConstructed: Boolean)

    private fun readTag(data: ByteArray, offset: Int): TagInfo? {
        if (offset >= data.size) return null
        val first = data[offset].toInt() and 0xFF
        val constructed = (first and 0x20) != 0
        if ((first and 0x1F) != 0x1F) return TagInfo(first, 1, constructed)
        if (offset + 1 >= data.size) return null
        val second = data[offset + 1].toInt() and 0xFF
        return TagInfo((first shl 8) or second, 2, constructed)
    }

    private class LengthInfo(val value: Int, val byteLength: Int)

    private fun readLength(data: ByteArray, offset: Int): LengthInfo? {
        if (offset >= data.size) return null
        val first = data[offset].toInt() and 0xFF
        if ((first and 0x80) == 0) return LengthInfo(first, 1)
        val count = first and 0x7F
        if (count == 0 || count > 4) return null
        if (offset + count >= data.size) return null
        var value = 0
        for (i in 1..count) {
            value = (value shl 8) or (data[offset + i].toInt() and 0xFF)
        }
        return LengthInfo(value, 1 + count)
    }
}
