package com.mousike.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mousike.app.MainActivity
import com.mousike.app.R

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService: MediaSessionService() {
    private var session: MediaSession?=null
    override fun onCreate() {
        super.onCreate()
        val notifications=DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(7001).setChannelId("mousike_playback")
            .setChannelName(R.string.playback_channel_name).build().apply {setSmallIcon(R.drawable.ic_music_note)}
        setMediaNotificationProvider(notifications)
        val activity=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java)
            .putExtra("open_player",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session=MediaSession.Builder(this,AudioPlayerEngine.getInstance(this).player).setSessionActivity(activity).build()
        addSession(session!!)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    // MediaSessionService owns the MediaStyle notification and foreground lifecycle.
    // A second plain startForeground notification with the same ID overwrote its controls.
    override fun onTaskRemoved(rootIntent: Intent?) {
        if(!AudioPlayerEngine.getInstance(this).player.playWhenReady) stopSelf()
    }
    override fun onDestroy() {
        session?.release(); session=null
        super.onDestroy()
    }
}
