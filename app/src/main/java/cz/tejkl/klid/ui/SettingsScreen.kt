package cz.tejkl.klid.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.tejkl.klid.AppEntry
import cz.tejkl.klid.LauncherStatus
import cz.tejkl.klid.R
import cz.tejkl.klid.Settings
import cz.tejkl.klid.SettingsStore
import cz.tejkl.klid.SystemActions

private val Background = Color(0xFF151515)
private val Warning = Color(0xFFF28B82)
private val BreakOptions = listOf(10, 15, 20, 30, 45, 60)
private val HomeCountOptions = listOf(4, 5, 6, 7, 8)

@Composable
fun SettingsScreen(
    settings: Settings,
    status: LauncherStatus,
    store: SettingsStore,
    hiddenApps: List<AppEntry>,
    onUnhide: (AppEntry) -> Unit,
    actions: SystemActions,
    onClose: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(Page.Main) }
    BackHandler { if (page == Page.Main) onClose() else page = Page.Main }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        when (page) {
            Page.Main -> MainPage(settings, status, store, hiddenApps.size, actions) { page = it }
            Page.Hidden -> HiddenPage(hiddenApps, onUnhide) { page = Page.Main }
        }
    }
}

private enum class Page { Main, Hidden }

@Composable
private fun MainPage(
    s: Settings,
    status: LauncherStatus,
    store: SettingsStore,
    hiddenCount: Int,
    actions: SystemActions,
    open: (Page) -> Unit,
) {
    fun set(transform: (Settings) -> Settings) = store.update(transform)

    /** Zapne volbu až po udělení oprávnění, vypnutí je vždy hned. */
    fun withPermission(permission: String?, enable: Boolean, transform: (Settings, Boolean) -> Settings) {
        if (!enable || permission == null) set { transform(it, enable) }
        else actions.requestPermission(permission) { granted -> if (granted) set { transform(it, true) } }
    }

    val needsNotifications = !status.hasNotificationAccess

    LazyColumn(Modifier.fillMaxSize()) {
        item { Title(stringResource(R.string.settings_title)) }

        item { Section(stringResource(R.string.section_system)) }
        item {
            Item(stringResource(R.string.set_default), warn = !status.isDefault, onClick = actions::requestDefaultLauncher)
        }
        item {
            Item(
                stringResource(R.string.notif_access),
                stringResource(R.string.notif_access_desc),
                warn = needsNotifications,
                onClick = actions::openNotificationAccess,
            )
        }

        item {
            Item(
                stringResource(R.string.usage_access),
                stringResource(R.string.usage_access_desc),
                warn = !status.usageAccess,
                onClick = actions::openUsageAccess,
            )
        }

        item { Section(stringResource(R.string.section_wellbeing)) }
        item {
            val postPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
            Toggle(
                stringResource(R.string.usage_breaker),
                stringResource(R.string.usage_breaker_desc, s.breakMinutes),
                s.usageBreaker,
            ) { on -> withPermission(postPermission, on) { st, v -> st.copy(usageBreaker = v) } }
        }
        if (s.usageBreaker) item {
            Item(stringResource(R.string.break_length), "${s.breakMinutes} min") {
                val next = BreakOptions.getOrElse(BreakOptions.indexOf(s.breakMinutes) + 1) { BreakOptions.first() }
                set { it.copy(breakMinutes = next) }
            }
        }

        item { Section(stringResource(R.string.section_apps)) }
        item {
            Toggle(
                stringResource(R.string.auto_home), stringResource(R.string.auto_home_desc), s.autoHome,
                warn = s.autoHome && !status.usageAccess,
            ) { on ->
                set { it.copy(autoHome = on) }
                if (on && !status.usageAccess) actions.openUsageAccess()
            }
        }
        if (s.autoHome) item {
            Item(stringResource(R.string.home_count), s.homeCount.toString()) {
                val next = HomeCountOptions.getOrElse(HomeCountOptions.indexOf(s.homeCount) + 1) { HomeCountOptions.first() }
                set { it.copy(homeCount = next) }
            }
        }
        item {
            Item(stringResource(R.string.hidden_apps), stringResource(R.string.hidden_apps_desc, hiddenCount)) { open(Page.Hidden) }
        }
        item {
            Toggle(stringResource(R.string.suggest_apps), stringResource(R.string.suggest_apps_desc), s.suggestApps) { on ->
                set { it.copy(suggestApps = on) }
            }
        }
        item {
            Toggle(stringResource(R.string.sort_usage), stringResource(R.string.sort_usage_desc), s.sortByUsage) { on ->
                set { it.copy(sortByUsage = on) }
            }
        }

        item { Section(stringResource(R.string.section_search)) }
        item {
            Toggle(stringResource(R.string.search_contacts), stringResource(R.string.search_contacts_desc), s.searchContacts) { on ->
                withPermission(Manifest.permission.READ_CONTACTS, on) { st, v -> st.copy(searchContacts = v) }
            }
        }

        item { Section(stringResource(R.string.section_home)) }
        item {
            Toggle(
                stringResource(R.string.quick_replies), stringResource(R.string.quick_replies_desc), s.quickReplies,
                warn = s.quickReplies && needsNotifications,
            ) { on ->
                set { it.copy(quickReplies = on) }
                if (on && needsNotifications) actions.openNotificationAccess()
            }
        }
        item {
            Toggle(stringResource(R.string.calendar_preview), stringResource(R.string.calendar_preview_desc), s.calendarPreview) { on ->
                withPermission(Manifest.permission.READ_CALENDAR, on) { st, v -> st.copy(calendarPreview = v) }
            }
        }
        item {
            Toggle(
                stringResource(R.string.media_player), stringResource(R.string.media_player_desc), s.mediaPlayer,
                warn = s.mediaPlayer && needsNotifications,
            ) { on ->
                set { it.copy(mediaPlayer = on) }
                if (on && needsNotifications) actions.openNotificationAccess()
            }
        }
        item {
            val hasWidget = s.widgetId != Settings.NO_WIDGET
            Item(
                stringResource(R.string.widget),
                stringResource(if (hasWidget) R.string.widget_remove else R.string.widget_add),
            ) { if (hasWidget) actions.removeWidget() else actions.addWidget() }
        }
        item {
            Toggle(stringResource(R.string.show_battery), null, s.showBattery) { on -> set { it.copy(showBattery = on) } }
        }

        item { Section(stringResource(R.string.section_gestures)) }
        item {
            Toggle(
                stringResource(R.string.quick_lock), stringResource(R.string.quick_lock_desc), s.quickLock,
                warn = s.quickLock && !status.lockServiceEnabled,
            ) { on ->
                set { it.copy(quickLock = on) }
                if (on && !status.lockServiceEnabled) actions.openAccessibilitySettings()
            }
        }

        item { Section(stringResource(R.string.section_advanced)) }
        item {
            Toggle(stringResource(R.string.allow_rotation), stringResource(R.string.allow_rotation_desc), s.allowRotation) { on ->
                set { it.copy(allowRotation = on) }
            }
        }
        item {
            Toggle(stringResource(R.string.haptics), stringResource(R.string.haptics_desc), s.haptics) { on ->
                set { it.copy(haptics = on) }
            }
        }
        item { Item(stringResource(R.string.wallpaper), onClick = actions::pickWallpaper) }
        item { Item(stringResource(R.string.system_settings), onClick = actions::openSystemSettings) }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun HiddenPage(apps: List<AppEntry>, onUnhide: (AppEntry) -> Unit, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                text = "‹  " + stringResource(R.string.back),
                color = Accent,
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
        item { Title(stringResource(R.string.hidden_apps)) }
        if (apps.isEmpty()) item {
            Text(
                stringResource(R.string.hidden_apps_empty),
                color = Secondary,
                fontSize = 16.sp,
                modifier = Modifier.padding(24.dp),
            )
        }
        items(apps, key = { it.key }) { app ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onUnhide(app) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(16.dp))
                Text(app.label, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.unhide_app), color = Accent, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text = text,
        color = Secondary,
        fontSize = 15.sp,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun Item(title: String, subtitle: String? = null, warn: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Labels(title, subtitle, warn, Modifier.weight(1f))
    }
}

@Composable
private fun Toggle(
    title: String,
    subtitle: String?,
    checked: Boolean,
    warn: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Labels(title, subtitle, warn, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
        )
    }
}

@Composable
private fun Labels(title: String, subtitle: String?, warn: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (subtitle != null) Text(subtitle, color = Secondary, fontSize = 14.sp)
        if (warn) Text(stringResource(R.string.needs_setup), color = Warning, fontSize = 13.sp)
    }
}
