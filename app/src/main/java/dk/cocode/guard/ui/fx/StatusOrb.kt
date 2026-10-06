package dk.cocode.guard.ui.fx

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dk.cocode.guard.ui.theme.GuardColors

/**
 * The status glyph: a glow in the status [tone] with the shield inside. While [isProtected] and motion
 * is allowed, a slow ping ring (3.2 s) and a Cyan sweep (one turn per 4 s) run; in every other state
 * it is still. Decorative: the status word beside it says the same.
 */
@Composable
fun StatusOrb(tone: Color, @DrawableRes icon: Int, isProtected: Boolean) {
    val moving = isProtected && LocalMotion.current
    val transition = if (moving) rememberInfiniteTransition(label = "orb") else null
    val ping = transition?.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "ping",
    )
    val sweep = transition?.animateFloat(
        0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "sweep",
    )
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(64.dp)) {
            val half = size.minDimension / 2
            val core = half * 0.62f
            drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.35f), Color.Transparent), radius = half))
            drawCircle(GuardColors.Panel, core)
            drawCircle(tone.copy(alpha = 0.6f), core, style = Stroke(1.dp.toPx()))
            ping?.let {
                drawCircle(
                    tone.copy(alpha = 0.5f * (1f - it.value)), core + (half - core) * it.value,
                    style = Stroke(2.dp.toPx()),
                )
            }
            sweep?.let {
                rotate(it.value) {
                    drawCircle(
                        Brush.sweepGradient(
                            0f to Color.Transparent, 0.65f to Color.Transparent, 1f to GuardColors.Cyan, center = Offset(half, half),
                        ),
                        core, style = Stroke(2.dp.toPx()),
                    )
                }
            }
        }
        Icon(painterResource(icon), contentDescription = null, tint = tone, modifier = Modifier.size(28.dp))
    }
}
