package dk.cocode.guard.ui.fx

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Android's "Remove animations" sets the animator duration scale to 0. */
fun motionAllowed(animatorScale: Float): Boolean = animatorScale > 0f

/** Whether the screen may move; [NeonScreen] provides it, and a screen outside it stays still. */
val LocalMotion = compositionLocalOf { false }

/** The system setting, followed live: a change made in Settings reaches a screen that stays composed. */
@Composable
fun systemMotionAllowed(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = motionAllowed(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    var allowed by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                allowed = read()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        allowed = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return allowed
}
