package cz.tejkl.klid

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.setContent
import androidx.core.app.NotificationManagerCompat
import cz.tejkl.klid.ui.LauncherApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class LauncherStatus(val isDefault: Boolean, val hasNotificationAccess: Boolean)

/** Akce, které potřebují Activity nebo systémové API. */
interface SystemActions {
    fun requestDefaultLauncher()
    fun openNotificationAccess()
    fun pickWallpaper()
    fun openSystemSettings()
    fun expandNotifications()
    fun openClock()
}

class MainActivity : ComponentActivity(), SystemActions {

    private val viewModel: LauncherViewModel by viewModels()
    private val resetSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val status = MutableStateFlow(LauncherStatus(isDefault = true, hasNotificationAccess = false))

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transparent = Color.Transparent.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(transparent),
            navigationBarStyle = SystemBarStyle.dark(transparent),
        )
        setContent {
            LauncherApp(viewModel, resetSignal, status, this)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onStop() {
        super.onStop()
        // Po návratu na plochu vždy začínáme čistě, bez otevřeného seznamu.
        resetSignal.tryEmit(Unit)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) resetSignal.tryEmit(Unit)
    }

    private fun refreshStatus() {
        val roleManager = getSystemService(RoleManager::class.java)
        status.value = LauncherStatus(
            isDefault = roleManager.isRoleHeld(RoleManager.ROLE_HOME),
            hasNotificationAccess = NotificationManagerCompat.getEnabledListenerPackages(this)
                .contains(packageName),
        )
    }

    override fun requestDefaultLauncher() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            roleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    override fun openNotificationAccess() {
        val component = ComponentName(this, NotificationService::class.java).flattenToString()
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
        if (!safeStart(detail)) safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    override fun pickWallpaper() {
        safeStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.wallpaper)))
    }

    override fun openSystemSettings() {
        safeStart(Intent(Settings.ACTION_SETTINGS))
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

    private fun safeStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
