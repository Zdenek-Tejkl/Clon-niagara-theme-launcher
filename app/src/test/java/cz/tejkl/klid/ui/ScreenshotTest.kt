package cz.tejkl.klid.ui

import android.content.ComponentName
import android.graphics.Color as AndroidColor
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import cz.tejkl.klid.AppEntry
import cz.tejkl.klid.IconRenderer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Vykreslí hlavní prvky do PNG v build/outputs/roborazzi, ať jde vzhled zkontrolovat bez telefonu. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val palette = listOf(0xFF1DB954, 0xFFE1306C, 0xFF4285F4, 0xFFFFC107, 0xFF7E57C2, 0xFF26A69A, 0xFFEF5350)

    private fun app(label: String, i: Int, adaptive: Boolean = true): AppEntry {
        val color = palette[i % palette.size].toInt()
        val drawable = if (adaptive) {
            val glyph = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(AndroidColor.WHITE) }
            AdaptiveIconDrawable(ColorDrawable(color), InsetDrawable(glyph, 0.36f))
        } else {
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        }
        return AppEntry(
            key = label, label = label, originalLabel = label, packageName = "x.$label",
            component = ComponentName("x", label), user = Process.myUserHandle(),
            icon = IconRenderer.render(drawable, 168).asImageBitmap(),
            section = label.take(1).uppercase(), installedAt = 0L,
        )
    }

    private val wallpaper = Brush.verticalGradient(listOf(Color(0xFF2B3A55), Color(0xFF6B4E71), Color(0xFFCE8B6B)))

    @Composable
    private fun Frame(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Accent), typography = AppTypography) {
            Box(Modifier.fillMaxSize().background(wallpaper)) { content() }
        }
    }

    @Test
    fun popup() {
        val apps = listOf("Mapy", "Messenger", "Mobilní data", "Moje O2", "Monzo").mapIndexed { i, l -> app(l, i, i != 3) }
        compose.setContent {
            Frame {
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 64.dp)) {
                    LetterPopup("M", apps, selected = 1)
                }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 120.dp).padding(top = 420.dp)) {
                    LetterBubble("M")
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/popup.png")
    }

    @Test
    fun drawer() {
        val apps = listOf("Čas", "Calendar", "Chrome", "Clock", "Contacts", "Camera", "Mapy", "Messenger").mapIndexed { i, l -> app(l, i) }
        compose.setContent {
            Frame {
                AppDrawer(
                    rows = buildRows(apps.filter { "c" in it.label.fold() }, withHeaders = false) + DrawerRow.Web("c"),
                    query = "c",
                    onQueryChange = {},
                    listState = rememberLazyListState(),
                    notifications = emptyMap(),
                    menu = AppMenuActions({ false }, {}, { _, _ -> }, { false }, {}, {}, {}, {}),
                    onLaunch = {}, onContact = {}, onWebSearch = {}, onToggleFolder = {}, onClose = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/drawer.png")
    }

    @Test
    fun rows() {
        val apps = listOf("WhatsApp", "Fotky", "Spotify", "Klid").mapIndexed { i, l -> app(l, i, i != 2) }
        compose.setContent {
            Frame {
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    apps.forEach {
                        AppRow(it, emptyList(), AppMenuActions({ false }, {}, { _, _ -> }, { false }, {}, {}, {}, {}),
                            {}, {}, 36.dp, androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp), false, false)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/rows.png")
    }
}
