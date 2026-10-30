package cz.tejkl.klid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.tejkl.klid.LauncherStatus
import cz.tejkl.klid.LauncherViewModel
import cz.tejkl.klid.NotificationHub
import cz.tejkl.klid.SystemActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer

@Composable
fun LauncherApp(
    viewModel: LauncherViewModel,
    resetSignal: Flow<Unit>,
    statusFlow: StateFlow<LauncherStatus>,
    actions: SystemActions,
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteKeys by viewModel.favoriteKeys.collectAsStateWithLifecycle()
    val notifications by NotificationHub.byPackage.collectAsStateWithLifecycle()
    val status by statusFlow.collectAsStateWithLifecycle()

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var scrubLetter by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    fun closeDrawer() {
        keyboard?.hide()
        drawerOpen = false
        query = ""
        scope.launch { listState.scrollToItem(0) }
    }

    LaunchedEffect(resetSignal) {
        resetSignal.collect {
            closeDrawer()
            settingsOpen = false
        }
    }
    BackHandler(enabled = drawerOpen) { closeDrawer() }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else {
            val needle = query.trim().fold()
            apps.filter { needle in it.label.fold() }
                .sortedBy { if (it.label.fold().startsWith(needle)) 0 else 1 }
        }
    }
    val rows = remember(filtered, query) { buildRows(filtered, withHeaders = query.isBlank()) }
    val letters = remember(apps) { apps.map { it.section }.distinct() }

    val menu = AppMenuActions(
        isFavorite = { it.key in favoriteKeys },
        toggleFavorite = viewModel::toggleFavorite,
        moveFavorite = viewModel::moveFavorite,
        openInfo = viewModel::openAppInfo,
        uninstall = viewModel::uninstall,
    )

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                favorites = favorites,
                notifications = notifications,
                menu = menu,
                onLaunch = viewModel::launch,
                onNotificationClick = viewModel::openNotification,
                onSwipeUp = { drawerOpen = true },
                onSwipeDown = actions::expandNotifications,
                onLongPress = { settingsOpen = true },
                onClockClick = actions::openClock,
            )

            AnimatedVisibility(
                visible = drawerOpen,
                enter = fadeIn() + slideInVertically { it / 6 },
                exit = fadeOut() + slideOutVertically { it / 6 },
            ) {
                AppDrawer(
                    rows = rows,
                    query = query,
                    onQueryChange = {
                        query = it
                        scope.launch { listState.scrollToItem(0) }
                    },
                    listState = listState,
                    notifications = notifications,
                    menu = menu,
                    onLaunch = viewModel::launch,
                    onClose = ::closeDrawer,
                )
            }

            if (query.isBlank()) {
                AlphabetScrubber(
                    letters = letters,
                    onLetter = { letter ->
                        scrubLetter = letter
                        drawerOpen = true
                        val index = rows.indexOfFirst { it is DrawerRow.Header && it.letter == letter }
                        if (index >= 0) scope.launch { listState.scrollToItem(index) }
                    },
                    onRelease = { scrubLetter = null },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .systemBarsPadding()
                        .padding(top = 96.dp, bottom = 48.dp, end = 8.dp)
                        .fillMaxHeight(),
                )
            }

            scrubLetter?.let { letter ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter, style = ShadowedText, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (settingsOpen) {
                SettingsSheet(status, actions) { settingsOpen = false }
            }
        }
    }
}

/** Malá písmena bez diakritiky, aby "cas" našlo "Čas". */
private fun String.fold(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

private val DIACRITICS = Regex("\\p{Mn}+")
