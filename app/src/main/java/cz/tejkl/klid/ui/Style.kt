package cz.tejkl.klid.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle

/** Stín pod textem, aby byl čitelný na světlých tapetách. */
val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, 2f), blurRadius = 8f)

val ShadowedText = TextStyle(color = Color.White, shadow = TextShadow)

val Secondary = Color.White.copy(alpha = 0.72f)
val Accent = Color(0xFF8AB4F8)
