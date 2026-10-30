package cz.tejkl.klid.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import java.text.Normalizer

/**
 * Malá písmena bez diakritiky, znak po znaku, takže délka i indexy
 * zůstávají stejné jako u původního textu ("cas" najde "Čas" a ví, co zvýraznit).
 */
fun String.fold(): String = buildString(length) {
    for (c in this@fold) append(Normalizer.normalize(c.toString(), Normalizer.Form.NFD).first().lowercaseChar())
}

/** Text s barevně zvýrazněnou shodou s hledaným výrazem. */
fun highlightMatch(text: String, query: String?, color: Color): AnnotatedString {
    val needle = query?.trim()?.fold().orEmpty()
    val start = if (needle.isEmpty()) -1 else text.fold().indexOf(needle)
    if (start < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
            append(text.substring(start, start + needle.length))
        }
        append(text.substring(start + needle.length))
    }
}
