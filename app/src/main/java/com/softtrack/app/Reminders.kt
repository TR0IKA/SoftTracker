package com.softtrack.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Напоминания: по одному будильнику на каждую пару «привычка + день недели».
 * Следующее срабатывание считается в текущем часовом поясе, поэтому смена пояса и перевод часов
 * учитываются при каждом пересчёте (запуск, загрузка, смена времени/пояса, срабатывание).
 */
object Reminders {
    const val CHANNEL = "habit_reminders"
    const val ACTION_REMIND = "com.softtrack.app.REMIND"
    const val ACTION_DONE = "com.softtrack.app.DONE"
    private const val PREFS = "settings"

    fun enabled(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("notify", true)

    fun canNotify(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Напоминания о привычках", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Напоминания в выбранные дни и время"
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun code(habitId: Long, dow: Int): Int = (habitId * 8 + dow).toInt()

    private fun alarmIntent(ctx: Context, habitId: Long, dow: Int): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_REMIND)
            .putExtra("habitId", habitId).putExtra("dow", dow)
        return PendingIntent.getBroadcast(ctx, code(habitId, dow), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun nextTrigger(dow: Int, minutes: Int, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        var t = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dow)))
            .withHour(minutes / 60).withMinute(minutes % 60).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusWeeks(1)
        return t
    }

    /** Полностью пересобирает будильники по текущему расписанию. */
    fun rescheduleAll(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wanted = LinkedHashMap<Int, Pair<Long, Int>>() // code -> (habitId, dow)
        val times = HashMap<Int, Int>()
        if (enabled(app)) {
            val snap = HabitDb.get(app).loadSnapshot()
            for (h in snap.habits) {
                if (h.archived || !h.isGood || !h.remindersOn) continue
                for (r in snap.rulesOf(h.id)) {
                    if (r.to != null || r.minutes < 0) continue
                    val c = code(h.id, r.dow)
                    wanted[c] = h.id to r.dow
                    times[c] = r.minutes
                }
            }
        }
        val old = prefs.getStringSet("alarm_codes", emptySet()) ?: emptySet()
        old.forEach { s ->
            val c = s.toIntOrNull() ?: return@forEach
            if (c !in wanted) {
                val i = Intent(app, ReminderReceiver::class.java).setAction(ACTION_REMIND)
                val pi = PendingIntent.getBroadcast(app, c, i, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                if (pi != null) { am.cancel(pi); pi.cancel() }
            }
        }
        val exact = canExact(app)
        for ((c, hd) in wanted) {
            val at = nextTrigger(hd.second, times[c] ?: 0).toInstant().toEpochMilli()
            val pi = alarmIntent(app, hd.first, hd.second)
            try {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
        prefs.edit().putStringSet("alarm_codes", wanted.keys.map { it.toString() }.toSet()).apply()
    }

    private fun openAppIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun post(ctx: Context, id: Int, title: String, text: String, habitId: Long?) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_star)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(ctx))
        if (habitId != null) {
            val done = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_DONE).putExtra("habitId", habitId)
            b.addAction(0, "Выполнено", PendingIntent.getBroadcast(ctx, 100000 + habitId.toInt(), done,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        try { NotificationManagerCompat.from(ctx).notify(id, b.build()) } catch (e: SecurityException) { }
    }

    fun showReminder(ctx: Context, habitId: Long, dow: Int) {
        if (!enabled(ctx)) return
        val snap = HabitDb.get(ctx).loadSnapshot()
        val h = snap.habits.firstOrNull { it.id == habitId } ?: return
        val today = LocalDate.now()
        if (h.archived || !h.remindersOn || !snap.isScheduled(h, today) || today.dayOfWeek.value != dow) return
        if (snap.isDone(h, today)) return
        val m = snap.reminderMinutes(h, today)
        post(ctx, habitId.toInt(), h.title, if (m != null) "Запланировано на сегодня, ${fmtTime(m)}" else "Запланировано на сегодня", habitId)
    }

    fun showTest(ctx: Context) = post(ctx, 1, "SoftTrack", "Уведомления работают", null)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("habitId", -1L)
        when (intent.action) {
            Reminders.ACTION_REMIND -> {
                if (id >= 0) Reminders.showReminder(ctx, id, intent.getIntExtra("dow", 0))
                Reminders.rescheduleAll(ctx)
            }
            Reminders.ACTION_DONE -> {
                if (id >= 0) HabitDb.get(ctx).setStatus(id, LocalDate.now().toString(), 1)
                NotificationManagerCompat.from(ctx).cancel(id.toInt())
            }
        }
    }
}

/** Загрузка устройства, обновление приложения, смена времени или часового пояса. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminders.rescheduleAll(ctx)
    }
}
