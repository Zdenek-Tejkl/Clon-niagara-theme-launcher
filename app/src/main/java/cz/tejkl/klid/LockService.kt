package cz.tejkl.klid

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stav učení tlačítka, zobrazovaný v nastavení. */
sealed interface LearnState {
    data object Idle : LearnState
    data object Waiting : LearnState
    data class Learned(val keyCode: Int) : LearnState
    /** Místo stisku se otevřela jiná aplikace, tlačítko k nám nedorazilo. */
    data class OpenedApp(val packageName: String) : LearnState
}

/**
 * Služba přístupnosti: zamknutí obrazovky, přepínání černobílého displeje podle
 * otevřené aplikace a vlastní funkce hardwarového tlačítka (1×, 2×, 3× stisk).
 * Čte jen název balíčku aplikace v popředí a stisky tlačítek, obsah obrazovky nečte.
 */
class LockService : AccessibilityService() {

    private var ignored: Set<String> = emptySet()
    private val handler = Handler(Looper.getMainLooper())
    private var pressCount = 0
    private var colorOverride = false

    private val firePresses = Runnable {
        val count = pressCount
        pressCount = 0
        val s = SettingsStore.get(this).current
        s.buttonActions.getOrNull(count - 1)?.let { ButtonActions.run(this, it) }
    }

    override fun onServiceConnected() {
        instance = this
        // Klávesnice a systémová lišta se objevují nad aplikacemi, barvy kvůli nim neměníme.
        ignored = getSystemService(InputMethodManager::class.java).enabledInputMethodList
            .map { it.packageName }.toSet() + setOf("com.android.systemui", "android")
        Grayscale.invalidate()
        ButtonActions.watchTorch(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        handler.removeCallbacks(firePresses)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored) return
        if (_learn.value == LearnState.Waiting && pkg != packageName) {
            _learn.value = LearnState.OpenedApp(pkg)
        }
        foregroundPackage = pkg
        applyGrayscale()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (_learn.value == LearnState.Waiting) {
            if (event.action == KeyEvent.ACTION_UP) {
                SettingsStore.get(this).update { it.copy(buttonKey = event.keyCode) }
                _learn.value = LearnState.Learned(event.keyCode)
            }
            return true
        }
        val s = SettingsStore.get(this).current
        if (s.buttonKey == 0 || event.keyCode != s.buttonKey) return false
        if (event.action == KeyEvent.ACTION_UP) {
            pressCount = (pressCount + 1).coerceAtMost(MAX_PRESSES)
            handler.removeCallbacks(firePresses)
            // Po třetím stisku není na co čekat.
            handler.postDelayed(firePresses, if (pressCount == MAX_PRESSES) 0 else PRESS_GAP_MS)
        }
        return true
    }

    override fun onInterrupt() = Unit

    fun toggleColorOverride() {
        colorOverride = !colorOverride
        ButtonActions.toast(this, if (colorOverride) R.string.colors_on else R.string.colors_off)
        Grayscale.invalidate()
        applyGrayscale()
    }

    private fun applyGrayscale() {
        val s = SettingsStore.get(this).current
        if (!s.grayscale) return
        Grayscale.set(this, !colorOverride && foregroundPackage !in s.colorApps.orEmpty())
    }

    companion object {
        private const val PRESS_GAP_MS = 400L
        private const val MAX_PRESSES = 3

        @Volatile private var instance: LockService? = null
        @Volatile private var foregroundPackage: String? = null
        private val _learn = MutableStateFlow<LearnState>(LearnState.Idle)
        val learn: StateFlow<LearnState> = _learn.asStateFlow()

        val isRunning: Boolean get() = instance != null

        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false

        /** Po změně nastavení přepočítá barvy pro aktuální aplikaci. */
        fun refresh() {
            Grayscale.invalidate()
            instance?.applyGrayscale()
        }

        private val learnTimeout = Runnable {
            if (_learn.value == LearnState.Waiting) _learn.value = LearnState.Idle
        }

        /** Čeká na stisk; po 30 s bez stisku to vzdá. */
        fun startLearning() {
            _learn.value = LearnState.Waiting
            val handler = Handler(Looper.getMainLooper())
            handler.removeCallbacks(learnTimeout)
            handler.postDelayed(learnTimeout, 30_000)
        }

        /** Plocha je vždy černobílá, pokud nejsou barvy zapnuté tlačítkem. */
        val colorOverrideActive: Boolean get() = instance?.colorOverride == true
    }
}
