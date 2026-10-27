package com.mousike.app.data.remote

import com.mousike.app.data.model.Track

/** Reorders catalog candidates only. Never searches again or changes the tapped video ID. */
object MusicPreference {
    private fun priority(track: Track)=when(track.musicVideoType) {
        "MUSIC_VIDEO_TYPE_ATV"->0 // Official audio/album recording
        "MUSIC_VIDEO_TYPE_OMV"->1 // Official music video
        "MUSIC_VIDEO_TYPE_UGC"->3
        else->if(track.artist.endsWith(" - Topic"))0 else 2
    }
    fun order(tracks: List<Track>,enabled: Boolean)=if(enabled)tracks.sortedBy(::priority)else tracks
}
