package cz.tejkl.klid

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val usageBreaker: Boolean = false,
    val breakMinutes: Int = 20,
    val autoHome: Boolean = true,
    val homeCount: Int = 6,
    val suggestApps: Boolean = true,
    val sortByUsage: Boolean = true,
    val searchContacts: Boolean = false,
    val quickReplies: Boolean = true,
    val calendarPreview: Boolean = false,
    val mediaPlayer: Boolean = true,
    val quickLock: Boolean = false,
    val showBattery: Boolean = false,
    val allowRotation: Boolean = false,
    val haptics: Boolean = true,
    val unusedFolder: Boolean = true,
    /** Vlastní názvy aplikací: klíč aplikace -> název. */
    val labels: Map<String, String> = emptyMap(),
    val grayscale: Boolean = false,
    /** Kód hardwarového tlačítka s vlastní funkcí, 0 = žádné. */
    val buttonKey: Int = 0,
    /** Akce pro 1×, 2× a 3× stisk, viz [ButtonActions]. */
    val buttonActions: List<String> = listOf(ButtonActions.COLOR, ButtonActions.WALLET, ButtonActions.TORCH),
    /** Aplikace, ve kterých zůstanou barvy. null = ještě nenastaveno, použijí se výchozí. */
    val colorApps: Set<String>? = null,
    val hidden: Set<String> = emptySet(),
    val widgetId: Int = NO_WIDGET,
) {
    companion object {
        const val NO_WIDGET = -1
    }
}

/** Nastavení v SharedPreferences, vystavená jako StateFlow. */
class SettingsStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("klid_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            usageBreaker = prefs.getBoolean("usageBreaker", d.usageBreaker),
            breakMinutes = prefs.getInt("breakMinutes", d.breakMinutes),
            autoHome = prefs.getBoolean("autoHome", d.autoHome),
            homeCount = prefs.getInt("homeCount", d.homeCount),
            suggestApps = prefs.getBoolean("suggestApps", d.suggestApps),
            sortByUsage = prefs.getBoolean("sortByUsage", d.sortByUsage),
            searchContacts = prefs.getBoolean("searchContacts", d.searchContacts),
            quickReplies = prefs.getBoolean("quickReplies", d.quickReplies),
            calendarPreview = prefs.getBoolean("calendarPreview", d.calendarPreview),
            mediaPlayer = prefs.getBoolean("mediaPlayer", d.mediaPlayer),
            quickLock = prefs.getBoolean("quickLock", d.quickLock),
            showBattery = prefs.getBoolean("showBattery", d.showBattery),
            allowRotation = prefs.getBoolean("allowRotation", d.allowRotation),
            haptics = prefs.getBoolean("haptics", d.haptics),
            unusedFolder = prefs.getBoolean("unusedFolder", d.unusedFolder),
            labels = prefs.getStringSet("labels", emptySet())!!.mapNotNull { entry ->
                entry.split(LABEL_SEPARATOR, limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap(),
            grayscale = prefs.getBoolean("grayscale", d.grayscale),
            buttonKey = prefs.getInt("buttonKey", d.buttonKey),
            buttonActions = d.buttonActions.indices.map { i -> prefs.getString("button$i", null) ?: d.buttonActions[i] },
            colorApps = prefs.getStringSet("colorApps", null)?.toSet(),
            hidden = prefs.getStringSet("hidden", d.hidden)!!.toSet(),
            widgetId = prefs.getInt("widgetId", d.widgetId),
        )
    }

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit()
            .putBoolean("usageBreaker", s.usageBreaker)
            .putInt("breakMinutes", s.breakMinutes)
            .putBoolean("autoHome", s.autoHome)
            .putInt("homeCount", s.homeCount)
            .putBoolean("suggestApps", s.suggestApps)
            .putBoolean("sortByUsage", s.sortByUsage)
            .putBoolean("searchContacts", s.searchContacts)
            .putBoolean("quickReplies", s.quickReplies)
            .putBoolean("calendarPreview", s.calendarPreview)
            .putBoolean("mediaPlayer", s.mediaPlayer)
            .putBoolean("quickLock", s.quickLock)
            .putBoolean("showBattery", s.showBattery)
            .putBoolean("allowRotation", s.allowRotation)
            .putBoolean("haptics", s.haptics)
            .putBoolean("unusedFolder", s.unusedFolder)
            .putStringSet("labels", s.labels.map { (k, v) -> k + LABEL_SEPARATOR + v }.toSet())
            .putBoolean("grayscale", s.grayscale)
            .putInt("buttonKey", s.buttonKey)
            .apply { s.buttonActions.forEachIndexed { i, action -> putString("button$i", action) } }
            .apply { if (s.colorApps != null) putStringSet("colorApps", s.colorApps) else remove("colorApps") }
            .putStringSet("hidden", s.hidden)
            .putInt("widgetId", s.widgetId)
            .apply()
    }

    companion object {
        private const val LABEL_SEPARATOR = "\t"
        @Volatile private var instance: SettingsStore? = null
        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
