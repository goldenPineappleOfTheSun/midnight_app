
package com.example.midnightlight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class NightFilterService : Service() {

    companion object {
        // Команды, которые MainActivity отправляет сервису.
        const val ACTION_START = "nightlight.START"
        const val ACTION_STOP = "nightlight.STOP"

        // Команда изменить параметры уже работающего фильтра.
        const val ACTION_UPDATE = "nightlight.UPDATE"

        // Имена передаваемых параметров.
        const val EXTRA_WARMTH = "warmth"
        const val EXTRA_DIMMING = "dimming"

        private const val CHANNEL_ID = "night_filter"
        private const val NOTIFICATION_ID = 1

        // Stores whether our night filter is running.
        var isRunning by mutableStateOf(false)
            private set
    }

    // WindowManager управляет окнами Android.
    private lateinit var windowManager: WindowManager

    // Наш полупрозрачный слой.
    private var filterView: View? = null

    // Параметры окна: размер, тип, прозрачность.
    private var windowParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(
            WINDOW_SERVICE
        ) as WindowManager

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        // Получаем команду от Activity.
        when (intent?.action) {


            ACTION_START -> {

                // Считываем параметры.
                val warmth = intent.getFloatExtra(
                    EXTRA_WARMTH, 0.65f
                )
                val dimming = intent.getFloatExtra(
                    EXTRA_DIMMING, 0.35f
                )

                // Если разрешения нет, сервис не запускаем.
                if (!Settings.canDrawOverlays(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                try {
                    // Android может отказать в переводе сервиса
                    // в foreground, если не соблюдены требования.
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification()
                    )

                    // WindowManager тоже может выбросить исключение.
                    showFilter(warmth, dimming)

                    // Только после успешного создания окна
                    // считаем фильтр включённым.
                    isRunning = true

                } catch (e: RuntimeException) {

                    // Записываем причину в Logcat.
                    Log.e(
                        "NightFilter",
                        "Не удалось включить фильтр",
                        e
                    )

                    // Убираем сервис. onDestroy удалит окно,
                    // если оно успело создаться.
                    isRunning = false
                    stopSelf()

                    return START_NOT_STICKY
                }
            }




            ACTION_UPDATE -> {

                // Сначала проверяем разрешение.
                // Это важно, даже если слой уже существует.
                if (!Settings.canDrawOverlays(this)) {

                    Log.w(
                        "NightFilter",
                        "Overlay permission was revoked"
                    )

                    // Завершаем сервис. onDestroy()
                    // очистит окно и сбросит isRunning.
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Обновлять можно только существующий фильтр.
                if (filterView != null) {

                    val warmth = intent.getFloatExtra(
                        EXTRA_WARMTH,
                        0.65f
                    )

                    val dimming = intent.getFloatExtra(
                        EXTRA_DIMMING,
                        0.35f
                    )

                    try {
                        // Даже после проверки разрешения
                        // WindowManager может отказать в обновлении.
                        showFilter(warmth, dimming)

                    } catch (e: RuntimeException) {

                        Log.e(
                            "NightFilter",
                            "Failed to update overlay",
                            e
                        )

                        // Если окно не удалось обновить,
                        // не оставляем фильтр в неизвестном состоянии.
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
            }

            ACTION_STOP -> {
                // stopSelf завершает работу сервиса.
                // Android затем вызовет onDestroy().
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // Не просим Android автоматически перезапускать
        // сервис после принудительного завершения процесса.
        return START_NOT_STICKY
    }


    private fun showFilter(
        warmthInput: Float,
        dimmingInput: Float
    ) {
        // Проверяем разрешение непосредственно перед
        // созданием или обновлением окна.
        //
        // Даже если при запуске разрешение было,
        // пользователь мог позже его отозвать.
        if (!Settings.canDrawOverlays(this)) {
            throw SecurityException(
                "Overlay permission has been revoked"
            )
        }

        // Нормализуем входные данные.
        // coerceIn ограничивает число указанным диапазоном.
        val warmth = warmthInput.coerceIn(0f, 1f)
        val dimming = dimmingInput.coerceIn(0f, 1f)

        // Представим, что у нас есть два слоя:
        //
        // 1. Оранжевый, отвечающий за теплоту.
        // 2. Чёрный, отвечающий за затемнение.
        //
        // Но на самом деле создадим ОДНО окно.
        // Это важно для ограничения прозрачности Android.

        // Максимальная сила тёплого слоя — 28%.
        val warmAlpha = warmth * 0.28f

        // Максимальная сила чёрного слоя — 60%.
        val darkAlpha = dimming * 0.60f

        // Формула объединения прозрачностей:
        //
        // total = 1 - (1 - warm) * (1 - dark)
        //
        // Это непрозрачность, эквивалентная двум
        // последовательно наложенным слоям.
        val totalAlpha = 1f -
                (1f - warmAlpha) * (1f - darkAlpha)

        // Вычисляем вклад тёплого цвета.
        //
        // Чёрный слой лежит поверх тёплого,
        // поэтому он частично скрывает оранжевый.
        val warmContribution = if (totalAlpha > 0f) {
            (warmAlpha * (1f - darkAlpha)) / totalAlpha
        } else {
            0f
        }

        // Базовый цвет тёплого слоя.
        // Было 255/145/65 (рыжий), сдвинуто ближе к красному.
        val warmRed = 255
        val warmGreen = 120
        val warmBlue = 50

        // Получаем итоговый цвет одного слоя.
        val red = (warmRed * warmContribution).toInt()
        val green = (warmGreen * warmContribution).toInt()
        val blue = (warmBlue * warmContribution).toInt()

        val filterColor = Color.rgb(red, green, blue)

        // При максимальных параметрах:
        //
        // totalAlpha = 1 - 0.72 * 0.40 = 0.712
        //
        // То есть 71.2% непрозрачности.
        // Это ниже стандартного порога Android 0.8.
        val opacity = totalAlpha

        // Если слой уже существует, обновляем его.
        if (filterView != null) {

            filterView?.setBackgroundColor(filterColor)

            windowParams?.let { params ->
                params.alpha = opacity

                filterView?.let { existingView ->
                    windowManager.updateViewLayout(
                        existingView,
                        params
                    )
                }
            }

            return
        }

        // Создаём слой, если его ещё нет.
        val view = View(this)
        view.setBackgroundColor(filterColor)


        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

            // Фильтр не перехватывает нажатия.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or

                    // Размещаем окно относительно всего экрана.
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or

                    // Разрешаем окну выходить за стандартные
                    // границы области, предоставленной Android.
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,

            PixelFormat.TRANSLUCENT
        )

        // Начиная с Android 9 можно управлять тем,
        // заходит ли окно в область выреза под камеру.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

            params.layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                    // Android 11+: разрешаем использовать
                    // области вырезов на всех краях дисплея.
                    WindowManager.LayoutParams
                        .LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

                } else {

                    // Android 9–10: разрешаем вырезы
                    // на коротких сторонах экрана.
                    WindowManager.LayoutParams
                        .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
        }

        params.alpha = opacity

        // Сначала пытаемся добавить окно.
        windowManager.addView(view, params)

        // Только если addView завершился успешно,
        // сохраняем ссылку на созданное окно.
        filterView = view
        windowParams = params
    }


    private fun createNotificationChannel() {

        // Каналы уведомлений появились в Android 8.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(
                NotificationManager::class.java
            )

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {

        // Нажатие на уведомление открывает приложение.
        val openIntent = Intent(this, MainActivity::class.java)

        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        // Команда выключения фильтра из уведомления.
        val stopIntent = Intent(
            this,
            NightFilterService::class.java
        ).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_turn_off),
                stopPendingIntent
            )
            .build()
    }

    override fun onDestroy() {

        // Сначала сообщаем интерфейсу,
        // что фильтр больше не работает.
        isRunning = false

        // Забираем ссылку на существующее окно.
        val view = filterView

        // Сбрасываем ссылки, чтобы сервис
        // больше не считал окно активным.
        filterView = null
        windowParams = null

        if (view != null) {

            try {
                // Пробуем удалить окно.
                windowManager.removeView(view)

            } catch (e: RuntimeException) {

                // Окно могло уже исчезнуть,
                // например после отзыва разрешения.
                Log.w(
                    "NightFilter",
                    "Could not remove overlay window",
                    e
                )
            }
        }

        // Завершаем жизненный цикл сервиса.
        super.onDestroy()
    }


    // Мы не используем привязку через bindService().
    // Поэтому возвращаем null.
    override fun onBind(intent: Intent?): IBinder? = null
}
