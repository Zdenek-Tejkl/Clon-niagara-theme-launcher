package cz.tejkl.klid.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.tejkl.klid.AppEntry
import cz.tejkl.klid.NotificationItem
import cz.tejkl.klid.R

/** Co lze s aplikací dělat z kontextové nabídky. */
class AppMenuActions(
    val isFavorite: (AppEntry) -> Boolean,
    val toggleFavorite: (AppEntry) -> Unit,
    val moveFavorite: (AppEntry, Int) -> Unit,
    val isHidden: (AppEntry) -> Boolean,
    val toggleHidden: (AppEntry) -> Unit,
    val openInfo: (AppEntry) -> Unit,
    val uninstall: (AppEntry) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppEntry,
    notifications: List<NotificationItem>,
    menu: AppMenuActions,
    onClick: () -> Unit,
    onNotificationClick: (NotificationItem) -> Unit,
    iconSize: Dp,
    labelSize: TextUnit,
    showReorder: Boolean,
    showPreview: Boolean,
    modifier: Modifier = Modifier,
    onReply: ((NotificationItem, String) -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current

    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RowShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(iconSize))
                Spacer(Modifier.width(16.dp))
                Text(
                    text = app.label,
                    style = ShadowedText,
                    fontSize = labelSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (notifications.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Accent)
                    )
                }
            }
            val latest = notifications.firstOrNull()
            if (showPreview && latest != null) {
                Row(
                    Modifier
                        .padding(start = iconSize + 16.dp, top = 2.dp)
                        .clip(RowShape)
                        .clickable { onNotificationClick(latest) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = listOf(latest.title, latest.text).filter { it.isNotBlank() }.joinToString(": "),
                        style = ShadowedText,
                        color = Secondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (notifications.size > 1) {
                        Text(
                            text = "  +${notifications.size - 1}",
                            style = ShadowedText,
                            color = Accent,
                            fontSize = 14.sp,
                        )
                    }
                    if (onReply != null && latest.reply != null && replyText == null) {
                        Text(
                            text = stringResource(R.string.reply),
                            style = ShadowedText,
                            color = Accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .clip(RowShape)
                                .clickable { replyText = "" }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                replyText?.let { text ->
                    ReplyField(
                        text = text,
                        onTextChange = { replyText = it },
                        onSend = {
                            if (text.isNotBlank()) onReply?.invoke(latest, text.trim())
                            replyText = null
                        },
                        modifier = Modifier.padding(start = iconSize + 16.dp, top = 6.dp),
                    )
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val favorite = menu.isFavorite(app)
            DropdownMenuItem(
                text = { Text(stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite)) },
                onClick = { menuOpen = false; menu.toggleFavorite(app) },
            )
            if (favorite && showReorder) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_up)) },
                    onClick = { menuOpen = false; menu.moveFavorite(app, -1) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_down)) },
                    onClick = { menuOpen = false; menu.moveFavorite(app, 1) },
                )
            }
            if (!favorite) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (menu.isHidden(app)) R.string.unhide_app else R.string.hide_app)) },
                    onClick = { menuOpen = false; menu.toggleHidden(app) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_info)) },
                onClick = { menuOpen = false; menu.openInfo(app) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.uninstall)) },
                onClick = { menuOpen = false; menu.uninstall(app) },
            )
        }
    }
}

@Composable
private fun ReplyField(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        textStyle = ShadowedText.copy(fontSize = 15.sp),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus),
        decorationBox = { inner ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (text.isEmpty()) Text(stringResource(R.string.reply_hint), style = ShadowedText, color = Secondary, fontSize = 15.sp)
                inner()
            }
        },
    )
}

private val RowShape = RoundedCornerShape(12.dp)
