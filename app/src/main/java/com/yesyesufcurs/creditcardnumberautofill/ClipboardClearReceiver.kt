package com.yesyesufcurs.creditcardnumberautofill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClipboardClearReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Clipboard.clear(context)
        CardNotifier.dismiss(context)
        CardCache.card = null
    }
}
