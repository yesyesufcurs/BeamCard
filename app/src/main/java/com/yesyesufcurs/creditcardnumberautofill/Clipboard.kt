package com.yesyesufcurs.creditcardnumberautofill

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object Clipboard {

    fun copy(context: Context, label: String, text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun clear(context: Context) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.clearPrimaryClip()
    }
}
