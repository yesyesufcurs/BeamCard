package com.yesyesufcurs.creditcardnumberautofill

import com.yesyesufcurs.creditcardnumberautofill.nfc.CardData

/**
 * In-memory holder for the most recently read card. Never persisted.
 * Cleared automatically after the clipboard timeout or on "Done".
 */
object CardCache {
    @Volatile
    var card: CardData? = null
}
