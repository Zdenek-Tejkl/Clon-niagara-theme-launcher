package cz.tejkl.klid

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat

/** Připomínka přestávky po N minutách v aplikaci spuštěné z plochy. */
object BreakReminder {
    private const val CHANNEL = "breaks"
    private const val EXTRA_LABEL = "label"
    private const val EXTRA_MINUTES = "minutes"

    fun schedule(context: Context, appLabel: String, minutes: Int) {
        val intent = Intent(context, BreakReceiver::class.java)
            .putExtra(EXTRA_LABEL, appLabel)
            .putExtra(EXTRA_MINUTES, minutes)
        context.getSystemService(AlarmManager::class.java).set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + minutes * 60_000L,
            pending(context, intent),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(pending(context, Intent(context, BreakReceiver::class.java)))
    }

    private fun pending(context: Context, intent: Intent) = PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun notify(context: Context, intent: Intent) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.break_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        manager.notify(
            1,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.break_title))
                .setContentText(context.getString(R.string.break_text, label, minutes))
                .setAutoCancel(true)
                .build(),
        )
    }
}

class BreakReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = BreakReminder.notify(context, intent)
}
