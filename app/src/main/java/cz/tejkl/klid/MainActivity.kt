package cz.tejkl.klid

import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings as SystemSettings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import cz.tejkl.klid.ui.LauncherApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class LauncherStatus(
    val isDefault: Boolean = true,
    val hasNotificationAccess: Boolean = false,
    val lockServiceEnabled: Boolean = false,
)

/** Akce, které potřebují Activity nebo systémové API. */
interface SystemActions {
    fun requestDefaultLauncher()
    fun openNotificationAccess()
    fun openAccessibilitySettings()
    fun requestPermission(permission: String, onResult: (Boolean) -> Unit)
    fun pickWallpaper()
    fun openSystemSettings()
    fun expandNotifications()
    fun openClock()
    fun openCalendar()
    fun openEvent(event: CalendarEvent)
    fun openContact(contact: Contact)
    fun webSearch(query: String)
    fun lockScreen()
    fun addWidget()
    fun removeWidget()
    fun createWidgetView(widgetId: Int): View?
    fun widgetMinHeightPx(widgetId: Int): Int
}

class MainActivity : ComponentActivity(), SystemActions {

    private val viewModel: LauncherViewModel by viewModels()
    private val resetSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val status = MutableStateFlow(LauncherStatus())
    private lateinit var mediaHub: MediaHub
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private var pendingWidgetId = Settings.NO_WIDGET
    private var permissionCallback: ((Boolean) -> Unit)? = null

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshStatus()
    }

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionCallback?.invoke(granted)
        permissionCallback = null
    }

    private val widgetPick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingWidgetId
        if (result.resultCode != Activity.RESULT_OK || id == Settings.NO_WIDGET) return@registerForActivityResult discardPendingWidget()
        val info = widgetManager.getAppWidgetInfo(id) ?: return@registerForActivityResult discardPendingWidget()
        if (info.configure != null) {
            try {
                @Suppress("DEPRECATION")
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_WIDGET_CONFIG, null)
            } catch (_: Exception) {
                commitWidget(id)
            }
        } else {
            commitWidget(id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transparent = Color.Transparent.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(transparent),
            navigationBarStyle = SystemBarStyle.dark(transparent),
        )
        mediaHub = MediaHub(this)
        widgetHost = AppWidgetHost(this, WIDGET_HOST_ID)
        widgetManager = AppWidgetManager.getInstance(this)

        lifecycleScope.launch {
            viewModel.settings.collect {
                requestedOrientation = if (it.allowRotation) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        setContent {
            LauncherApp(viewModel, resetSignal, status, mediaHub, this)
        }
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
        mediaHub.start()
        // Uživatel je zpět na ploše, připomínka přestávky už není potřeba.
        BreakReminder.cancel(this)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
        mediaHub.stop()
        // Po návratu na plochu vždy začínáme čistě, bez otevřeného seznamu.
        resetSignal.tryEmit(Unit)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) resetSignal.tryEmit(Unit)
    }

    @Deprecated("Konfigurace widgetů zatím nemá Activity Result API.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_WIDGET_CONFIG) return
        if (resultCode == Activity.RESULT_OK) commitWidget(pendingWidgetId) else discardPendingWidget()
    }

    private fun refreshStatus() {
        val roleManager = getSystemService(RoleManager::class.java)
        val lockComponent = ComponentName(this, LockService::class.java).flattenToString()
        val enabledServices = SystemSettings.Secure.getString(
            contentResolver, SystemSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        status.value = LauncherStatus(
            isDefault = roleManager.isRoleHeld(RoleManager.ROLE_HOME),
            hasNotificationAccess = NotificationManagerCompat.getEnabledListenerPackages(this)
                .contains(packageName),
            lockServiceEnabled = enabledServices.split(':').any { it.equals(lockComponent, ignoreCase = true) },
        )
    }

    override fun requestDefaultLauncher() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            roleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            safeStart(Intent(SystemSettings.ACTION_HOME_SETTINGS))
        }
    }

    override fun openNotificationAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val component = ComponentName(this, NotificationService::class.java).flattenToString()
            val detail = Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(SystemSettings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
            if (safeStart(detail)) return
        }
        safeStart(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    override fun openAccessibilitySettings() {
        safeStart(Intent(SystemSettings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun requestPermission(permission: String, onResult: (Boolean) -> Unit) {
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onResult(true)
            return
        }
        permissionCallback = onResult
        permissionRequest.launch(permission)
    }

    override fun pickWallpaper() {
        safeStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.wallpaper)))
    }

    override fun openSystemSettings() {
        safeStart(Intent(SystemSettings.ACTION_SETTINGS))
    }

    @SuppressLint("WrongConstant")
    override fun expandNotifications() {
        try {
            val statusBar = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(statusBar)
        } catch (_: Exception) {
            // Některé systémy to nepovolí, gesto pak nic nedělá.
        }
    }

    override fun openClock() {
        safeStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    }

    override fun openCalendar() {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .also { ContentUris.appendId(it, System.currentTimeMillis()) }.build()
        safeStart(Intent(Intent.ACTION_VIEW, uri))
    }

    override fun openEvent(event: CalendarEvent) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
        safeStart(
            Intent(Intent.ACTION_VIEW, uri)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
        )
    }

    override fun openContact(contact: Contact) {
        safeStart(Intent(Intent.ACTION_VIEW, contact.uri))
    }

    override fun webSearch(query: String) {
        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)
        if (!safeStart(search)) {
            safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(query))))
        }
    }

    override fun lockScreen() {
        if (!LockService.lock()) openAccessibilitySettings()
    }

    override fun addWidget() {
        discardPendingWidget()
        pendingWidgetId = widgetHost.allocateAppWidgetId()
        widgetPick.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
        )
    }

    override fun removeWidget() {
        val id = viewModel.settings.value.widgetId
        if (id != Settings.NO_WIDGET) widgetHost.deleteAppWidgetId(id)
        viewModel.settingsStore.update { it.copy(widgetId = Settings.NO_WIDGET) }
    }

    override fun createWidgetView(widgetId: Int): View? {
        val info = widgetManager.getAppWidgetInfo(widgetId) ?: return null
        return widgetHost.createView(applicationContext, widgetId, info)
    }

    override fun widgetMinHeightPx(widgetId: Int): Int =
        widgetManager.getAppWidgetInfo(widgetId)?.minHeight ?: 0

    private fun commitWidget(id: Int) {
        val old = viewModel.settings.value.widgetId
        if (old != Settings.NO_WIDGET && old != id) widgetHost.deleteAppWidgetId(old)
        viewModel.settingsStore.update { it.copy(widgetId = id) }
        pendingWidgetId = Settings.NO_WIDGET
    }

    private fun discardPendingWidget() {
        if (pendingWidgetId != Settings.NO_WIDGET) widgetHost.deleteAppWidgetId(pendingWidgetId)
        pendingWidgetId = Settings.NO_WIDGET
    }

    private fun safeStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val WIDGET_HOST_ID = 1024
        const val REQUEST_WIDGET_CONFIG = 7
    }
}
