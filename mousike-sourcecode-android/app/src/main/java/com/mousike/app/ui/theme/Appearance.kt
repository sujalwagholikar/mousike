package com.mousike.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.mousike.app.settings.Preferences

fun coverCorners(shape: String)=RoundedCornerShape(when(shape){"Square"->0.dp;"Soft"->26.dp;else->12.dp})
val ArtworkShape @Composable get()=coverCorners(LocalAppearance.current.coverShape)
val ComponentShape @Composable get()=RoundedCornerShape(when(LocalAppearance.current.surfaceStyle){"Flat"->6.dp;else->22.dp})

/** Presentation presets only. Playback, queue, downloads and taste are retained. */
fun Preferences.withLook(name: String)=when(name) {
    "Studio"->copy(theme="Light",accent="Coral",fontCombo="Studio",coverShape="Square",surfaceStyle="Outlined",playerLayout="Compact",albumLayout="Compact")
    "Afterhours"->copy(theme="AMOLED",accent="Lavender",fontCombo="Modern",coverShape="Soft",surfaceStyle="Soft",playerLayout="Immersive",albumLayout="Gallery")
    "Editorial"->copy(theme="Warm",accent="Amber",fontCombo="Editorial",coverShape="Square",surfaceStyle="Flat",playerLayout="Classic",albumLayout="Editorial")
    else->copy(theme="Midnight",accent="Coral",fontCombo="Signature",coverShape="Square",surfaceStyle="Soft",playerLayout="Classic",albumLayout="Editorial")
}
fun Preferences.resetAppearance()=copy(theme="Midnight",accent="Coral",fontCombo="Signature",textSize="Default",cardDensity="Comfortable",coverShape="Square",surfaceStyle="Soft",playerAmbience=true,playerLayout="Classic",playerArtworkSize="Default",playerBackground="Artwork",playerControls="Accent",showPlayerShortcuts=true,showPlayerContext=true,albumLayout="Editorial",trackSpacing="Comfortable",showTrackArtwork=true,showTrackDuration=true,showNavLabels=true,miniPlayerStyle="Floating",showMiniProgress=true,reduceMotion=false,showArtists=true,showAlbums=true,showMixes=true)
