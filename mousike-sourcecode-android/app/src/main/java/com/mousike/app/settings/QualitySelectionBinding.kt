package com.mousike.app.settings

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

/** Standard Media3 settings adapter. Changes apply at the next item, leaving current audio alone. */
class QualitySelectionBinding(private val player: Player,private val preferences: StateFlow<Preferences>): Player.Listener {
    init {player.addListener(this);if(player.currentMediaItem==null)apply()}
    override fun onMediaItemTransition(mediaItem: MediaItem?,reason: Int) {apply()}
    private fun apply() {
        val quality=preferences.value.audioQuality
        player.trackSelectionParameters=player.trackSelectionParameters.buildUpon()
            .setMaxAudioBitrate(AudioQualityPolicy.maxBitrate(quality)).build()
    }
    fun close(){player.removeListener(this)}
}
