package it.marino8383.lasttime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import it.marino8383.lasttime.ui.HomeScreen
import it.marino8383.lasttime.ui.theme.LastTimeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LastTimeTheme {
                HomeScreen(vm = viewModel())
            }
        }
    }
}
