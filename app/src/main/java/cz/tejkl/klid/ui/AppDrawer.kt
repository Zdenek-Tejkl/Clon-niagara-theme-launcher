package cz.tejkl.klid.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.tejkl.klid.AppEntry
import cz.tejkl.klid.Contact
import cz.tejkl.klid.NotificationItem
import cz.tejkl.klid.R

/** Řádek seznamu: buď nadpis sekce, nebo aplikace. */
sealed interface DrawerRow {
    val key: String

    data class Header(val letter: String) : DrawerRow {
        override val key get() = "h:$letter"
    }

    data class App(val app: AppEntry) : DrawerRow {
        override val key get() = app.key
    }

    data class Person(val contact: Contact) : DrawerRow {
        override val key get() = "c:${contact.id}"
    }

    data class Web(val query: String) : DrawerRow {
        override val key get() = "web"
    }

    /** Složka nepoužívaných aplikací na konci seznamu. */
    data class Folder(val apps: List<AppEntry>, val expanded: Boolean) : DrawerRow {
        override val key get() = "folder"
    }

    companion object {
        /** Písmeno abecedy, které skočí na složku. */
        const val FOLDER_LETTER = "…"
    }
}

fun buildRows(apps: List<AppEntry>, withHeaders: Boolean): List<DrawerRow> {
    if (!withHeaders) return apps.map { DrawerRow.App(it) }
    val rows = ArrayList<DrawerRow>(apps.size + 30)
    var last: String? = null
    for (app in apps) {
        if (app.section != last) {
            rows += DrawerRow.Header(app.section)
            last = app.section
        }
        rows += DrawerRow.App(app)
    }
    return rows
}

@Composable
fun AppDrawer(
    rows: List<DrawerRow>,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    notifications: Map<String, List<NotificationItem>>,
    menu: AppMenuActions,
    onLaunch: (AppEntry) -> Unit,
    onContact: (Contact) -> Unit,
    onWebSearch: (String) -> Unit,
    onToggleFolder: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Přetažení dolů na začátku seznamu ho zavře.
    val closeOnPull = remember(listState) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > 220f) {
                        pulled = 0f
                        onClose()
                    }
                } else {
                    pulled = 0f
                }
                return Offset.Zero
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .statusBarsPadding()
            .imePadding(),
    ) {
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            onGo = {
                val app = rows.firstNotNullOfOrNull { (it as? DrawerRow.App)?.app }
                if (app != null) onLaunch(app) else if (query.isNotBlank()) onWebSearch(query.trim())
            },
        )
        if (rows.none { it is DrawerRow.App } && query.isBlank()) {
            Text(
                text = stringResource(R.string.no_results),
                style = ShadowedText,
                color = Secondary,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 52.dp, bottom = 24.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(closeOnPull)
                .navigationBarsPadding(),
        ) {
            items(rows, key = { it.key }, contentType = { it::class }) { row ->
                val item = Modifier.animateItem(
                    fadeInSpec = tween(180),
                    placementSpec = spring(dampingRatio = 0.85f, stiffness = 500f, visibilityThreshold = IntOffset.VisibilityThreshold),
                    fadeOutSpec = tween(120),
                )
                when (row) {
                    is DrawerRow.Header -> Text(
                        text = row.letter,
                        style = ShadowedText,
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                        modifier = item.padding(start = 10.dp, top = 22.dp, bottom = 6.dp),
                    )

                    is DrawerRow.App -> AppRow(
                        app = row.app,
                        notifications = notifications[row.app.packageName].orEmpty(),
                        menu = menu,
                        onClick = { onLaunch(row.app) },
                        onNotificationClick = {},
                        iconSize = 36.dp,
                        labelSize = 18.sp,
                        showReorder = false,
                        showPreview = false,
                        highlight = query,
                        modifier = item,
                    )

                    is DrawerRow.Person -> ActionRow(
                        text = row.contact.name,
                        modifier = item,
                        badge = { Text(row.contact.name.take(1).uppercase(), style = ShadowedText, fontSize = 15.sp) },
                    ) { onContact(row.contact) }

                    is DrawerRow.Folder -> Box(item) { FolderRow(row, onToggleFolder) }

                    is DrawerRow.Web -> ActionRow(
                        text = stringResource(R.string.web_search, row.query),
                        modifier = item,
                        badge = { SearchIcon(Modifier.size(16.dp), Color.White) },
                    ) { onWebSearch(row.query) }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: DrawerRow.Folder, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Náhled složky: mřížka 2×2 z ikon prvních aplikací.
        Column(Modifier.size(32.dp)) {
            folder.apps.take(4).chunked(2).forEach { pair ->
                Row {
                    pair.forEach { Image(it.icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.unused_folder), style = ShadowedText, fontSize = 18.sp)
            Text(
                stringResource(R.string.unused_folder_count, folder.apps.size),
                style = ShadowedText,
                color = Secondary,
                fontSize = 13.sp,
            )
        }
        Text(if (folder.expanded) "\u2303" else "\u2304", style = ShadowedText, color = Secondary, fontSize = 18.sp)
    }
}

@Composable
private fun ActionRow(text: String, modifier: Modifier, badge: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            badge()
        }
        Spacer(Modifier.width(16.dp))
        Text(text, style = ShadowedText, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onGo: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focus by animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "focus")
    val shape = RoundedCornerShape(28.dp)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        interactionSource = interaction,
        textStyle = ShadowedText.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 52.dp, top = 12.dp, bottom = 8.dp),
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.10f + 0.05f * focus))
                    .border(1.dp, lerp(GlassEdge, Accent.copy(alpha = 0.6f), focus), shape)
                    .padding(start = 18.dp, end = 8.dp)
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchIcon(Modifier.size(18.dp), lerp(Secondary, Accent, focus))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.search_hint), style = ShadowedText, color = Secondary, fontSize = 17.sp, maxLines = 1)
                    }
                    inner()
                }
                AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        CloseIcon(Modifier.size(12.dp), Secondary)
                    }
                }
            }
        },
    )
}

/** Lupa kreslená čarou, ať sedí k tenkému písmu. */
@Composable
fun SearchIcon(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = size.minDimension / 9f
        val r = size.minDimension * 0.34f
        val c = Offset(r + stroke, r + stroke)
        drawCircle(color, r, c, style = Stroke(stroke))
        drawLine(color, Offset(c.x + r * 0.72f, c.y + r * 0.72f), Offset(size.width - stroke / 2, size.height - stroke / 2), stroke, StrokeCap.Round)
    }
}

@Composable
private fun CloseIcon(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = size.minDimension / 6f
        drawLine(color, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
    }
}
