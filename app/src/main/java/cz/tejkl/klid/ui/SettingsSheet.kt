package cz.tejkl.klid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.tejkl.klid.LauncherStatus
import cz.tejkl.klid.R
import cz.tejkl.klid.SystemActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(status: LauncherStatus, actions: SystemActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SettingRow(stringResource(R.string.set_default), done = status.isDefault) {
            onDismiss(); actions.requestDefaultLauncher()
        }
        SettingRow(stringResource(R.string.notif_access), done = status.hasNotificationAccess) {
            onDismiss(); actions.openNotificationAccess()
        }
        SettingRow(stringResource(R.string.wallpaper)) { onDismiss(); actions.pickWallpaper() }
        SettingRow(stringResource(R.string.system_settings)) { onDismiss(); actions.openSystemSettings() }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingRow(label: String, done: Boolean? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (done != null) Text(if (done) "✓" else "!", color = if (done) Accent else Color(0xFFF28B82))
    }
}
