package com.example.midnightlight

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Calendar

// ============================================================
// ВКЛЮЧЕНИЕ И ВЫКЛЮЧЕНИЕ ФИЛЬТРА
// ============================================================
//
// Общие функции, которыми пользуются и экран, и таймер.

object FilterControl {

    // Включает фильтр с сохранёнными параметрами.
    // Возвращает false, если включить не удалось.
    fun start(context: Context): Boolean {

        // Без разрешения рисовать поверх приложений
        // фильтр показать невозможно.
        if (!Settings.canDrawOverlays(context)) return false

        val intent = Intent(context, NightFilterService::class.java).apply {
            action = NightFilterService.ACTION_START
            putExtra(NightFilterService.EXTRA_WARMTH, FilterPrefs.warmth(context))
            putExtra(NightFilterService.EXTRA_DIMMING, FilterPrefs.dimming(context))
        }

        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: RuntimeException) {
            // Android может запретить запуск из фона.
            Log.e("NightFilter", "Таймер не смог включить фильтр", e)
            false
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, NightFilterService::class.java))
    }
}


// ============================================================
// ПЛАНИРОВЩИК ТАЙМЕРА
// ============================================================
//
// AlarmManager — системный будильник Android. Он срабатывает
// в заданное время, даже если приложение закрыто.
//
// Мы ставим два будильника: на старт и на финиш.
// Каждый раз, когда будильник срабатывает, ставим его
// снова — на следующие сутки.

object TimerScheduler {

    const val ACTION_TIMER_START = "com.example.midnightlight.TIMER_START"
    const val ACTION_TIMER_END = "com.example.midnightlight.TIMER_END"

    // Точные будильники на Android 12+ требуют разрешения
    // "Будильники и напоминания". Только точный будильник
    // даёт право включить фильтр, когда приложение закрыто.
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return alarmManager.canScheduleExactAlarms()
    }

    // Находится ли время now внутри ночного промежутка.
    // Учитывает промежуток через полночь (22:00–07:00).
    fun isInsideWindow(now: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> now in start until end
        else -> now >= start || now < end
    }

    private fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    // Переставляет оба будильника по сохранённым настройкам.
    // Если таймер выключен — просто отменяет их.
    fun reschedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        val startIntent = pendingIntent(context, ACTION_TIMER_START)
        val endIntent = pendingIntent(context, ACTION_TIMER_END)

        alarmManager.cancel(startIntent)
        alarmManager.cancel(endIntent)

        val start = FilterPrefs.timerStart(context)
        val end = FilterPrefs.timerEnd(context)

        if (!FilterPrefs.timerEnabled(context) || start == end) return

        setAlarm(context, alarmManager, nextOccurrence(start), startIntent)
        setAlarm(context, alarmManager, nextOccurrence(end), endIntent)
    }

    // Если таймер включён и сейчас ночной промежуток —
    // включает фильтр немедленно. Нужно после перезагрузки
    // и при включении таймера посреди ночи.
    fun applyNow(context: Context) {
        if (!FilterPrefs.timerEnabled(context)) return

        val inside = isInsideWindow(
            nowMinutes(),
            FilterPrefs.timerStart(context),
            FilterPrefs.timerEnd(context)
        )

        if (inside && !NightFilterService.isRunning) {
            FilterControl.start(context)
        }
    }

    // Ближайший момент времени minutes (сегодня или завтра).
    private fun nextOccurrence(minutes: Int): Long {
        val now = Calendar.getInstance()
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!at.after(now)) at.add(Calendar.DAY_OF_YEAR, 1)
        return at.timeInMillis
    }

    private fun setAlarm(
        context: Context,
        alarmManager: AlarmManager,
        at: Long,
        intent: PendingIntent
    ) {
        if (canScheduleExact(context)) {
            // Срабатывает точно в срок, даже в режиме сна телефона.
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, at, intent
            )
        } else {
            // Запасной вариант без разрешения: может опоздать,
            // и на Android 12+ фильтр, скорее всего, не включится.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, at, intent
            )
        }
    }

    private fun pendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            if (action == ACTION_TIMER_START) 10 else 11,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}


// ============================================================
// ПРИЁМНИКИ СОБЫТИЙ
// ============================================================

// Срабатывание наших будильников (старт / финиш).
class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("NightFilter", "Будильник таймера: ${intent.action}")

        if (FilterPrefs.timerEnabled(context)) {
            when (intent.action) {
                TimerScheduler.ACTION_TIMER_START -> {
                    val ok = FilterControl.start(context)
                    Log.i("NightFilter", "Таймер включает фильтр: $ok")
                }
                TimerScheduler.ACTION_TIMER_END -> FilterControl.stop(context)
            }
        }

        // Ставим будильник на следующие сутки.
        TimerScheduler.reschedule(context)
    }
}

// Системные события: перезагрузка, смена времени или часового
// пояса, обновление приложения, выдача разрешения на будильники.
//
// AlarmManager забывает будильники после перезагрузки,
// поэтому ставим их заново и, если сейчас ночь, включаем фильтр.
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("NightFilter", "Системное событие: ${intent.action}")
        TimerScheduler.reschedule(context)
        TimerScheduler.applyNow(context)
    }
}
