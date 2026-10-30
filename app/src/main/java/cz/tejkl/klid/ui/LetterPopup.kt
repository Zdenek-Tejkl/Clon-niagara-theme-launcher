package cz.tejkl.klid.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.tejkl.klid.AppEntry

/** Rozměry karty, ze kterých se počítá i to, na kterou aplikaci prst ukazuje. */
object PopupGeometry {
    val Width = 248.dp
    val Header = 52.dp
    val Row = 56.dp
    val Padding = 8.dp
    const val MAX_APPS = 7

    fun height(density: Density, count: Int): Float =
        with(density) { (Header + Row * count + Padding * 2).toPx() }

    /** Index řádku pod prstem, nebo -1. */
    fun indexAt(density: Density, top: Float, y: Float, count: Int): Int {
        val first = top + with(density) { (Padding + Header).toPx() }
        val row = with(density) { Row.toPx() }
        val index = ((y - first) / row).toInt()
        return if (y >= first && index in 0 until count) index else -1
    }
}

/** Skleněná karta s aplikacemi pro písmeno, plovoucí vedle prstu. */
@Composable
fun LetterPopup(letter: String, apps: List<AppEntry>, selected: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(PopupGeometry.Width)
            .clip(RoundedCornerShape(28.dp))
            .background(Glass)
            .border(1.dp, GlassEdge, RoundedCornerShape(28.dp))
            .padding(PopupGeometry.Padding),
    ) {
        AnimatedContent(
            targetState = letter to apps,
            transitionSpec = {
                (fadeIn(tween(140)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 600f)) { it / 12 })
                    .togetherWith(fadeOut(tween(90)))
            },
            label = "letter",
        ) { (shownLetter, shownApps) ->
            Column {
                Row(
                    Modifier
                        .height(PopupGeometry.Header)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(shownLetter, style = ShadowedText, color = Accent, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    Text("${shownApps.size}", style = ShadowedText, color = Secondary, fontSize = 13.sp)
                }
                shownApps.forEachIndexed { index, app ->
                    PopupRow(app, index == selected)
                }
            }
        }
    }
}

@Composable
private fun PopupRow(app: AppEntry, selected: Boolean) {
    val highlight by animateFloatAsState(if (selected) 1f else 0f, spring(stiffness = 700f), label = "row")
    Row(
        Modifier
            .fillMaxWidth()
            .height(PopupGeometry.Row)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.11f * highlight))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(app.icon, contentDescription = null, modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            app.label,
            style = ShadowedText,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Malé skleněné kolečko s písmenem u prstu (při tažení v otevřeném seznamu). */
@Composable
fun LetterBubble(letter: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .size(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Glass)
            .border(1.dp, GlassEdge, RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = ShadowedText, color = Accent, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    }
}
