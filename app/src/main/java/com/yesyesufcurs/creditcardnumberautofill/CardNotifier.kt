package com.yesyesufcurs.creditcardnumberautofill

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object CardNotifier {

    const val CHANNEL_ID = "card_read"
    const val NOTIFICATION_ID = 1001

    const val ACTION_COPY_NUMBER = "com.yesyesufcurs.creditcardnumberautofill.action.COPY_NUMBER"
    const val ACTION_COPY_EXPIRY = "com.yesyesufcurs.creditcardnumberautofill.action.COPY_EXPIRY"
    const val ACTION_CLEAR = "com.yesyesufcurs.creditcardnumberautofill.action.CLEAR"

    private const val CLEAR_REQUEST_CODE = 0xCC01
    private const val CLEAR_DELAY_MS = 90_000L

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.app_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_title)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context) {
        createChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, NfcReadActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_tile)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(context.getString(R.string.notif_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_text)))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.copy_number), copyAction(context, ACTION_COPY_NUMBER))
            .addAction(0, context.getString(R.string.copy_expiry), copyAction(context, ACTION_COPY_EXPIRY))
            .addAction(0, context.getString(R.string.action_clear), copyAction(context, ACTION_CLEAR))

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, builder.build())

        scheduleClear(context)
    }

    fun update(context: Context, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_tile)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, NfcReadActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.copy_number), copyAction(context, ACTION_COPY_NUMBER))
            .addAction(0, context.getString(R.string.copy_expiry), copyAction(context, ACTION_COPY_EXPIRY))
            .addAction(0, context.getString(R.string.action_clear), copyAction(context, ACTION_CLEAR))
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        cancelClear(context)
    }

    private fun copyAction(context: Context, action: String): PendingIntent {
        val intent = Intent(context, ClipboardActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleClear(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            CLEAR_REQUEST_CODE,
            Intent(context, ClipboardClearReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + CLEAR_DELAY_MS, pending)
    }

    private fun cancelClear(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            CLEAR_REQUEST_CODE,
            Intent(context, ClipboardClearReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pending?.let { alarmManager.cancel(it) }
    }
}
