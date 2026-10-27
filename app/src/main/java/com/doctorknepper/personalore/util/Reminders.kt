package com.doctorknepper.personalore.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.doctorknepper.personalore.MainActivity
import com.doctorknepper.personalore.R
import com.doctorknepper.personalore.data.Store
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Reminders {
    private const val CHANNEL = "daily_reminder"

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context)
        am.cancel(pi)
        Store.init(context)
        if (!Store.reminderEnabled) return
        var next = LocalDateTime.now().withHour(Store.reminderHour).withMinute(Store.reminderMinute).withSecond(0).withNano(0)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact so no special alarm permission is needed; Android may shift it by a few minutes.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 1, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun show(context: Context) {
        Store.init(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily journal reminder", NotificationManager.IMPORTANCE_DEFAULT))
        if (Store.wroteToday) return // no nagging once you've written
        // With the lock on, the journal stays encrypted, so the notification can't show your prompt.
        Store.ensureDailyPrompt()
        val prompt = if (Store.unlocked && !Store.lockEnabled) Store.dailyPrompt?.text else null
        val open = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time to write")
            .setContentText(prompt ?: "Take a few minutes for your journal.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(prompt ?: "Take a few minutes for your journal."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(7, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.show(context)
        Reminders.schedule(context) // set up tomorrow's
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.schedule(context)
    }
}
