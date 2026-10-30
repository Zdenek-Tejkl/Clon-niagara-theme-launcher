package cz.tejkl.klid

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaState(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
)

/** Sleduje aktivní přehrávání. Funguje jen s povoleným přístupem k notifikacím. */
class MediaHub(context: Context) {
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, NotificationService::class.java)
    private var controller: MediaController? = null

    private val _state = MutableStateFlow<MediaState?>(null)
    val state: StateFlow<MediaState?> = _state.asStateFlow()

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = pick(currentSessions())
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it.orEmpty()) }

    fun start() {
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            pick(currentSessions())
        } catch (_: SecurityException) {
            _state.value = null
        }
    }

    fun stop() {
        sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun currentSessions(): List<MediaController> = try {
        sessionManager.getActiveSessions(listenerComponent)
    } catch (_: SecurityException) {
        emptyList()
    }

    private fun pick(sessions: List<MediaController>) {
        val chosen = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.metadata != null }
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = chosen
            chosen?.registerCallback(callback)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        if (c == null || meta == null) {
            _state.value = null
            return
        }
        val art: Bitmap? = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
        _state.value = MediaState(
            title = meta.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            art = art?.asImageBitmap(),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()

    fun open() {
        try {
            controller?.sessionActivity?.send()
        } catch (_: Exception) {
        }
    }
}
