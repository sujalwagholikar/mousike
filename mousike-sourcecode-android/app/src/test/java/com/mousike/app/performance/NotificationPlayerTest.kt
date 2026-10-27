package androidx.media3.session

import android.app.Notification
import android.app.Service
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultActionFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.google.common.collect.ImmutableList
import com.mousike.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verify a real Media3 notification, without a network request or emulator boot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NotificationPlayerTest {
    class Host: Service() {override fun onBind(intent: android.content.Intent?)=null}
    @Test fun notificationContainsSessionMetadataAndPreviousPlayNextControls() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val host=Robolectric.buildService(Host::class.java).create().get()
        val player=ExoPlayer.Builder(context).build()
        val session=MediaSession.Builder(context,player).build()
        try {
            fun item(id: String)=MediaItem.Builder().setMediaId(id).setUri("asset:///demo/soundcheck-1.wav")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Your music").setArtist("Mousike").build()).build()
            player.setMediaItems(listOf(item("one"),item("two"),item("three")),1,0)
            val provider=DefaultMediaNotificationProvider.Builder(context).setNotificationId(7001)
                .setChannelId("mousike_playback").setChannelName(R.string.playback_channel_name).build()
            val notification=provider.createNotification(session,ImmutableList.of(),DefaultActionFactory(host),{}).notification
            assertEquals("Your music",notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals("Mousike",notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertNotNull(notification.extras.getParcelable<android.os.Parcelable>(Notification.EXTRA_MEDIA_SESSION))
            assertTrue(notification.actions.size>=3)
            assertEquals(3,notification.extras.getIntArray("android.compactActions")!!.size)
        } finally {session.release();player.release();host.onDestroy()}
    }
}
