package dk.cocode.guard.ui.fx

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import dk.cocode.guard.ui.theme.GuardColors

private fun DrawScope.tint(color: Color, center: Offset) = drawRect(
    Brush.radialGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent), center, size.width * 0.9f),
)

/** Gradient, three soft tints and faint scanlines: drawn once, behind everything. */
private fun DrawScope.drawNight() {
    drawRect(Brush.verticalGradient(listOf(GuardColors.NightTop, GuardColors.Night)))
    tint(GuardColors.Magenta, Offset(size.width, 0f))
    tint(GuardColors.Cyan, Offset(0f, size.height * 0.25f))
    tint(GuardColors.Amber, Offset(size.width * 0.5f, size.height))
    val gap = 4.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawLine(GuardColors.OnNight, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, alpha = 0.10f)
        y += gap
    }
}

/** Faint diagonal rain in the top band only (see [rainAlphaAt]), one slow fall in nine seconds. Only drawn when motion is allowed. */
@Composable
private fun Rain() {
    val phase = rememberInfiniteTransition(label = "rain").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "rain",
    )
    Canvas(Modifier.fillMaxSize()) {
        // One vertical gradient in screen coordinates masks every pixel: it is clamped to
        // transparent below the fade end, so a stroke crossing the line is cut there too.
        val mask = Brush.verticalGradient(
            0f to GuardColors.Cyan.copy(alpha = rainAlphaAt(0f, size.height)),
            1f to Color.Transparent,
            startY = 0f,
            endY = rainFadeEnd(size.height),
        )
        val step = 36.dp.toPx()
        val length = 48.dp.toPx()
        val slant = 0.3f
        val columns = ((size.width + size.height * slant) / step).toInt() + 1
        for (i in 0 until columns) {
            val y = ((phase.value + i * 0.618f) % 1f) * (size.height + length) - length
            val x = i * step - size.height * slant + y * slant
            drawLine(
                mask, Offset(x, y), Offset(x + length * slant, y + length),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

/**
 * Every screen's frame: the night background behind a scrolling column (so text at 200% font size
 * is never clipped). It also tells the screens below whether they may move.
 */
@Composable
fun NeonScreen(content: @Composable ColumnScope.() -> Unit) {
    val motion = systemMotionAllowed()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = GuardColors.Night,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        CompositionLocalProvider(LocalMotion provides motion) {
            Box(Modifier.fillMaxSize().drawBehind { drawNight() }) {
                if (motion) Rain()
                Column(
                    modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}
