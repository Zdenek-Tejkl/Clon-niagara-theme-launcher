package cz.tejkl.klid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import cz.tejkl.klid.LearnState
import cz.tejkl.klid.LockService
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
            // Při učení tlačítka může stisk otevřít jinou aplikaci; nastavení pak musí po návratu ukázat proč.
            if (LockService.learn.value == LearnState.Idle) settingsOpen = false
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

    // Tažení po abecedě: poloha prstu a případné zamčení v kartě aplikací (prst posunutý doleva).
    var scrub by remember { mutableStateOf<Scrub?>(null) }
    var lockY by remember { mutableStateOf<Float?>(null) }
    var rootHeight by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val insets = WindowInsets.systemBars
    val popupLetter = scrubLetter
    val popupApps = remember(popupLetter, drawerApps, unusedApps) {
        when (popupLetter) {
            null -> emptyList()
            DrawerRow.FOLDER_LETTER -> unusedApps
            else -> drawerApps.filter { it.section == popupLetter }
        }.take(PopupGeometry.MAX_APPS)
    }
    val popupHeight = PopupGeometry.height(density, popupApps.size)
    val popupTop = run {
        val anchor = lockY ?: scrub?.y ?: 0f
        val minTop = insets.getTop(density) + with(density) { 16.dp.toPx() }
        val maxTop = (rootHeight - insets.getBottom(density) - popupHeight - with(density) { 16.dp.toPx() })
            .coerceAtLeast(minTop)
        (anchor - popupHeight / 2).coerceIn(minTop, maxTop)
    }
    val selectedApp = if (lockY == null) -1
    else PopupGeometry.indexAt(density, popupTop, scrub?.y ?: 0f, popupApps.size)
    val showPopup = scrub != null && !drawerOpen && popupApps.isNotEmpty()

    LaunchedEffect(selectedApp) {
        if (selectedApp >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // Plocha za seznamem a za kartou se rozostří a lehce oddálí, ať je jasné, co je v popředí.
    val depth by animateFloatAsState(
        when {
            drawerOpen -> 1f
            showPopup -> 0.4f
            else -> 0f
        },
        spring(dampingRatio = 0.9f, stiffness = 320f),
        label = "depth",
    )

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent), typography = AppTypography) {
        CompositionLocalProvider(LocalHapticFeedback provides haptics) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { rootHeight = it.height.toFloat() },
            ) {
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
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = 1f - 0.05f * depth
                            scaleY = scaleX
                            alpha = 1f - 0.35f * depth
                        }
                        .blur(28.dp * depth),
                )

                AnimatedVisibility(
                    visible = drawerOpen,
                    enter = fadeIn(tween(200)) +
                        slideInVertically(spring(dampingRatio = 0.82f, stiffness = 420f)) { it / 10 } +
                        scaleIn(spring(dampingRatio = 0.82f, stiffness = 420f), initialScale = 0.97f),
                    exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 12 },
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

                fun jumpTo(letter: String) {
                    val index = rows.indexOfFirst {
                        (it is DrawerRow.Header && it.letter == letter) ||
                            (it is DrawerRow.Folder && letter == DrawerRow.FOLDER_LETTER)
                    }
                    if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                }

                if (query.isBlank()) {
                    AlphabetScrubber(
                        letters = letters,
                        lockLetter = lockY != null,
                        onLetter = { letter ->
                            scrubLetter = letter
                            if (drawerOpen) jumpTo(letter)
                        },
                        onScrub = { s ->
                            scrub = s
                            if (!drawerOpen) {
                                val enter = with(density) { -56.dp.toPx() }
                                val exit = with(density) { -24.dp.toPx() }
                                if (lockY == null && s.dx < enter) lockY = s.y
                                else if (lockY != null && s.dx > exit) lockY = null
                            }
                        },
                        onRelease = { s, tapped ->
                            when {
                                tapped && s != null -> {
                                    drawerOpen = true
                                    folderOpen = s.letter == DrawerRow.FOLDER_LETTER
                                    jumpTo(s.letter)
                                }
                                selectedApp >= 0 -> viewModel.launch(popupApps[selectedApp])
                            }
                            scrub = null
                            lockY = null
                            scrubLetter = null
                        },
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

                AnimatedVisibility(
                    visible = showPopup,
                    enter = fadeIn(tween(120)) +
                        scaleIn(spring(dampingRatio = 0.72f, stiffness = 520f), 0.88f, TransformOrigin(1f, 0.5f)),
                    exit = fadeOut(tween(140)) + scaleOut(tween(160), 0.94f, TransformOrigin(1f, 0.5f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(-64.dp.roundToPx(), popupTop.roundToInt()) },
                ) {
                    LetterPopup(popupLetter.orEmpty(), popupApps, selectedApp)
                }

                // V otevřeném seznamu stačí nenápadné písmeno u prstu, seznam sám skáče.
                if (drawerOpen && scrub != null && popupLetter != null) {
                    LetterBubble(
                        popupLetter,
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset {
                                IntOffset(-88.dp.roundToPx(), ((scrub?.y ?: 0f) - 32.dp.toPx()).roundToInt())
                            },
                    )
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
