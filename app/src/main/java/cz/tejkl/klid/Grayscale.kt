package cz.tejkl.klid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Settings as SystemSettings

/**
 * Černobílý displej přes systémovou korekci barev (monochromie).
 * Zápis do Secure nastavení vyžaduje jednorázově přes adb udělené WRITE_SECURE_SETTINGS.
 */
object Grayscale {
    private const val ENABLED = "accessibility_display_daltonizer_enabled"
    private const val MODE = "accessibility_display_daltonizer"
    private const val MONOCHROMACY = 0

    const val ADB_COMMAND = "adb shell pm grant cz.tejkl.klid android.permission.WRITE_SECURE_SETTINGS"

    /** Známé galerie a fotoaparáty výrobců, doplňují to, co najde systém. */
    private val KNOWN_PHOTO_APPS = setOf(
        "com.google.android.apps.photos",
        "com.google.android.GoogleCamera",
        "com.sec.android.gallery3d",
        "com.sec.android.app.camera",
        "com.miui.gallery",
        "com.android.camera",
        "com.android.gallery3d",
        "com.oneplus.gallery",
        "com.oneplus.camera",
        "com.coloros.gallery3d",
        "com.oplus.camera",
        "com.huawei.photos",
        "com.huawei.camera",
    )

    private var lastApplied: Boolean? = null

    /** Zapomene poslední stav, aby se příště zapsal znovu (např. po ruční změně v systému). */
    fun invalidate() {
        lastApplied = null
    }

    fun canWrite(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun set(context: Context, gray: Boolean) {
        if (lastApplied == gray || !canWrite(context)) return
        try {
            val resolver = context.contentResolver
            if (gray) SystemSettings.Secure.putInt(resolver, MODE, MONOCHROMACY)
            SystemSettings.Secure.putInt(resolver, ENABLED, if (gray) 1 else 0)
            lastApplied = gray
        } catch (_: SecurityException) {
            lastApplied = null
        }
    }

    /** Galerie, Fotky a Fotoaparát nainstalované v telefonu. */
    fun defaultColorApps(context: Context, installed: Set<String>): Set<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        val resolved = intents.flatMap { intent ->
            pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
        }
        return (resolved + KNOWN_PHOTO_APPS).filter { it in installed }.toSet()
    }
}
