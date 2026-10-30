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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.tejkl.klid.AppEntry
import cz.tejkl.klid.LauncherStatus
import cz.tejkl.klid.LauncherViewModel
import cz.tejkl.klid.MediaHub
import cz.tejkl.klid.NotificationHub
import cz.tejkl.klid.SystemActions
import cz.tejkl.klid.searchContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

@Composable
fun LauncherApp(
    viewModel: LauncherViewModel,
    resetSignal: Flow<Unit>,
    statusFlow: StateFlow<LauncherStatus>,
    mediaHub: MediaHub,
    actions: SystemActions,
) {
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val drawerApps by viewModel.drawerApps.collectAsStateWithLifecycle()
    val unusedApps by viewModel.unusedApps.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val favoriteKeys by viewModel.favoriteKeys.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val notifications by NotificationHub.byPackage.collectAsStateWithLifecycle()
    val media by mediaHub.state.collectAsStateWithLifecycle()
    val status by statusFlow.collectAsStateWithLifecycle()

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var scrubLetter by remember { mutableStateOf<String?>(null) }
    var folderOpen by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AppEntry?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    fun closeDrawer() {
        keyboard?.hide()
        drawerOpen = false
        folderOpen = false
        query = ""
        scope.launch { listState.scrollToItem(0) }
    }

    LaunchedEffect(resetSignal) {
        resetSignal.collect {
            closeDrawer()
            settingsOpen = false
        }
    }
    BackHandler(enabled = drawerOpen && !settingsOpen) { closeDrawer() }

    val contacts by produceState(emptyList(), query, settings.searchContacts) {
        value = if (!settings.searchContacts || query.isBlank()) emptyList() else {
            delay(150)
            withContext(Dispatchers.IO) { searchContacts(context, query) }
        }
    }

    val rows = remember(drawerApps, unusedApps, folderOpen, allApps, query, contacts) {
        if (query.isBlank()) {
            val folder = if (unusedApps.isEmpty()) emptyList()
            else listOf(DrawerRow.Folder(unusedApps, folderOpen)) +
                (if (folderOpen) unusedApps.map { DrawerRow.App(it) } else emptyList())
            buildRows(drawerApps, withHeaders = true) + folder
        }
        else {
            // Hledání najde i skryté aplikace.
            val needle = query.trim().fold()
            val apps = allApps.filter { needle in it.label.fold() }
                .sortedBy { if (it.label.fold().startsWith(needle)) 0 else 1 }
            buildRows(apps, withHeaders = false) +
                contacts.map { DrawerRow.Person(it) } +
                DrawerRow.Web(query.trim())
        }
    }
    val letters = remember(drawerApps, unusedApps) {
        drawerApps.map { it.section }.distinct() +
            (if (unusedApps.isEmpty()) emptyList() else listOf(DrawerRow.FOLDER_LETTER))
    }

    val menu = AppMenuActions(
        isFavorite = { it.key in favoriteKeys },
        toggleFavorite = viewModel::toggleFavorite,
        moveFavorite = viewModel::moveFavorite,
        isHidden = { it.key in settings.hidden },
        toggleHidden = viewModel::toggleHidden,
        rename = { renaming = it },
        openInfo = viewModel::openAppInfo,
        uninstall = viewModel::uninstall,
    )
    val mediaControls = remember(mediaHub) {
        MediaControls(mediaHub::playPause, { mediaHub.next() }, { mediaHub.previous() }, mediaHub::open)
    }
    val systemHaptics = LocalHapticFeedback.current
    val haptics = if (settings.haptics) systemHaptics else NoHaptics

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
        CompositionLocalProvider(LocalHapticFeedback provides haptics) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    favorites = favorites,
                    suggestions = suggestions,
                    notifications = notifications,
                    settings = settings,
                    media = media,
                    mediaControls = mediaControls,
                    menu = menu,
                    actions = actions,
                    onLaunch = viewModel::launch,
                    onNotificationClick = viewModel::openNotification,
                    onReply = viewModel::reply,
                    onSwipeUp = { drawerOpen = true },
                    onLongPress = { settingsOpen = true },
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
                        onContact = actions::openContact,
                        onWebSearch = actions::webSearch,
                        onToggleFolder = { folderOpen = !folderOpen },
                        onClose = ::closeDrawer,
                    )
                }

                if (query.isBlank()) {
                    AlphabetScrubber(
                        letters = letters,
                        onLetter = { letter ->
                            scrubLetter = letter
                            drawerOpen = true
                            val index = rows.indexOfFirst {
                                (it is DrawerRow.Header && it.letter == letter) ||
                                    (it is DrawerRow.Folder && letter == DrawerRow.FOLDER_LETTER)
                            }
                            if (index >= 0) scope.launch { listState.scrollToItem(index) }
                        },
                        onRelease = { scrubLetter = null },
                        onDoubleTap = if (settings.quickLock) {
                            { closeDrawer(); actions.lockScreen() }
                        } else null,
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

                renaming?.let { app ->
                    RenameDialog(
                        app = app,
                        onSave = { viewModel.rename(app, it); renaming = null },
                        onDismiss = { renaming = null },
                    )
                }

                AnimatedVisibility(visible = settingsOpen, enter = fadeIn(), exit = fadeOut()) {
                    SettingsScreen(
                        settings = settings,
                        status = status,
                        store = viewModel.settingsStore,
                        allApps = allApps,
                        hiddenApps = allApps.filter { it.key in settings.hidden },
                        onUnhide = viewModel::toggleHidden,
                        actions = actions,
                        onClose = { settingsOpen = false },
                    )
                }
            }
        }
    }
}

private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/** Malá písmena bez diakritiky, aby "cas" našlo "Čas". */
private fun String.fold(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

private val DIACRITICS = Regex("\\p{Mn}+")
