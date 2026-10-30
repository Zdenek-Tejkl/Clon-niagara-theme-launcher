package cz.tejkl.klid.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/** Okamžitý stav tažení: písmeno pod prstem, svislá poloha v px vůči oknu a posun doleva (záporný). */
data class Scrub(val letter: String, val y: Float, val dx: Float)

/**
 * Svislá abeceda u okraje. Písmena kolem prstu se plynule vlní doleva a zvětšují
 * (Gaussova křivka podle přesné polohy prstu), po puštění se pružně vrátí.
 */
@Composable
fun AlphabetScrubber(
    letters: List<String>,
    onLetter: (String) -> Unit,
    onScrub: (Scrub) -> Unit,
    onRelease: (scrub: Scrub?, tapped: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    lockLetter: Boolean = false,
    onDoubleTap: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val presence = remember { Animatable(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableIntStateOf(1) }
    var topInRoot by remember { mutableFloatStateOf(0f) }
    var activeIndex by remember { mutableStateOf<Int?>(null) }
    var lastTapAt by remember { mutableLongStateOf(0L) }

    val currentLetters by rememberUpdatedState(letters)
    val currentLock by rememberUpdatedState(lockLetter)
    val currentOnLetter by rememberUpdatedState(onLetter)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

    fun indexAt(y: Float): Int {
        val n = currentLetters.size
        return (y / heightPx * n).toInt().coerceIn(0, (n - 1).coerceAtLeast(0))
    }

    /** Aktualizuje vlnu i písmeno; při zamčení (prst v kartě aplikací) jen hlásí polohu. */
    fun track(y: Float, dx: Float): Scrub? {
        val list = currentLetters
        if (list.isEmpty()) return null
        if (!currentLock) {
            fingerY = y.coerceIn(0f, heightPx.toFloat())
            val index = indexAt(y)
            if (index != activeIndex) {
                activeIndex = index
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnLetter(list[index])
            }
        }
        val scrub = Scrub(list[activeIndex ?: indexAt(y)], topInRoot + y, dx)
        currentOnScrub(scrub)
        return scrub
    }

    Column(
        modifier = modifier
            .width(36.dp)
            .onGloballyPositioned {
                heightPx = it.size.height.coerceAtLeast(1)
                topInRoot = it.positionInRoot().y
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var moved = false
                    var last: Scrub? = null
                    val waitForDoubleTap = currentOnDoubleTap != null
                    scope.launch { presence.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 900f)) }
                    // Při povoleném dvojklepnutí čekáme s výběrem písmene na pohyb prstu.
                    if (!waitForDoubleTap) last = track(down.position.y, 0f)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        val dy = abs(change.position.y - down.position.y)
                        val dx = change.position.x - down.position.x
                        if (dy > viewConfiguration.touchSlop || abs(dx) > viewConfiguration.touchSlop) moved = true
                        if (moved || !waitForDoubleTap) last = track(change.position.y, dx)
                    }
                    var tapped = !moved
                    if (tapped && waitForDoubleTap) {
                        val now = down.uptimeMillis
                        if (now - lastTapAt < viewConfiguration.doubleTapTimeoutMillis) {
                            lastTapAt = 0L
                            tapped = false
                            last = null
                            currentOnDoubleTap?.invoke()
                        } else {
                            lastTapAt = now
                            last = track(down.position.y, 0f)
                        }
                    }
                    currentOnRelease(last, tapped)
                    activeIndex = null
                    scope.launch { presence.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val n = letters.size.coerceAtLeast(1)
        val slot = heightPx.toFloat() / n
        letters.forEachIndexed { index, letter ->
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = letter,
                    style = ShadowedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.graphicsLayer {
                        val center = (index + 0.5f) * slot
                        val d = (fingerY - center) / slot
                        val wave = exp(-(d * d) / (2f * SIGMA * SIGMA)) * presence.value
                        translationX = -wave * 64.dp.toPx()
                        scaleX = 1f + wave * 1.35f
                        scaleY = scaleX
                        alpha = 0.6f + 0.4f * wave
                    },
                    color = if (activeIndex == index) lerp(Secondary, Accent, presence.value) else Secondary,
                )
            }
        }
    }
}

/** Šířka vlny v počtu písmen. */
private const val SIGMA = 1.7f
