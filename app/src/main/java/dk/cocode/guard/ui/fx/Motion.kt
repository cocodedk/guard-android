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

/** The y from which no rain is visible: 30% of the screen height. */
fun rainFadeEnd(height: Float): Float = height * 0.3f

/** Rain is faint at the top and gone from 30% of the screen height down, so it never crosses body text. */
fun rainAlphaAt(y: Float, height: Float, peak: Float = 0.15f): Float {
    val fadeEnd = rainFadeEnd(height)
    return if (fadeEnd <= 0f) 0f else peak * (1f - y / fadeEnd).coerceIn(0f, 1f)
}

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
