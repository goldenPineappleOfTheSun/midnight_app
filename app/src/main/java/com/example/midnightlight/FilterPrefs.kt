package com.example.midnightlight

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

// ============================================================
// ПОСТОЯННОЕ ХРАНЕНИЕ НАСТРОЕК
// ============================================================
//
// SharedPreferences — маленькое хранилище "ключ → значение",
// которое сохраняется на диске. Оно переживает закрытие
// приложения и перезагрузку телефона.
//
// Здесь хранятся:
// - параметры фильтра (теплота и затемнение), чтобы таймер
//   мог включить фильтр, когда приложение закрыто;
// - настройки таймера.
//
// Время хранится в минутах от полуночи:
// 22:00 → 22 * 60 = 1320.

object FilterPrefs {

    private const val FILE = "midnight_light"

    private const val KEY_WARMTH = "warmth"
    private const val KEY_DIMMING = "dimming"
    private const val KEY_TIMER_ENABLED = "timer_enabled"
    private const val KEY_TIMER_START = "timer_start"
    private const val KEY_TIMER_END = "timer_end"

    const val DEFAULT_WARMTH = 0.65f
    const val DEFAULT_DIMMING = 0.35f
    const val DEFAULT_START = 22 * 60
    const val DEFAULT_END = 7 * 60

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun warmth(context: Context) =
        prefs(context).getFloat(KEY_WARMTH, DEFAULT_WARMTH)

    fun dimming(context: Context) =
        prefs(context).getFloat(KEY_DIMMING, DEFAULT_DIMMING)

    fun saveFilter(context: Context, warmth: Float, dimming: Float) {
        prefs(context).edit()
            .putFloat(KEY_WARMTH, warmth)
            .putFloat(KEY_DIMMING, dimming)
            .apply()
    }

    fun timerEnabled(context: Context) =
        prefs(context).getBoolean(KEY_TIMER_ENABLED, false)

    fun timerStart(context: Context) =
        prefs(context).getInt(KEY_TIMER_START, DEFAULT_START)

    fun timerEnd(context: Context) =
        prefs(context).getInt(KEY_TIMER_END, DEFAULT_END)

    fun saveTimer(context: Context, enabled: Boolean, start: Int, end: Int) {
        prefs(context).edit()
            .putBoolean(KEY_TIMER_ENABLED, enabled)
            .putInt(KEY_TIMER_START, start)
            .putInt(KEY_TIMER_END, end)
            .apply()
    }
}


// Счётчик возвращений на экран (onResume).
//
// Когда пользователь возвращается из системных настроек,
// счётчик увеличивается, и экран таймера заново проверяет,
// выданы ли разрешения.

object AppLifecycle {
    var resumeCount by mutableIntStateOf(0)
}
