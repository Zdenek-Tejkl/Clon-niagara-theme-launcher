package cz.tejkl.klid.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import cz.tejkl.klid.R

val AppFont = FontFamily(
    Font(R.font.manrope_extralight, FontWeight.ExtraLight),
    Font(R.font.manrope_light, FontWeight.Light),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

/** Material typografie celá v písmu Manrope (dialogy, nastavení). */
val AppTypography: Typography = Typography().run {
    fun TextStyle.app() = copy(fontFamily = AppFont)
    copy(
        displayLarge = displayLarge.app(), displayMedium = displayMedium.app(), displaySmall = displaySmall.app(),
        headlineLarge = headlineLarge.app(), headlineMedium = headlineMedium.app(), headlineSmall = headlineSmall.app(),
        titleLarge = titleLarge.app(), titleMedium = titleMedium.app(), titleSmall = titleSmall.app(),
        bodyLarge = bodyLarge.app(), bodyMedium = bodyMedium.app(), bodySmall = bodySmall.app(),
        labelLarge = labelLarge.app(), labelMedium = labelMedium.app(), labelSmall = labelSmall.app(),
    )
}

/** Stín pod textem, aby byl čitelný na světlých tapetách. */
val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, 2f), blurRadius = 8f)

val ShadowedText = TextStyle(color = Color.White, shadow = TextShadow, fontFamily = AppFont)

val Secondary = Color.White.copy(alpha = 0.72f)
val Accent = Color(0xFF8AB4F8)

/** Tmavé "sklo" pro plovoucí panely. */
val Glass = Color(0xFF16181C).copy(alpha = 0.86f)
val GlassEdge = Color.White.copy(alpha = 0.09f)
