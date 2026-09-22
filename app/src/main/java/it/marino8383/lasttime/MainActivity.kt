package it.marino8383.lasttime

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.marino8383.lasttime.notif.AlarmScheduler
import it.marino8383.lasttime.notif.Notifications
import it.marino8383.lasttime.ui.HomeScreen
import it.marino8383.lasttime.ui.theme.LastTimeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Contatore per cui la notifica ha chiesto "Rimanda": apre la maschera di snooze. */
    private var snoozeRequest by mutableStateOf<Long?>(null)

    /** Codice dal link d'invito toccato (https://.../lasttime/join?code=...). */
    private var joinRequest by mutableStateOf<String?>(null)

    private fun readSnoozeExtra(intent: Intent?) {
        intent?.getLongExtra(Notifications.EXTRA_SNOOZE_COUNTER_ID, -1L)
            ?.takeIf { it > 0 }
            ?.let { snoozeRequest = it }
    }

    private fun readJoinExtra(intent: Intent?) {
        intent?.data
            ?.takeIf { it.host == "marino8383.github.io" && it.path?.startsWith("/lasttime/join") == true }
            ?.getQueryParameter("code")
            ?.takeIf { it.isNotBlank() }
            ?.let { joinRequest = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Rete di sicurezza: riallinea la sveglia a ogni apertura dell'app
        lifecycleScope.launch { AlarmScheduler.scheduleNext(this@MainActivity) }

        readSnoozeExtra(intent)
        readJoinExtra(intent)

        setContent {
            LastTimeTheme {
                HomeScreen(
                    vm = viewModel(),
                    snoozeCounterId = snoozeRequest,
                    onSnoozeHandled = { snoozeRequest = null },
                    joinCode = joinRequest,
                    onJoinHandled = { joinRequest = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readSnoozeExtra(intent)
        readJoinExtra(intent)
    }
}
