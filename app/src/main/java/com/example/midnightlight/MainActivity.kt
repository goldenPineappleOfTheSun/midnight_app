
package com.example.midnightlight

// Bundle хранит данные, которые Android может передать Activity
// при её создании или восстановлении.
import android.os.Bundle

// ComponentActivity — базовый класс нашего экрана.
// setContent позволяет использовать Jetpack Compose вместо XML.
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

// Canvas нужен для рисования собственного элемента интерфейса.
import androidx.compose.foundation.Canvas

// background задаёт цвет фона.
import androidx.compose.foundation.background

// Инструменты для обработки прикосновений к экрану.
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

// Column, Row, Spacer и другие элементы размещения интерфейса.
import androidx.compose.foundation.layout.*

// Форма с закруглёнными углами.
import androidx.compose.foundation.shape.RoundedCornerShape

// Готовые компоненты Material Design: кнопки, текст и тема.
import androidx.compose.material3.*

// Состояние интерфейса и инструменты его хранения.
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

// Позиция точки на плоскости: x и y.
import androidx.compose.ui.geometry.Offset

// Инструменты рисования: цвета, градиенты и обводка.
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

// Обработка движений пальца.
import androidx.compose.ui.input.pointer.pointerInput

// Выравнивание элементов.
import androidx.compose.ui.Alignment

// Modifier — способ изменять внешний вид и поведение элементов.
import androidx.compose.ui.Modifier

// dp — единица размеров элементов.
// sp — единица размера текста.
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.debounce

import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight


import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.ColorFilter
import androidx.core.content.res.ResourcesCompat



// ============================================================
// 1. ГЛАВНАЯ ACTIVITY
// ============================================================

// Activity — это компонент Android, управляющий экраном приложения.
//
// При запуске Midnight Android создаёт MainActivity
// и вызывает метод onCreate().

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        // Вызываем реализацию onCreate() родительского класса.
        // Это необходимо для корректной работы Android.
        super.onCreate(savedInstanceState)

        // Переставляем будильники таймера при каждом запуске.
        // Это восстанавливает таймер, если пользователь
        // принудительно останавливал приложение.
        TimerScheduler.reschedule(this)

        // setContent сообщает Android:
        // "Весь интерфейс этого окна будет создан через Compose".
        setContent {

            // MaterialTheme предоставляет стандартные настройки
            // оформления элементов Material Design.
            MaterialTheme {

                // Какой экран сейчас открыт: главный или таймер.
                var showTimer by rememberSaveable { mutableStateOf(false) }

                if (showTimer) {
                    TimerScreen(onBack = { showTimer = false })
                } else {
                    NightLightScreen(onOpenTimer = { showTimer = true })
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()

        // Оставляем существующее уведомление экранов
        // о возвращении пользователя в приложение.
        AppLifecycle.resumeCount++

        // Проверяем, разрешено ли ещё создавать
        // окна поверх других приложений.
        val permissionGranted =
            Settings.canDrawOverlays(this)

        // Если сервис работает, но разрешение исчезло,
        // останавливаем фильтр.
        if (
            NightFilterService.isRunning &&
            !permissionGranted
        ) {

            Log.w(
                "NightFilter",
                "Overlay permission missing on resume"
            )

            stopService(
                Intent(
                    this,
                    NightFilterService::class.java
                )
            )
        }
    }
}


// ============================================================
// 2. ГЛАВНЫЙ ЭКРАН
// ============================================================

// @Composable обозначает функцию, которая описывает интерфейс.
//
// В Compose мы не ищем кнопки через findViewById().
// Вместо этого описываем, как интерфейс должен выглядеть
// при текущих значениях переменных.

@OptIn(FlowPreview::class)
@Composable
fun NightLightScreen(onOpenTimer: () -> Unit) {


    // Context — доступ к возможностям Android:
    // запуску сервисов, системным настройкам и другим функциям.
    //
    // LocalContext.current получает Context текущего экрана.
    val context = LocalContext.current


    // --------------------------------------------------------
    // СОСТОЯНИЕ ПРИЛОЖЕНИЯ
    // --------------------------------------------------------

    // enabled показывает, включён ли наш фильтр.
    //
    // false = выключен
    // true  = включён
    //
    // Пока это только состояние кнопки.
    // Настоящий фильтр добавим позднее.

    // Теперь состояние кнопки берём из сервиса.
    //
    // Когда сервис меняет isRunning, Compose замечает
    // изменение и автоматически обновляет кнопку.
    val enabled = NightFilterService.isRunning

    // warmth — теплота будущего фильтра.
    //
    // 0.0 = нейтральный цвет
    // 1.0 = максимально тёплый
    //
    // Float — число с дробной частью.
    // Буква f сообщает Kotlin, что число имеет тип Float.

    // Начальное значение читаем из сохранённых настроек.
    var warmth by rememberSaveable {
        mutableFloatStateOf(FilterPrefs.warmth(context))
    }

    // dimming — степень затемнения.
    //
    // 0.0 = нет затемнения
    // 1.0 = максимальное значение на нашей шкале
    //
    // В настоящем фильтре мы позже ограничим непрозрачность
    // до безопасного уровня, чтобы не мешать касаниям.

    var dimming by rememberSaveable {
        mutableFloatStateOf(FilterPrefs.dimming(context))
    }

    // Сохраняем теплоту и затемнение на диск, чтобы таймер
    // мог включить фильтр с этими параметрами, когда
    // приложение закрыто. debounce ждёт, пока палец
    // остановится, и не пишет на диск на каждое движение.
    LaunchedEffect(Unit) {
        snapshotFlow { warmth to dimming }
            .debounce(300L)
            .collect { (w, d) -> FilterPrefs.saveFilter(context, w, d) }
    }

    // Настройки таймера для подписи на кнопке "Timer".
    val timerEnabled = remember { FilterPrefs.timerEnabled(context) }
    val timerStart = remember { FilterPrefs.timerStart(context) }
    val timerEnd = remember { FilterPrefs.timerEnd(context) }

    // Текст ошибки для Toast берём заранее: внутри обработчиков
    // нажатий stringResource вызывать нельзя.
    val errorStartFilter = stringResource(R.string.error_start_filter)


    // LaunchedEffect запускает код при изменении
    // одного из перечисленных параметров.
    //
    // В нашем случае он реагирует на:
    // - включение фильтра;
    // - изменение теплоты;
    // - изменение затемнения.

    LaunchedEffect(enabled) {

        // Если фильтр выключен, ничего не делаем.
        if (!enabled) return@LaunchedEffect

        // Следим за изменениями двух параметров.
        snapshotFlow {
            warmth to dimming
        }
            // Берём последнее значение каждые ~33 мс.
            // Это примерно 30 обновлений в секунду.
            .sample(33L)

            // Получаем очередную пару значений.
            .collect { (newWarmth, newDimming) ->

                val intent = Intent(
                    context,
                    NightFilterService::class.java
                ).apply {
                    action = NightFilterService.ACTION_UPDATE

                    putExtra(
                        NightFilterService.EXTRA_WARMTH,
                        newWarmth
                    )

                    putExtra(
                        NightFilterService.EXTRA_DIMMING,
                        newDimming
                    )
                }

                // Передаём обновление только при наличии разрешения.
                if (!Settings.canDrawOverlays(context)) {
                    // Разрешение исчезло — останавливаем сервис.
                    context.stopService(
                        Intent(
                            context,
                            NightFilterService::class.java
                        )
                    )
                } else {
                    try {
                        context.startService(intent)
                    } catch (e: RuntimeException) {
                        Log.e(
                            "NightFilter",
                            "Failed to send filter update",
                            e
                        )
                        // Не продолжаем работу с ошибочным состоянием.
                        context.stopService(
                            Intent(
                                context,
                                NightFilterService::class.java
                            )
                        )
                    }
                }
            }
    }



    // --------------------------------------------------------
    // ЦВЕТА ИНТЕРФЕЙСА
    // --------------------------------------------------------

    // Color(0xFF...) создаёт цвет в формате ARGB.
    //
    // FF — непрозрачность цвета.
    // Остальные цифры задают красный, зелёный и синий каналы.

    val backgroundColor = Color(0xFF141820)
    val orangeColor = Color(0xFFFFB66D)
    val inactiveColor = Color(0xFF192437)


    // ========================================================
    // 3. РАЗМЕЩЕНИЕ ЭЛЕМЕНТОВ НА ЭКРАНЕ
    // ========================================================

    // Column располагает элементы вертикально:
    //
    // Заголовок
    // Кнопка
    // Название настройки
    // Квадрат
    // Показатели
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {

        // 1. Сначала рисуем фоновую картинку.
        Image(
            painter = painterResource(
                id = R.drawable.bg
            ),

            // Описание для специальных возможностей.
            // null означает, что картинка декоративная.
            contentDescription = null,

            // Заполняем весь экран изображением.
            modifier = Modifier.fillMaxSize(),

            // Обрезаем изображение при необходимости,
            // чтобы оно заполнило экран без искажений.
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 448.dp) // Ограничиваем ширину на планшетах
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ====================================================
            // 4. ЗАГОЛОВОК
            // ====================================================

            TitleWithMoon()

            // Spacer — пустое пространство между элементами.
            Spacer(modifier = Modifier.height(28.dp))


            // ====================================================
            // 5. КНОПКА ВКЛЮЧЕНИЯ
            // ====================================================

            Button(

                // onClick вызывается при нажатии кнопки.
                //
                // Оператор ! означает логическое НЕ.
                //
                // Если enabled == false, станет true.
                // Если enabled == true, станет false.


                onClick = {

                    if (enabled) {

                        // Фильтр уже работает.
                        // Просим Android остановить наш сервис.
                        context.stopService(
                            Intent(
                                context,
                                NightFilterService::class.java
                            )
                        )

                    } else {

                        // Проверяем разрешение рисовать поверх приложений.
                        if (!Settings.canDrawOverlays(context)) {

                            // Если разрешения нет, открываем настройки.
                            val permissionIntent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )

                            context.startActivity(permissionIntent)

                        } else {

                            // Создаём Intent — сообщение для сервиса.
                            val startIntent = Intent(
                                context,
                                NightFilterService::class.java
                            ).apply {

                                action = NightFilterService.ACTION_START

                                // Передаём текущие значения квадрата.
                                putExtra(
                                    NightFilterService.EXTRA_WARMTH,
                                    warmth
                                )

                                putExtra(
                                    NightFilterService.EXTRA_DIMMING,
                                    dimming
                                )
                            }


                            try {
                                // Запрашиваем запуск foreground service.
                                ContextCompat.startForegroundService(
                                    context,
                                    startIntent
                                )

                            } catch (e: RuntimeException) {

                                // Здесь возможны ограничения фонового запуска
                                // и ошибки разрешений Android.
                                Log.e(
                                    "NightFilter",
                                    "Ошибка запуска сервиса",
                                    e
                                )

                                // Показываем пользователю короткое сообщение.
                                Toast.makeText(
                                    context,
                                    errorStartFilter,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                        }
                    }
                },

                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),

                shape = RoundedCornerShape(18.5.dp),

                // Цвет кнопки зависит от состояния enabled.
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (enabled) {

                        // При dimming = 0 кнопка оранжевая.
                        // При dimming = 1 кнопка полностью белая.
                        //
                        // lerp смешивает два цвета.
                        // Третий параметр определяет пропорцию смешивания.
                        lerp(
                            orangeColor,
                            Color.White,
                            dimming
                        )

                    } else {
                        inactiveColor
                    }
                )


            ) {

                // Когда enabled изменяется, Compose автоматически
                // обновляет текст и цвет кнопки.

                Text(
                    text = if (enabled) {
                        stringResource(R.string.toggle_on)
                    } else {
                        stringResource(R.string.toggle_off)
                    },

                    fontSize = 24.sp,
                    fontFamily = MidnightFont,
                    color =  if (enabled) {
                        Color.Black
                    } else {
                        Color.White
                    },
                )
            }


            Spacer(modifier = Modifier.height(28.dp))


            // ====================================================
            // 6. НАЗВАНИЕ НАСТРОЙКИ
            // ====================================================

            Text(
                text = stringResource(R.string.filter_settings),
                fontFamily = MidnightFont,
                fontSize = 20.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(18.dp))


            // ====================================================
            // 7. КВАДРАТ УПРАВЛЕНИЯ
            // ====================================================

            // Canvas — область, где мы можем рисовать самостоятельно.
            //
            // У квадрата две оси:
            //
            // X: слева направо увеличивается теплота.
            //
            // Y: снизу вверх увеличивается затемнение.
            //
            // В Compose координата Y растёт СВЕРХУ ВНИЗ.
            // Поэтому для затемнения мы переворачиваем значение.

            // Квадрат и подписи занимают всё место между
            // "Filter settings" и кнопкой Timer.
            //
            // На обычном телефоне (около 414×896 dp) квадрат
            // во всю ширину, как кнопка On/Off. Лишняя высота
            // уходит в промежуток над Timer, и Timer стоит внизу.
            // На низких экранах квадрат уменьшается, чтобы Timer
            // не уходил за край.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {

                // Место под подписями: отступ 14.dp + строка текста.
                val labelsHeight = 14.dp + 24.dp

                val side = minOf(
                    maxWidth,
                    400.dp,
                    (maxHeight - labelsHeight).coerceAtLeast(0.dp)
                )

                Column(modifier = Modifier.width(side)) {

                    Canvas(
                        modifier = Modifier

                            // Квадрат: сторона рассчитана выше.
                            .size(side)

                            // Обрезаем содержимое Canvas по закруглённой форме.
                            .clip(RoundedCornerShape(18.5.dp))

                            // pointerInput обрабатывает касания.
                            .pointerInput(Unit) {

                                // Получаем размер Canvas в пикселях.
                                //
                                // Здесь size — размер области обработки касаний,
                                // а не размер в dp.

                                awaitEachGesture {

                                    // Ждём первого касания пальцем.
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false
                                    )

                                    // Функция пересчитывает координаты касания
                                    // в значения теплоты и затемнения.
                                    //
                                    // position.x — горизонтальная координата.
                                    // position.y — вертикальная координата.

                                    fun updatePosition(position: Offset) {

                                        val width = size.width.toFloat()
                                        val height = size.height.toFloat()

                                        if (width <= 0f || height <= 0f) {
                                            return
                                        }

                                        // Переводим X в диапазон от 0 до 1.
                                        //
                                        // Например:
                                        // width = 300 пикселей
                                        // x = 150 пикселей
                                        //
                                        // warmth = 150 / 300 = 0.5

                                        warmth = (position.x / width)
                                            .coerceIn(0f, 1f)

                                        // Переводим Y в диапазон от 0 до 1.
                                        //
                                        // Сверху Y = 0, поэтому:
                                        // 1 - 0 = 1 (максимальное затемнение)
                                        //
                                        // Снизу Y = height:
                                        // 1 - 1 = 0 (нет затемнения)
                                        // Теперь значение растёт сверху вниз.
                                        dimming = (position.y / height)
                                            .coerceIn(0f, 1f)
                                    }

                                    // Обрабатываем первое касание.
                                    updatePosition(down.position)


                                    // Если фильтр выключен, включаем его
                                    // при взаимодействии с квадратом.
                                    if (!NightFilterService.isRunning) {

                                        // Проверяем разрешение на наложение.
                                        if (!Settings.canDrawOverlays(context)) {

                                            // Если разрешения нет — открываем настройки Android.
                                            val permissionIntent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )

                                            context.startActivity(permissionIntent)

                                        } else {

                                            // Создаём команду включения фильтра.
                                            val startIntent = Intent(
                                                context,
                                                NightFilterService::class.java
                                            ).apply {

                                                action = NightFilterService.ACTION_START

                                                // Используем только что выбранные параметры.
                                                putExtra(
                                                    NightFilterService.EXTRA_WARMTH,
                                                    warmth
                                                )

                                                putExtra(
                                                    NightFilterService.EXTRA_DIMMING,
                                                    dimming
                                                )
                                            }

                                            try {
                                                ContextCompat.startForegroundService(
                                                    context,
                                                    startIntent
                                                )
                                            } catch (e: RuntimeException) {
                                                Log.e(
                                                    "NightFilter",
                                                    "Failed to start filter",
                                                    e
                                                )

                                                Toast.makeText(
                                                    context,
                                                    errorStartFilter,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }

                                    // Продолжаем следить за пальцем,
                                    // пока пользователь его не отпустит.

                                    while (true) {

                                        val event = awaitPointerEvent()

                                        // Находим палец, который начал жест.
                                        val change = event.changes
                                            .firstOrNull {
                                                it.id == down.id
                                            } ?: break

                                        // Если палец отпущен — заканчиваем.
                                        if (!change.pressed) {
                                            break
                                        }

                                        // Обновляем значения при движении.
                                        updatePosition(change.position)

                                        // Сообщаем Compose, что обработали
                                        // это изменение координат.
                                        change.consume()
                                    }
                                }
                            }
                    ) {

                        // =================================================
                        // 8. РИСУЕМ КВАДРАТ
                        // =================================================

                        // Первый градиент меняется по горизонтали.
                        //
                        // Слева — серо-синий цвет.
                        // Справа — тёплый оранжевый.

                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF718298),
                                    Color(0xFFFFAE62)
                                )
                            )
                        )

                        // Второй градиент рисуется поверх первого.
                        //
                        // Верх — тёмный.
                        // Низ — прозрачный.
                        //
                        // Это даёт визуальное представление затемнения.

                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )


                        // =================================================
                        // 9. ПОЗИЦИЯ ИНДИКАТОРА
                        // =================================================

                        // Переводим значения 0..1 обратно в пиксели.
                        //
                        // Например:
                        // warmth = 0.5
                        // width = 300
                        // x = 150


                        // Радиус внешнего кольца индикатора.
                        // Добавляем 2.dp для учёта толщины обводки.
                        val radius = 19.dp.toPx()

                        // Не позволяем центру кольца подходить
                        // слишком близко к границам квадрата.
                        val indicatorX = (warmth * size.width)
                            .coerceIn(radius, size.width - radius)

                        val indicatorY = (dimming * size.height)
                            .coerceIn(radius, size.height - radius)


                        val indicatorPosition = Offset(
                            x = indicatorX,
                            y = indicatorY
                        )

                        // Рисуем внешнее белое кольцо индикатора.
                        drawCircle(
                            color = Color.White,
                            radius = 17.dp.toPx(),
                            center = indicatorPosition,
                            style = Stroke(
                                width = 3.dp.toPx()
                            )
                        )

                        // Рисуем маленькую белую точку внутри кольца.
                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx(),
                            center = indicatorPosition
                        )
                    }


                    Spacer(modifier = Modifier.height(14.dp))


                    // ====================================================
                    // 10. ПОДПИСИ ОСИ ТЕПЛОТЫ
                    // ====================================================

                    // Row размещает элементы горизонтально.
                    //
                    // SpaceBetween располагает первый элемент слева,
                    // а второй — справа.

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {

                        Text(
                            text = stringResource(R.string.axis_neutral),
                            fontFamily = MidnightFont,
                            color = Color.LightGray
                        )

                        Text(
                            text = stringResource(R.string.axis_warm),
                            fontFamily = MidnightFont,
                            color = orangeColor
                        )
                    }


                } // Column квадрата
            } // BoxWithConstraints

            // Минимальный отступ над кнопкой таймера.
            // Остальное свободное место уже забрал блок выше,
            // поэтому кнопка стоит внизу экрана.
            Spacer(modifier = Modifier.height(20.dp))


            // ====================================================
            // 11. КНОПКА ТАЙМЕРА
            // ====================================================

            Button(
                onClick = onOpenTimer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(18.5.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = inactiveColor
                )
            ) {
                val is24 = android.text.format.DateFormat.is24HourFormat(context)

                Text(
                    text = if (timerEnabled && timerStart != timerEnd) {
                        stringResource(
                            R.string.timer_with_range,
                            formatTime(timerStart, is24),
                            formatTime(timerEnd, is24)
                        )
                    } else {
                        stringResource(R.string.timer)
                    },
                    fontFamily = MidnightFont,
                    fontSize = 20.sp,
                    color = if (timerEnabled) orangeColor else Color.White
                )
            }
            // Снизу отступ даёт padding(24.dp) у Column.
        }
    }
}

// Создаём семейства шрифтов для приложения.
//
// FontFamily объединяет разные начертания
// одного шрифта в единую группу. Все начертания указывают
// на один файл, чтобы Android не рисовал "искусственный" жирный.

// Шрифт интерфейса зависит от языка:
// res/font/ui_font.xml    → Signika (английский, французский, немецкий);
// res/font-ru/ui_font.xml → M PLUS Rounded 1c (в Signika нет кириллицы).
val MidnightFont = FontFamily(
    Font(R.font.ui_font, FontWeight.Normal),
    Font(R.font.ui_font, FontWeight.Medium),
    Font(R.font.ui_font, FontWeight.Bold)
)

// Заголовок-логотип "Midnight" всегда пишем Signika.
val TitleFont = FontFamily(
    Font(R.font.signika_regular, FontWeight.Normal),
    Font(R.font.signika_regular, FontWeight.Bold)
)


// ========================================================
// ЗАГОЛОВОК С ИКОНКОЙ ЛУНЫ
// ========================================================
//
// Текст и иконка стоят в одном Row, поэтому Column
// центрирует их вместе как единый блок.
//
// Иконка по высоте занимает ровно промежуток от базовой
// линии до верха заглавных букв (cap height) шрифта.

@Composable
fun TitleWithMoon() {
    val titleSize = 30.sp
    val context = LocalContext.current
    val density = LocalDensity.current

    // Измеряем высоту заглавной буквы "H" в пикселях
    // тем же шрифтом и размером, что и у заголовка.
    val capHeightPx = remember(context, density, titleSize) {
        val paint = android.graphics.Paint().apply {
            typeface = ResourcesCompat.getFont(context, R.font.signika_regular)
            textSize = with(density) { titleSize.toPx() }
        }
        val bounds = android.graphics.Rect()
        paint.getTextBounds("H", 0, 1, bounds)
        -bounds.top.toFloat()
    }
    val capHeight = with(density) { capHeightPx.toDp() }

    // Иконка на 10% ниже заглавных букв, низ по-прежнему на базовой линии.
    val iconSize = capHeight * 0.9f

    Row {
        Text(
            text = "Midnight",
            fontFamily = TitleFont,
            color = Color.Black,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alignByBaseline()
        )

        // Отступ между текстом и иконкой — примерно ширина пробела.
        Spacer(modifier = Modifier.width(capHeight * 0.35f))

        Image(
            painter = painterResource(R.drawable.ic_moon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color(0xfff0a561)),
            modifier = Modifier
                .size(iconSize)
                // Низ иконки совпадает с базовой линией текста.
                .alignBy { it.measuredHeight }
        )
    }
}
