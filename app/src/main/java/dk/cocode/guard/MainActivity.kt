package dk.cocode.guard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dk.cocode.guard.ui.HomeScreen
import dk.cocode.guard.ui.theme.GuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GuardTheme {
                HomeScreen()
            }
        }
    }
}
