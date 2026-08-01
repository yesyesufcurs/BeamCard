package com.yesyesufcurs.creditcardnumberautofill.nfc

enum class CardNetwork(val displayName: String) {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    AMEX("Amex"),
    DISCOVER("Discover"),
    JCB("JCB"),
    MAESTRO("Maestro"),
    UNKNOWN("Card");

    companion object {
        fun detect(number: String): CardNetwork {
            val n = number.trim()
            if (n.length < 2) return UNKNOWN
            val head2 = if (n.length >= 2) n.substring(0, 2).toIntOrNull() else null
            val head3 = if (n.length >= 3) n.substring(0, 3).toIntOrNull() else null
            val head4 = if (n.length >= 4) n.substring(0, 4).toIntOrNull() else null
            val head6 = if (n.length >= 6) n.substring(0, 6).toIntOrNull() else null
            return when {
                n.startsWith("4") -> VISA
                n.startsWith("34") || n.startsWith("37") -> AMEX
                head2 in 51..55 || head4 in 2221..2720 -> MASTERCARD
                n.startsWith("6011") || n.startsWith("65") -> DISCOVER
                head3 in 644..649 -> DISCOVER
                head6 in 622126..622925 -> DISCOVER
                head4 in 3528..3589 -> JCB
                n.startsWith("50") || head2 in 56..69 -> MAESTRO
                else -> UNKNOWN
            }
        }
    }
}

data class CardData(
    val number: String,
    val expiryMonth: Int?,
    val expiryYear: Int?,
    val holderName: String?
) {
    val network: CardNetwork get() = CardNetwork.detect(number)

    val lastFour: String get() = number.takeLast(4)

    val maskedNumber: String get() = "\u2022\u2022\u2022\u2022 $lastFour"

    val expiryText: String?
        get() {
            val m = expiryMonth ?: return null
            val y = expiryYear ?: return null
            return "%02d/%02d".format(m, y)
        }
}
