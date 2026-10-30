package cz.tejkl.klid.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Svislá abeceda u okraje. Při tažení se písmena kolem prstu vlní doleva a zvětšují.
 */
@Composable
fun AlphabetScrubber(
    letters: List<String>,
    onLetter: (String) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var active by remember { mutableStateOf<Int?>(null) }
    var heightPx by remember { mutableIntStateOf(1) }
    val haptics = LocalHapticFeedback.current
    val currentLetters by rememberUpdatedState(letters)
    val currentOnLetter by rememberUpdatedState(onLetter)
    val currentOnRelease by rememberUpdatedState(onRelease)

    fun select(y: Float) {
        val list = currentLetters
        if (list.isEmpty()) return
        val index = (y / heightPx * list.size).toInt().coerceIn(0, list.lastIndex)
        if (index != active) {
            active = index
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnLetter(list[index])
        }
    }

    Column(
        modifier = modifier
            .width(36.dp)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    select(down.position.y)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        select(change.position.y)
                    }
                    active = null
                    currentOnRelease()
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEachIndexed { index, letter ->
            val distance = active?.let { abs(it - index) }
            val target = if (distance == null) 0f else (1f - distance / 4f).coerceAtLeast(0f)
            val strength by animateFloatAsState(target, spring(stiffness = 600f), label = "wave")
            Text(
                text = letter,
                style = ShadowedText,
                fontSize = 11.sp,
                fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Medium,
                color = if (distance == 0) Color.White else Secondary,
                modifier = Modifier.graphicsLayer {
                    translationX = -strength * 56.dp.toPx()
                    scaleX = 1f + strength
                    scaleY = 1f + strength
                },
            )
        }
    }
}
