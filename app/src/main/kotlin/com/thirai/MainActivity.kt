package com.thirai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.thirai.ui.ThiraiSetupScreen
import com.thirai.ui.theme.ThiraiTheme

/**
 * The phone app's single screen. The mother never opens this — the widget is
 * her whole interface (see docs/INTENT.md). This activity exists only so the
 * owner can point Thirai at the TV, refresh the show list, and diagnose the
 * connection. It carries the same premium finish as the widget it configures.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThiraiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ThiraiSetupScreen()
                }
            }
        }
    }
}
