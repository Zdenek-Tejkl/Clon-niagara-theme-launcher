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

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())

    /** Všechny aplikace včetně skrytých, abecedně. Pro hledání a správu skrytých. */
    val allApps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    val favoriteKeys: StateFlow<List<String>> = favoritesStore.keys

    /** Aplikace pro abecední seznam: bez skrytých, v rámci písmene případně podle používání. */
    val drawerApps: StateFlow<List<AppEntry>> =
        combine(_apps, settings, usageStore.scores) { apps, s, scores ->
            val visible = apps.filter { it.key !in s.hidden }
            if (!s.sortByUsage) visible
            else groupConsecutive(visible).flatMap { group ->
                group.sortedByDescending { scores[it.key] ?: 0f }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<List<AppEntry>> =
        combine(_apps, favoritesStore.keys) { apps, keys ->
            val byKey = apps.associateBy { it.key }
            keys.mapNotNull { byKey[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Často používané aplikace, které nejsou na ploše. */
    val suggestions: StateFlow<List<AppEntry>> =
        combine(_apps, favoritesStore.keys, settings, usageStore.scores) { apps, keys, s, scores ->
            if (!s.suggestApps) return@combine emptyList()
            apps.filter { it.key !in keys && it.key !in s.hidden && (scores[it.key] ?: 0f) >= 1f }
                .sortedByDescending { scores[it.key] }
                .take(SUGGESTION_COUNT)
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
            val collator = Collator.getInstance(Locale.getDefault())
            val iconSize = (48 * getApplication<Application>().resources.displayMetrics.density).toInt()
            _apps.value = userManager.userProfiles.flatMap { user ->
                val serial = userManager.getSerialNumberForUser(user)
                launcherApps.getActivityList(null, user)
                    .filter { it.applicationInfo.packageName != ownPackage }
                    .map { info ->
                        val label = info.label.toString().trim()
                        AppEntry(
                            key = "${info.componentName.flattenToString()}#$serial",
                            label = label,
                            packageName = info.applicationInfo.packageName,
                            component = info.componentName,
                            user = user,
                            icon = info.getBadgedIcon(0).toBitmap(iconSize, iconSize).asImageBitmap(),
                            section = sectionOf(label),
                        )
                    }
            }.sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
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
    }
}
