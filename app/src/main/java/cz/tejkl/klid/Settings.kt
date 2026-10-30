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
    val grayscale: Boolean = false,
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
            grayscale = prefs.getBoolean("grayscale", d.grayscale),
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
            .putBoolean("grayscale", s.grayscale)
            .apply { if (s.colorApps != null) putStringSet("colorApps", s.colorApps) else remove("colorApps") }
            .putStringSet("hidden", s.hidden)
            .putInt("widgetId", s.widgetId)
            .apply()
    }

    companion object {
        @Volatile private var instance: SettingsStore? = null
        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
