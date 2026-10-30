package cz.tejkl.klid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.tejkl.klid.AppEntry
import cz.tejkl.klid.NotificationItem
import cz.tejkl.klid.R
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    favorites: List<AppEntry>,
    notifications: Map<String, List<NotificationItem>>,
    menu: AppMenuActions,
    onLaunch: (AppEntry) -> Unit,
    onNotificationClick: (AppEntry, NotificationItem) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onLongPress: () -> Unit,
    onClockClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        val threshold = 60.dp.toPx()
                        if (total < -threshold) onSwipeUp() else if (total > threshold) onSwipeDown()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    total += dragAmount
                }
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .systemBarsPadding()
            .padding(start = 16.dp, end = 52.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Clock(onClockClick)
        Spacer(Modifier.weight(1f))
        if (favorites.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_home),
                style = ShadowedText,
                color = Secondary,
                fontSize = 16.sp,
                modifier = Modifier.padding(8.dp),
            )
        }
        favorites.forEach { app ->
            AppRow(
                app = app,
                notifications = notifications[app.packageName].orEmpty(),
                menu = menu,
                onClick = { onLaunch(app) },
                onNotificationClick = { onNotificationClick(app, it) },
                iconSize = 36.dp,
                labelSize = 24.sp,
                showReorder = true,
                showPreview = true,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Clock(onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    val date = Date(now)
    Column(
        Modifier
            .padding(horizontal = 8.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Text(
            text = DateFormat.getTimeInstance(DateFormat.SHORT).format(date),
            style = ShadowedText,
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
        )
        Text(
            text = DateFormat.getDateInstance(DateFormat.FULL).format(date),
            style = ShadowedText,
            color = Secondary,
            fontSize = 16.sp,
        )
    }
}
