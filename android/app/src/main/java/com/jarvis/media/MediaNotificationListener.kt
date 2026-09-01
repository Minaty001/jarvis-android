package com.jarvis.media

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log

data class NowPlaying(
    val title: String?,
    val artist: String?,
    val album: String?,
    val isPlaying: Boolean,
    val packageName: String
)

class MediaNotificationListener : NotificationListenerService() {
    companion object {
        private const val TAG = "MediaNotificationListener"
        var instance: MediaNotificationListener? = null
            private set
    }

    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    var onNowPlayingChanged: ((NowPlaying?) -> Unit)? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        updateActiveSession()
        Log.d(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        instance = null
        activeController = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        updateActiveSession()
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {
        updateActiveSession()
    }

    private fun updateActiveSession() {
        val component = ComponentName(this, MediaNotificationListener::class.java)
        val controllers = sessionManager?.getActiveSessions(component)
        val controller = controllers?.firstOrNull()

        if (controller != activeController) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = controller
            activeController?.registerCallback(controllerCallback)
        }

        notifyNowPlaying()
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            notifyNowPlaying()
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            notifyNowPlaying()
        }
    }

    private fun notifyNowPlaying() {
        val controller = activeController ?: run {
            onNowPlayingChanged?.invoke(null)
            return
        }
        val metadata = controller.metadata
        val state = controller.playbackState

        onNowPlayingChanged?.invoke(
            NowPlaying(
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                isPlaying = state?.state == PlaybackState.STATE_PLAYING,
                packageName = controller.packageName
            )
        )
    }

    fun getNowPlaying(): NowPlaying? {
        val controller = activeController ?: return null
        val metadata = controller.metadata
        val state = controller.playbackState
        return NowPlaying(
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            isPlaying = state?.state == PlaybackState.STATE_PLAYING,
            packageName = controller.packageName
        )
    }

    fun play() = activeController?.transportControls?.play()
    fun pause() = activeController?.transportControls?.pause()
    fun playPause() {
        val state = activeController?.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) pause() else play()
    }
    fun next() = activeController?.transportControls?.skipToNext()
    fun previous() = activeController?.transportControls?.skipToPrevious()
    fun stop() = activeController?.transportControls?.stop()
}
