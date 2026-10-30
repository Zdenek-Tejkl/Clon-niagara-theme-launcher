package cz.tejkl.klid.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import cz.tejkl.klid.AppEntry
import cz.tejkl.klid.CalendarEvent
import cz.tejkl.klid.MediaState
import cz.tejkl.klid.NotificationItem
import cz.tejkl.klid.R
import cz.tejkl.klid.Settings
import cz.tejkl.klid.SystemActions
import cz.tejkl.klid.upcomingEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Ovládání přehrávače z plochy. */
class MediaControls(
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val open: () -> Unit,
)

@Composable
fun HomeScreen(
    favorites: List<AppEntry>,
    suggestions: List<AppEntry>,
    notifications: Map<String, List<NotificationItem>>,
    settings: Settings,
    media: MediaState?,
    mediaControls: MediaControls,
    menu: AppMenuActions,
    actions: SystemActions,
    onLaunch: (AppEntry) -> Unit,
    onNotificationClick: (AppEntry, NotificationItem) -> Unit,
    onReply: (NotificationItem, String) -> Unit,
    onSwipeUp: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        val threshold = 60.dp.toPx()
                        if (total < -threshold) onSwipeUp() else if (total > threshold) actions.expandNotifications()
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
        Clock(now, settings.showBattery, actions)
        if (settings.calendarPreview) CalendarPreview(now, actions)
        Spacer(Modifier.weight(1f))

        if (settings.widgetId != Settings.NO_WIDGET) {
            key(settings.widgetId) { Widget(settings.widgetId, actions) }
            Spacer(Modifier.height(16.dp))
        }
        if (settings.mediaPlayer && media != null) {
            MediaCard(media, mediaControls)
            Spacer(Modifier.height(12.dp))
        }
        if (suggestions.isNotEmpty()) {
            Suggestions(suggestions, onLaunch)
        }
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
                onReply = if (settings.quickReplies) onReply else null,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Clock(now: Long, showBattery: Boolean, actions: SystemActions) {
    val date = Date(now)
    val battery = if (showBattery) rememberBatteryLevel() else null
    val noRipple = remember { MutableInteractionSource() }
    Column(Modifier.padding(horizontal = 8.dp)) {
        Text(
            text = DateFormat.getTimeInstance(DateFormat.SHORT).format(date),
            style = ShadowedText,
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.clickable(noRipple, indication = null, onClick = actions::openClock),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = DateFormat.getDateInstance(DateFormat.FULL).format(date),
                style = ShadowedText,
                color = Secondary,
                fontSize = 16.sp,
                modifier = Modifier.clickable(noRipple, indication = null, onClick = actions::openCalendar),
            )
            if (battery != null && battery >= 0) {
                Text(
                    text = "  ·  " + stringResource(R.string.battery, battery),
                    style = ShadowedText,
                    color = Secondary,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun rememberBatteryLevel(): Int {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (raw >= 0 && scale > 0) level = raw * 100 / scale
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let { receiver.onReceive(context, it) }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return level
}

@Composable
private fun CalendarPreview(now: Long, actions: SystemActions) {
    val context = LocalContext.current
    // Klíčem je čas zaokrouhlený na minuty, takže se seznam obnovuje s hodinami.
    val events by produceState(emptyList<CalendarEvent>(), now) {
        value = withContext(Dispatchers.IO) { upcomingEvents(context) }
    }
    if (events.isEmpty()) return
    Column(Modifier.padding(start = 8.dp, top = 16.dp)) {
        val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
        events.forEach { event ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { actions.openEvent(event) }
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = if (event.allDay) "•" else timeFormat.format(Date(event.begin)),
                    style = ShadowedText,
                    color = Accent,
                    fontSize = 15.sp,
                    modifier = Modifier.width(56.dp),
                )
                Text(
                    text = event.title,
                    style = ShadowedText,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Widget(widgetId: Int, actions: SystemActions) {
    val minHeight = with(LocalDensity.current) { actions.widgetMinHeightPx(widgetId).toDp() }
    AndroidView(
        factory = { context -> actions.createWidgetView(widgetId) ?: View(context) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.coerceAtLeast(48.dp)),
    )
}

@Composable
private fun MediaCard(media: MediaState, controls: MediaControls) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = controls.open)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (media.art != null) {
            Image(
                bitmap = media.art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(media.title, style = ShadowedText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (media.artist.isNotBlank()) {
                Text(media.artist, style = ShadowedText, color = Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MediaButton(R.drawable.ic_previous, controls.previous)
        MediaButton(if (media.playing) R.drawable.ic_pause else R.drawable.ic_play, controls.playPause)
        MediaButton(R.drawable.ic_next, controls.next)
    }
}

@Composable
private fun MediaButton(icon: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun Suggestions(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    Row(
        Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        apps.forEach { app ->
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onLaunch(app) },
            )
        }
    }
}
