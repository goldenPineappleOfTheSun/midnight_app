package com.example.midnightlight

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


// ============================================================
// РАЗРЕШЕНИЕ "ПОВЕРХ ДРУГИХ ПРИЛОЖЕНИЙ"
// ============================================================
//
// Это особое разрешение: системного окна "Разрешить / Отклонить"
// для него нет, пользователь включает его сам в настройках.
//
// На Android 11+ система всегда открывает общий список
// приложений (даже если попросить страницу Midnight).
// Поэтому перед переходом показываем окно с объяснением.


// Открывает системный экран с разрешением.
fun openOverlaySettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    )
}


// Окно-объяснение перед переходом в настройки.
//
// onConfirm — пользователь нажал "Понятно" (открываем настройки).
// onDismiss — нажал "Отмена" или закрыл окно.
@Composable
fun OverlayPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Android 8–10 сразу открывает страницу Midnight,
    // Android 11+ — общий список, где Midnight надо найти.
    val text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        stringResource(R.string.overlay_permission_list)
    } else {
        stringResource(R.string.overlay_permission_page)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFFB66D),
            onPrimary = Color.Black
        )
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.overlay_permission_title),
                    fontFamily = MidnightFont,
                    fontSize = 22.sp
                )
            },
            text = {
                Text(
                    text = text,
                    fontFamily = MidnightFont,
                    fontSize = 17.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.heightIn(min = 56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.got_it),
                        fontFamily = MidnightFont,
                        fontSize = 20.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        fontFamily = MidnightFont,
                        fontSize = 20.sp
                    )
                }
            }
        )
    }
}
