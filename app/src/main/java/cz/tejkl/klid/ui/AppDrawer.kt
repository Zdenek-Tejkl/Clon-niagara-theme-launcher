package cz.tejkl.klid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
            .background(Color.Black.copy(alpha = 0.72f))
            .statusBarsPadding()
            .imePadding(),
    ) {
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            onGo = { rows.firstNotNullOfOrNull { (it as? DrawerRow.App)?.app }?.let(onLaunch) },
        )
        if (rows.isEmpty()) {
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
                when (row) {
                    is DrawerRow.Header -> Text(
                        text = row.letter,
                        style = ShadowedText,
                        color = Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
                    )

                    is DrawerRow.App -> AppRow(
                        app = row.app,
                        notifications = notifications[row.app.packageName].orEmpty(),
                        menu = menu,
                        onClick = { onLaunch(row.app) },
                        onNotificationClick = {},
                        iconSize = 32.dp,
                        labelSize = 18.sp,
                        showReorder = false,
                        showPreview = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onGo: () -> Unit) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = ShadowedText.copy(fontSize = 18.sp),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 52.dp, top = 12.dp, bottom = 8.dp),
        decorationBox = { inner ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.search_hint), style = ShadowedText, color = Secondary, fontSize = 18.sp)
                }
                inner()
            }
        },
    )
}
