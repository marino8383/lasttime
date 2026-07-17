package it.marino8383.lasttime.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import it.marino8383.lasttime.notif.AlarmScheduler

private class CheckInfo(
    val title: String,
    val ok: Boolean,
    val description: String,
    val fix: ((Context) -> Unit)?,
)

/**
 * Diagnostica affidabilità notifiche: permesso notifiche, sveglie esatte,
 * esenzione ottimizzazione batteria (il punto critico su Samsung/Xiaomi & co).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Al ritorno dalle impostazioni di sistema i check vanno ricalcolati
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // ...e la sveglia ri-registrata, ora che i permessi possono essere cambiati
    LaunchedEffect(refresh) { AlarmScheduler.scheduleNext(context) }

    val checks = remember(refresh) { buildChecks(context) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "🩺 Diagnostica notifiche",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Perché le campanelle arrivino puntuali anche ad app chiusa, tutti e tre i punti devono essere verdi.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            checks.forEach { check ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (check.ok) "✅" else "⚠️", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            check.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            check.description,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!check.ok && check.fix != null) {
                        TextButton(onClick = { check.fix.invoke(context) }) {
                            Text("Sistema", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun buildChecks(context: Context): List<CheckInfo> {
    val nm = context.getSystemService(NotificationManager::class.java)
    val am = context.getSystemService(AlarmManager::class.java)
    val pm = context.getSystemService(PowerManager::class.java)

    val notifOk = nm.areNotificationsEnabled()
    val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    val batteryOk = pm.isIgnoringBatteryOptimizations(context.packageName)

    return listOf(
        CheckInfo(
            title = "Notifiche",
            ok = notifOk,
            description = if (notifOk) "L'app può mostrare notifiche."
            else "Le notifiche sono disattivate: le campanelle non appariranno.",
            fix = { ctx ->
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
        ),
        CheckInfo(
            title = "Sveglie esatte",
            ok = exactOk,
            description = if (exactOk) "Le campanelle scattano all'orario preciso."
            else "Senza questo permesso le campanelle possono slittare di parecchi minuti.",
            fix = { ctx -> AlarmScheduler.ensureExactAlarmPermission(ctx) },
        ),
        CheckInfo(
            title = "Ottimizzazione batteria",
            ok = batteryOk,
            description = if (batteryOk) "L'app è esente: il sistema non la addormenta."
            else "Su molti telefoni (Samsung, Xiaomi…) il risparmio batteria blocca gli avvisi ad app chiusa.",
            fix = { ctx ->
                try {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${ctx.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                    // fallback: lista generale delle app
                    ctx.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
        ),
    )
}
