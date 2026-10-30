package cz.tejkl.klid

import android.app.ActivityOptions
import android.app.Application
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    private val userManager = app.getSystemService(UserManager::class.java)
    private val favoritesStore = FavoritesStore(app)
    private val usageStore = UsageStore(app)
    val settingsStore = SettingsStore.get(app)
    val settings: StateFlow<Settings> = settingsStore.settings

    /** Aplikace tak, jak je vrátil systém, bez vlastních názvů. */
    private val systemApps = MutableStateFlow<List<AppEntry>>(emptyList())

    /** Všechny aplikace včetně skrytých, s vlastními názvy, abecedně. */
    private val _apps: StateFlow<List<AppEntry>> =
        combine(systemApps, settings.map { it.labels }.distinctUntilChanged()) { apps, labels ->
            val collator = Collator.getInstance(Locale.getDefault())
            apps.map { app ->
                val custom = labels[app.key]
                if (custom == null) app else app.copy(label = custom, section = sectionOf(custom))
            }.sortedWith { a, b -> collator.compare(a.label, b.label) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allApps: StateFlow<List<AppEntry>> = _apps

    val favoriteKeys: StateFlow<List<String>> = favoritesStore.keys

    private val systemUsage = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val lastUsed = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Aplikace neotevřené přes půl roku (a ne čerstvě nainstalované). Bez přístupu k využití prázdné. */
    val unusedApps: StateFlow<List<AppEntry>> =
        combine(_apps, settings, favoritesStore.keys, lastUsed) { apps, s, pinnedKeys, last ->
            if (!s.unusedFolder || last.isEmpty()) return@combine emptyList()
            val threshold = System.currentTimeMillis() - UNUSED_AFTER_MS
            apps.filter {
                it.key !in s.hidden && it.key !in pinnedKeys &&
                    it.installedAt < threshold && (last[it.packageName] ?: 0L) < threshold
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Aplikace pro abecední seznam: bez skrytých a nepoužívaných, v rámci písmene případně podle používání. */
    val drawerApps: StateFlow<List<AppEntry>> =
        combine(_apps, settings, usageStore.scores, unusedApps) { apps, s, scores, unused ->
            val unusedKeys = unused.map { it.key }.toSet()
            val visible = apps.filter { it.key !in s.hidden && it.key !in unusedKeys }
            if (!s.sortByUsage) visible
            else groupConsecutive(visible).flatMap { group ->
                group.sortedByDescending { scores[it.key] ?: 0f }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Pořadí podle používání: systémová statistika, jinak vlastní záznam spuštění. */
    private val ranked: StateFlow<List<AppEntry>> =
        combine(_apps, settings, usageStore.scores, systemUsage) { apps, s, scores, system ->
            apps.filter { it.key !in s.hidden }
                .map { it to (system[it.packageName] ?: 0) * 1000f + (scores[it.key] ?: 0f) }
                .filter { it.second > 0f }
                .sortedByDescending { it.second }
                .map { it.first }
                .distinctBy { it.packageName }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Ručně připnuté aplikace. */
    private val pinned: StateFlow<List<AppEntry>> =
        combine(_apps, favoritesStore.keys) { apps, keys ->
            val byKey = apps.associateBy { it.key }
            keys.mapNotNull { byKey[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Plocha: připnuté nahoře, zbytek doplní nejpoužívanější aplikace. */
    val favorites: StateFlow<List<AppEntry>> =
        combine(pinned, ranked, settings) { pinned, ranked, s ->
            if (!s.autoHome) return@combine pinned
            val pinnedKeys = pinned.map { it.key }.toSet()
            pinned + ranked.filter { it.key !in pinnedKeys }.take((s.homeCount - pinned.size).coerceAtLeast(0))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Další často používané aplikace, které nejsou na ploše. */
    val suggestions: StateFlow<List<AppEntry>> =
        combine(ranked, favorites, settings) { ranked, home, s ->
            if (!s.suggestApps) return@combine emptyList()
            val onHome = home.map { it.key }.toSet()
            ranked.filter { it.key !in onHome }.take(SUGGESTION_COUNT)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(callback)
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.Default) {
            val ownPackage = getApplication<Application>().packageName
            val density = getApplication<Application>().resources.displayMetrics.density
            val iconSize = (56 * density).toInt()
            val packageManager = getApplication<Application>().packageManager
            val me = android.os.Process.myUserHandle()
            systemApps.value = userManager.userProfiles.flatMap { user ->
                val serial = userManager.getSerialNumberForUser(user)
                launcherApps.getActivityList(null, user)
                    .filter { it.applicationInfo.packageName != ownPackage }
                    .map { info ->
                        val label = info.label.toString().trim()
                        AppEntry(
                            key = "${info.componentName.flattenToString()}#$serial",
                            label = label,
                            originalLabel = label,
                            packageName = info.applicationInfo.packageName,
                            component = info.componentName,
                            user = user,
                            icon = IconRenderer.render(info.getIcon(0), iconSize).let { bmp ->
                                // Aplikace z pracovního profilu dostanou systémový odznak.
                                if (user == me) bmp
                                else packageManager.getUserBadgedIcon(android.graphics.drawable.BitmapDrawable(getApplication<Application>().resources, bmp), user)
                                    .toBitmap(iconSize, iconSize)
                            }.asImageBitmap(),
                            section = sectionOf(label),
                            installedAt = info.firstInstallTime,
                        )
                    }
            }
        }
    }

    fun refreshSystemUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            systemUsage.value = SystemUsage.openCounts(getApplication())
            lastUsed.value = SystemUsage.lastUsed(getApplication())
        }
    }

    /** Prázdný nebo původní název přejmenování zruší. */
    fun rename(app: AppEntry, name: String) = settingsStore.update {
        val clean = name.trim()
        it.copy(labels = if (clean.isEmpty() || clean == app.originalLabel) it.labels - app.key else it.labels + (app.key to clean))
    }

    fun toggleFavorite(app: AppEntry) = favoritesStore.toggle(app.key)
    fun moveFavorite(app: AppEntry, delta: Int) = favoritesStore.move(app.key, delta)

    fun toggleHidden(app: AppEntry) = settingsStore.update {
        it.copy(hidden = if (app.key in it.hidden) it.hidden - app.key else it.hidden + app.key)
    }

    fun launch(app: AppEntry) {
        try {
            launcherApps.startMainActivity(app.component, app.user, null, null)
            usageStore.recordLaunch(app.key)
            val s = settings.value
            if (s.usageBreaker) BreakReminder.schedule(getApplication(), app.label, s.breakMinutes)
        } catch (e: Exception) {
            reload()
        }
    }

    fun openNotification(app: AppEntry, item: NotificationItem) {
        val intent = item.contentIntent ?: return launch(app)
        try {
            val options = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }
            intent.send(getApplication(), 0, null, null, null, null, options.toBundle())
        } catch (e: Exception) {
            launch(app)
        }
    }

    fun reply(item: NotificationItem, text: String) {
        item.reply?.send(getApplication(), text)
    }

    fun openAppInfo(app: AppEntry) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }

    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    private fun groupConsecutive(apps: List<AppEntry>): List<List<AppEntry>> {
        val groups = mutableListOf<MutableList<AppEntry>>()
        for (app in apps) {
            if (groups.lastOrNull()?.first()?.section == app.section) groups.last() += app
            else groups += mutableListOf(app)
        }
        return groups
    }

    private fun sectionOf(label: String): String {
        val first = label.firstOrNull()?.uppercaseChar() ?: return "#"
        if (!first.isLetter()) return "#"
        // České CH a háčkovaná písmena nechávám jako samostatné sekce, jiné diakritiky slučuji.
        if (isCzech && label.length > 1 && first == 'C' && label[1].uppercaseChar() == 'H') return "CH"
        if (first in CZECH_LETTERS) return first.toString()
        return Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first().toString()
    }

    private val isCzech get() = Locale.getDefault().language == "cs"

    private companion object {
        val CZECH_LETTERS = setOf('Č', 'Ř', 'Š', 'Ž')
        const val SUGGESTION_COUNT = 4
        const val UNUSED_AFTER_MS = 182L * 24 * 60 * 60 * 1000
    }
}
