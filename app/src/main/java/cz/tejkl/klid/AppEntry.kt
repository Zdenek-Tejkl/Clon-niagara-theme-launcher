package cz.tejkl.klid

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap

data class AppEntry(
    /** Stabilní klíč: komponenta + sériové číslo profilu (kvůli pracovnímu profilu). */
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    /** Písmeno sekce v abecedním seznamu, "#" pro čísla a symboly. */
    val section: String,
)
