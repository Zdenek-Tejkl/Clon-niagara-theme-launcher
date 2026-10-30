package cz.tejkl.klid

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Process
import android.provider.MediaStore
import android.widget.Toast

/**
 * Akce přiřaditelné k vícenásobnému stisku hardwarového tlačítka.
 * Uložené jako text: "color", "wallet", "torch", "lock", "camera", "none" nebo "app:<balíček>".
 */
object ButtonActions {
    const val NONE = "none"
    const val COLOR = "color"
    const val WALLET = "wallet"
    const val TORCH = "torch"
    const val LOCK = "lock"
    const val CAMERA = "camera"
    const val APP_PREFIX = "app:"

    /** Pořadí, ve kterém se volby přepínají klepnutím v nastavení. */
    val CYCLE = listOf(NONE, COLOR, WALLET, TORCH, LOCK, CAMERA)

    private val WALLETS = listOf(
        "com.samsung.android.spay",
        "com.google.android.apps.walletnfcrel",
    )

    private var torchOn = false
    private var torchCallbackRegistered = false

    fun watchTorch(context: Context) {
        if (torchCallbackRegistered) return
        context.getSystemService(CameraManager::class.java).registerTorchCallback(
            object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    torchOn = enabled
                }
            },
            null,
        )
        torchCallbackRegistered = true
    }

    fun run(service: LockService, action: String) {
        when {
            action == COLOR -> service.toggleColorOverride()
            action == WALLET -> WALLETS.firstOrNull { launchPackage(service, it) } ?: toast(service, R.string.no_wallet)
            action == TORCH -> toggleTorch(service)
            action == LOCK -> LockService.lock()
            action == CAMERA -> start(service, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            action.startsWith(APP_PREFIX) -> launchPackage(service, action.removePrefix(APP_PREFIX))
        }
    }

    private fun toggleTorch(context: Context) {
        val camera = context.getSystemService(CameraManager::class.java)
        val id = camera.cameraIdList.firstOrNull {
            camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        try {
            camera.setTorchMode(id, !torchOn)
        } catch (_: Exception) {
            // Fotoaparát může zrovna používat jiná aplikace.
        }
    }

    private fun launchPackage(context: Context, packageName: String): Boolean {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val activity = launcherApps.getActivityList(packageName, Process.myUserHandle()).firstOrNull() ?: return false
        return try {
            launcherApps.startMainActivity(activity.componentName, activity.user, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    fun toast(context: Context, text: Int) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
