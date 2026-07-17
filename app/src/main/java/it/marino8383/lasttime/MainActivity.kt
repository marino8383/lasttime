package it.marino8383.lasttime

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.marino8383.lasttime.notif.AlarmScheduler
import it.marino8383.lasttime.ui.HomeScreen
import it.marino8383.lasttime.ui.theme.LastTimeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Rete di sicurezza: riallinea la sveglia a ogni apertura dell'app
        lifecycleScope.launch { AlarmScheduler.scheduleNext(this@MainActivity) }

        setContent {
            LastTimeTheme {
                HomeScreen(vm = viewModel())
            }
        }
    }
}
