package com.yesyesufcurs.creditcardnumberautofill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClipboardActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val card = CardCache.card ?: return
        when (intent.action) {
            CardNotifier.ACTION_COPY_NUMBER -> {
                Clipboard.copy(context, "cardNumber", card.number)
                CardNotifier.update(context, context.getString(R.string.notif_step_number))
            }

            CardNotifier.ACTION_COPY_EXPIRY -> {
                Clipboard.copy(context, "cardExpiry", card.expiryText.orEmpty())
                CardNotifier.update(context, context.getString(R.string.notif_step_expiry))
            }

            CardNotifier.ACTION_CLEAR -> {
                Clipboard.clear(context)
                CardNotifier.dismiss(context)
                CardCache.card = null
            }
        }
    }
}
