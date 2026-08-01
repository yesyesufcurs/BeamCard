package com.yesyesufcurs.creditcardnumberautofill.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

class CardReadException(message: String) : Exception(message)

/**
 * Reads the card number, expiry and (best-effort) cardholder name from a
 * contactless EMV card using the standard PPSE / PSE -> SELECT -> GPO -> READ RECORD flow.
 */
class EmvCardReader {

    suspend fun read(tag: Tag): CardData = withContext(Dispatchers.IO) {
        try {
            val isoDep = IsoDep.get(tag)
                ?: throw CardReadException("Not a contactless payment card")
            isoDep.timeout = 2000
            isoDep.connect()
            try {
                readEmv(isoDep)
            } finally {
                runCatching { isoDep.close() }
            }
        } catch (e: CardReadException) {
            throw e
        } catch (e: Exception) {
            throw CardReadException("Could not read the card")
        }
    }

    private fun readEmv(isoDep: IsoDep): CardData {
        val ppseFci = try {
            select(isoDep, SELECT_PPSE)
        } catch (e: CardReadException) {
            // Fall back to PSE (1PAY.SYS.DDF01) on cards that don't support PPSE.
            val pseFci = select(isoDep, SELECT_PSE)
            val sfi = TlvParser.findFirst(pseFci, 0x88)?.firstOrNull()?.let {
                (it.toInt() and 0xF8) shr 3
            } ?: 1
            val aids = mutableListOf<ByteArray>()
            for (record in 1..10) {
                val content = try {
                    readRecord(isoDep, sfi, record)
                } catch (e: Exception) {
                    break
                }
                TlvParser.findAll(content, 0x4F).forEach { aids.add(it) }
                if (aids.isNotEmpty()) break
            }
            if (aids.isEmpty()) throw CardReadException("No payment application found")
            return readFirstSuccessfulAid(isoDep, aids)
        }

        val aids = TlvParser.findAll(ppseFci, 0x4F)
        if (aids.isEmpty()) throw CardReadException("No payment application found")
        return readFirstSuccessfulAid(isoDep, aids)
    }

    private fun readFirstSuccessfulAid(isoDep: IsoDep, aids: List<ByteArray>): CardData {
        for (aid in aids) {
            readWithAid(isoDep, aid)?.let { return it }
        }
        throw CardReadException("Could not read card data")
    }

    private fun readWithAid(isoDep: IsoDep, aid: ByteArray): CardData? {
        val selectResponse = try {
            select(isoDep, buildSelect(aid))
        } catch (e: CardReadException) {
            return null
        }

        val collected = mutableMapOf<Int, ByteArray>()

        val pdol = TlvParser.findFirst(selectResponse, 0x9F38)
        val gpoResponse = try {
            if (pdol == null || pdol.isEmpty()) {
                isoTransceive(isoDep, GPO_EMPTY)
            } else {
                isoTransceive(isoDep, buildGpo(buildPdolValue(pdol)))
            }
        } catch (e: IOException) {
            null
        }

        if (gpoResponse != null && isSuccess(gpoResponse)) {
            TlvParser.findFirst(gpoResponse, 0x94)?.let { afl ->
                for ((sfi, record) in parseAfl(afl)) {
                    readRecordInto(isoDep, sfi, record, collected)
                }
            }
        }

        if (collected[0x5A] == null && collected[0x57] == null) {
            for (record in 1..3) {
                readRecordInto(isoDep, 1, record, collected)
            }
        }

        return buildCard(collected)
    }

    private fun buildCard(collected: Map<Int, ByteArray>): CardData? {
        val pan = collected[0x5A]?.let { parsePan(it) }
            ?: collected[0x57]?.let { parseTrack2(it)?.first }
        if (pan == null || pan.length !in 12..19) return null

        val expiry = collected[0x5F24]?.let { parseYymm(it) }
            ?: collected[0x57]?.let { parseTrack2(it)?.second }
        val name = collected[0x5F20]?.let { cleanName(it) }

        return CardData(
            number = pan,
            expiryMonth = expiry?.second,
            expiryYear = expiry?.first,
            holderName = name
        )
    }

    private fun readRecordInto(
        isoDep: IsoDep,
        sfi: Int,
        record: Int,
        collected: MutableMap<Int, ByteArray>
    ) {
        val content = try {
            readRecord(isoDep, sfi, record)
        } catch (e: Exception) {
            return
        }
        for (tag in intArrayOf(0x5A, 0x5F24, 0x5F20, 0x57)) {
            TlvParser.findFirst(content, tag)?.let { value ->
                collected.putIfAbsent(tag, value)
            }
        }
    }

    private fun select(isoDep: IsoDep, apdu: ByteArray): ByteArray {
        val response = isoTransceive(isoDep, apdu)
        if (!isSuccess(response)) throw CardReadException("Card selection failed")
        return response.copyOf(response.size - 2)
    }

    private fun readRecord(isoDep: IsoDep, sfi: Int, record: Int): ByteArray {
        val apdu = byteArrayOf(
            0x00, 0xB2.toByte(), record.toByte(), ((sfi shl 3) or 0x04).toByte(), 0x00
        )
        val response = isoTransceive(isoDep, apdu)
        if (!isSuccess(response)) throw CardReadException("Read record failed")
        return response.copyOf(response.size - 2)
    }

    private fun isoTransceive(isoDep: IsoDep, apdu: ByteArray): ByteArray {
        var response = isoDep.transceive(apdu)
        var attempt = 0
        while (response.size >= 2 && attempt < 16) {
            val sw1 = response[response.size - 2].toInt() and 0xFF
            val sw2 = response[response.size - 1].toInt() and 0xFF
            when {
                sw1 == 0x61 -> {
                    val getResponse = byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, sw2.toByte())
                    val extra = isoDep.transceive(getResponse)
                    val body = response.copyOf(response.size - 2)
                    response = body + extra
                }

                sw1 == 0x6C && apdu.isNotEmpty() -> {
                    val corrected = apdu.copyOf(apdu.size - 1) + sw2.toByte()
                    response = isoDep.transceive(corrected)
                }

                else -> break
            }
            attempt++
        }
        return response
    }

    private fun parseAfl(afl: ByteArray): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i + 3 < afl.size) {
            val sfi = (afl[i].toInt() and 0xF8) shr 3
            val first = afl[i + 1].toInt() and 0x1F
            val last = afl[i + 2].toInt() and 0x1F
            i += 4
            if (sfi in 1..30 && last in 1..16 && last >= first) {
                for (record in first.coerceAtLeast(1)..last) {
                    result.add(sfi to record)
                }
            }
        }
        return result
    }

    private fun parsePan(bcd: ByteArray): String {
        val sb = StringBuilder()
        for (byte in bcd) {
            val hi = (byte.toInt() shr 4) and 0x0F
            val lo = byte.toInt() and 0x0F
            if (hi != 0x0F) sb.append(hi)
            if (lo != 0x0F) sb.append(lo)
        }
        return sb.toString()
    }

    private fun parseYymm(bcd: ByteArray): Pair<Int, Int>? {
        if (bcd.size < 2) return null
        val yy = ((bcd[0].toInt() shr 4) and 0x0F) * 10 + (bcd[0].toInt() and 0x0F)
        val mm = ((bcd[1].toInt() shr 4) and 0x0F) * 10 + (bcd[1].toInt() and 0x0F)
        if (mm !in 1..12) return null
        return yy to mm
    }

    private fun parseTrack2(raw: ByteArray): Pair<String, Pair<Int, Int>>? {
        val text = StringBuilder()
        for (byte in raw) {
            val c = (byte.toInt() and 0xFF).toChar()
            if (c.isDigit() || c == '=' || c == 'D') text.append(c)
        }
        val s = text.toString()
        val sep = s.indexOfFirst { it == '=' || it == 'D' }
        if (sep < 0) return null
        val pan = s.substring(0, sep)
        if (pan.length !in 12..19) return null
        val rest = s.substring(sep + 1)
        if (rest.length < 4) return null
        val yy = rest.substring(0, 2).toIntOrNull() ?: return null
        val mm = rest.substring(2, 4).toIntOrNull() ?: return null
        if (mm !in 1..12) return null
        return pan to (yy to mm)
    }

    private fun cleanName(raw: ByteArray): String? {
        var end = raw.size
        while (end > 0 && (raw[end - 1] == 0xFF.toByte() || raw[end - 1] == 0x20.toByte())) end--
        if (end == 0) return null
        val sb = StringBuilder()
        for (i in 0 until end) {
            val b = raw[i].toInt() and 0xFF
            if (b == 0x1F || b == 0xFF || b == 0x00) continue
            sb.append(b.toChar())
        }
        val name = sb.toString().trim().replace('^', ' ')
        return name.ifBlank { null }
    }

    private fun buildSelect(aid: ByteArray): ByteArray {
        val cmd = ByteArrayOutputStream()
        cmd.write(0x00)
        cmd.write(0xA4)
        cmd.write(0x04)
        cmd.write(0x00)
        cmd.write(aid.size)
        cmd.write(aid)
        cmd.write(0x00)
        return cmd.toByteArray()
    }

    private fun buildGpo(pdolValue: ByteArray): ByteArray {
        val cmd = ByteArrayOutputStream()
        cmd.write(0x80)
        cmd.write(0xA8)
        cmd.write(0x00)
        cmd.write(0x00)
        cmd.write(2 + pdolValue.size)
        cmd.write(0x83)
        cmd.write(pdolValue.size)
        cmd.write(pdolValue)
        cmd.write(0x00)
        return cmd.toByteArray()
    }

    private fun buildPdolValue(pdol: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < pdol.size) {
            val tagBytes = if ((pdol[i].toInt() and 0x1F) == 0x1F) 2 else 1
            if (i + tagBytes >= pdol.size) break
            val len = pdol[i + tagBytes].toInt() and 0xFF
            repeat(len) { out.write(0) }
            i += tagBytes + 1
        }
        return out.toByteArray()
    }

    private fun isSuccess(response: ByteArray): Boolean =
        response.size >= 2 &&
            response[response.size - 2] == 0x90.toByte() &&
            response[response.size - 1] == 0x00.toByte()

    companion object {
        private val SELECT_PPSE: ByteArray = buildSelect("2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII))
        private val SELECT_PSE: ByteArray = buildSelect("1PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII))
        private val GPO_EMPTY = byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, 0x02, 0x83.toByte(), 0x00, 0x00)

        private fun buildSelect(selectFile: ByteArray): ByteArray {
            val cmd = ByteArrayOutputStream()
            cmd.write(0x00)
            cmd.write(0xA4)
            cmd.write(0x04)
            cmd.write(0x00)
            cmd.write(selectFile.size)
            cmd.write(selectFile)
            cmd.write(0x00)
            return cmd.toByteArray()
        }
    }
}
