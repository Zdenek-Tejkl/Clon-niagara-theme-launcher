package cz.tejkl.klid

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Služba přístupnosti: zamknutí obrazovky dvojklepnutím na abecedu
 * a přepínání černobílého displeje podle otevřené aplikace.
 * Čte jen název balíčku aplikace v popředí, ne obsah obrazovky.
 */
class LockService : AccessibilityService() {

    private var ignored: Set<String> = emptySet()

    override fun onServiceConnected() {
        instance = this
        // Klávesnice a systémová lišta se objevují nad aplikacemi, barvy kvůli nim neměníme.
        ignored = getSystemService(InputMethodManager::class.java).enabledInputMethodList
            .map { it.packageName }.toSet() + setOf("com.android.systemui", "android")
        Grayscale.invalidate()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored) return
        foregroundPackage = pkg
        applyGrayscale()
    }

    override fun onInterrupt() = Unit

    private fun applyGrayscale() {
        val s = SettingsStore.get(this).current
        if (!s.grayscale) return
        Grayscale.set(this, foregroundPackage !in s.colorApps.orEmpty())
    }

    companion object {
        @Volatile private var instance: LockService? = null
        @Volatile private var foregroundPackage: String? = null
        val isRunning: Boolean get() = instance != null

        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false

        /** Po změně nastavení přepočítá barvy pro aktuální aplikaci. */
        fun refresh() {
            Grayscale.invalidate()
            instance?.applyGrayscale()
        }
    }
}
