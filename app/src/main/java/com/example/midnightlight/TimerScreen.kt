package com.example.midnightlight

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Цвета те же, что на главном экране.
private val Orange = Color(0xFFFFB66D)
private val Inactive = Color(0xFF192437)

// ============================================================
// ЭКРАН ТАЙМЕРА
// ============================================================
//
// Здесь выбираются время старта и финиша ночного режима
// и включается сам таймер. Все кнопки не ниже 56dp,
// чтобы по ним было легко попасть пальцем.

@Composable
fun TimerScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // Системная кнопка / жест "назад" тоже возвращает на главный экран.
    BackHandler(onBack = onBack)

    var enabled by remember { mutableStateOf(FilterPrefs.timerEnabled(context)) }
    var start by remember { mutableIntStateOf(FilterPrefs.timerStart(context)) }
    var end by remember { mutableIntStateOf(FilterPrefs.timerEnd(context)) }

    // Какое время сейчас редактируется: "start", "end" или null.
    var editing by remember { mutableStateOf<String?>(null) }

    // Перепроверяем разрешения при каждом возвращении на экран
    // (например, из системных настроек).
    val resume = AppLifecycle.resumeCount
    val canOverlay = remember(resume) { Settings.canDrawOverlays(context) }
    val canExact = remember(resume) { TimerScheduler.canScheduleExact(context) }

    val is24 = DateFormat.is24HourFormat(context)

    // Сохраняем настройки и переставляем будильники.
    fun save() {
        FilterPrefs.saveTimer(context, enabled, start, end)
        TimerScheduler.reschedule(context)
    }

    fun openOverlaySettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
    }

    fun openAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(R.drawable.bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ------------------------------------------------
            // КНОПКА НАЗАД
            // ------------------------------------------------

            Button(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.Start)
                    .height(56.dp),
                shape = RoundedCornerShape(18.5.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Inactive),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Text(
                    text = "←  Back",
                    fontFamily = MidnightFont,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Timer",
                fontFamily = MidnightFont,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "The night filter turns on and off by itself, " +
                        "even when the app is closed.",
                fontFamily = MidnightFont,
                fontSize = 16.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ------------------------------------------------
            // ВРЕМЯ СТАРТА И ФИНИША
            // ------------------------------------------------

            TimeButton(
                label = "Start",
                time = formatTime(start, is24),
                onClick = { editing = "start" }
            )

            Spacer(modifier = Modifier.height(16.dp))

            TimeButton(
                label = "End",
                time = formatTime(end, is24),
                onClick = { editing = "end" }
            )

            if (start == end) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Start and end must be different.",
                    fontFamily = MidnightFont,
                    fontSize = 16.sp,
                    color = Orange,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ------------------------------------------------
            // ВКЛЮЧЕНИЕ ТАЙМЕРА
            // ------------------------------------------------

            Button(
                onClick = {
                    enabled = !enabled
                    save()

                    if (enabled) {
                        // Без разрешений сразу ведём в настройки.
                        when {
                            !canOverlay -> openOverlaySettings()
                            !canExact -> openAlarmSettings()
                            // Если таймер включили посреди ночи —
                            // включаем фильтр сразу.
                            else -> TimerScheduler.applyNow(context)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(18.5.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (enabled) Orange else Inactive
                )
            ) {
                Text(
                    text = if (enabled) "Timer on" else "Timer off",
                    fontFamily = MidnightFont,
                    fontSize = 24.sp,
                    color = if (enabled) Color.Black else Color.White
                )
            }

            // ------------------------------------------------
            // ПРЕДУПРЕЖДЕНИЯ О РАЗРЕШЕНИЯХ
            // ------------------------------------------------

            if (enabled && !canOverlay) {
                PermissionWarning(
                    text = "Allow Midnight to display over other apps, " +
                            "otherwise the filter can't be shown.",
                    button = "Open settings",
                    onClick = ::openOverlaySettings
                )
            } else if (enabled && !canExact) {
                PermissionWarning(
                    text = "Allow \"Alarms & reminders\" so the timer can " +
                            "turn the filter on while the app is closed.",
                    button = "Open settings",
                    onClick = ::openAlarmSettings
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "On some devices, Midnight needs background startup permission " +
                        "to restore your timer after a restart.\n\n" +
                        "Enable it in your phone's app settings.",
                fontFamily = MidnightFont,
                fontSize = 16.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
        }
    }

    // ----------------------------------------------------
    // ДИАЛОГ ВЫБОРА ВРЕМЕНИ
    // ----------------------------------------------------

    editing?.let { which ->
        TimePickDialog(
            initial = if (which == "start") start else end,
            is24 = is24,
            onDismiss = { editing = null },
            onConfirm = { minutes ->
                if (which == "start") start = minutes else end = minutes
                editing = null
                save()
            }
        )
    }
}


// Большая кнопка "Start   22:00".
@Composable
private fun TimeButton(label: String, time: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        shape = RoundedCornerShape(18.5.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Inactive),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = MidnightFont,
                fontSize = 20.sp,
                color = Color.LightGray
            )
            Text(
                text = time,
                fontFamily = MidnightFont,
                fontSize = 34.sp,
                color = Color.White
            )
        }
    }
}


@Composable
private fun PermissionWarning(text: String, button: String, onClick: () -> Unit) {
    Spacer(modifier = Modifier.height(20.dp))

    Text(
        text = text,
        fontFamily = MidnightFont,
        fontSize = 16.sp,
        color = Orange,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.5.dp)
    ) {
        Text(
            text = button,
            fontFamily = MidnightFont,
            fontSize = 18.sp,
            color = Orange
        )
    }
}


// Стандартные часы Material для выбора времени,
// в тёмных цветах приложения.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(
    initial: Int,
    is24: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initial / 60,
        initialMinute = initial % 60,
        is24Hour = is24
    )

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Orange,
            onPrimary = Color.Black
        )
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { onConfirm(state.hour * 60 + state.minute) },
                    modifier = Modifier.heightIn(min = 56.dp)
                ) {
                    Text("OK", fontFamily = MidnightFont, fontSize = 20.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 56.dp)
                ) {
                    Text("Cancel", fontFamily = MidnightFont, fontSize = 20.sp)
                }
            },
            text = { TimePicker(state = state) }
        )
    }
}


// 1320 → "22:00" или "10:00 PM" в зависимости от настроек телефона.
fun formatTime(minutes: Int, is24: Boolean): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (is24) {
        "%02d:%02d".format(h, m)
    } else {
        val h12 = if (h % 12 == 0) 12 else h % 12
        "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
    }
}
