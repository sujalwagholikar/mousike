package com.mousike.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.mousike.app.settings.Preferences
import com.mousike.app.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun AppearanceStudio(p: Preferences,update: (Preferences)->Unit) {
    SettingsCategoryHeader("YOUR LOOK, YOUR WAY")
    SettingsPanel {
        Text("Start with a mood",style=MaterialTheme.typography.titleLarge,color=TextPrimary)
        Text("A complete look in one tap. Fine-tune every detail below.",style=MaterialTheme.typography.bodySmall,color=TextSecondary,modifier=Modifier.padding(top=6.dp))
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Signature","Studio","Afterhours","Editorial").forEach {look->
                AssistChip(onClick={update(p.withLook(look))},label={Text(look)},leadingIcon={Icon(Icons.Default.AutoAwesome,null,Modifier.size(15.dp))})
            }
        }
        Row(Modifier.fillMaxWidth().padding(top=12.dp).background(MousikeGreen.copy(alpha=.09f),ComponentShape).padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(ArtworkShape).background(Brush.linearGradient(listOf(MousikeGreen,Color(0xFF675880)))),contentAlignment=Alignment.Center) {Icon(Icons.Default.GraphicEq,null,tint=Color(0xFF18181B),modifier=Modifier.size(32.dp))}
            Column(Modifier.padding(start=14.dp).weight(1f)) {Text("Your next favourite",fontFamily=Manrope,fontWeight=FontWeight.Bold,color=TextPrimary,fontSize=18.sp);Text("A little more you.",fontFamily=Inter,color=TextSecondary,fontSize=12.sp,modifier=Modifier.padding(top=4.dp))}
        }
        Choice("Theme",listOf("Midnight","AMOLED","Light","Warm","System"),p.theme){update(p.copy(theme=it))}
        Choice("Accent",listOf("Coral","Green","Lavender","Sky","Rose","Amber","Mint"),p.accent){update(p.copy(accent=it))}
        Choice("Surface style",listOf("Soft","Flat","Outlined"),p.surfaceStyle){update(p.copy(surfaceStyle=it))}
        Choice("Cover shape",listOf("Square","Rounded","Soft"),p.coverShape){update(p.copy(coverShape=it))}
        Choice("Text size",listOf("Small","Default","Large"),p.textSize){update(p.copy(textSize=it))}
        Choice("Card density",listOf("Compact","Comfortable","Spacious"),p.cardDensity){update(p.copy(cardDensity=it))}
    }
    SettingsCategoryHeader("TYPE THAT FEELS LIKE YOU")
    SettingsPanel {
        Text("Six Google Fonts combinations",style=MaterialTheme.typography.titleMedium,color=TextPrimary)
        Text("Bundled in the app. No font download or restart needed.",style=MaterialTheme.typography.bodySmall,color=TextSecondary,modifier=Modifier.padding(top=6.dp,bottom=12.dp))
        FontCombinations.forEach {(name,line)->
            val fonts=remember(name){fontsFor(name)}
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(p.fontCombo==name)MousikeGreen.copy(alpha=.10f) else Color.Transparent).clickable {update(p.copy(fontCombo=name))}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Text(name,fontFamily=fonts.heading,color=TextPrimary,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(line,fontFamily=fonts.body,color=TextSecondary,fontSize=11.sp,modifier=Modifier.padding(top=4.dp))}
                RadioButton(selected=p.fontCombo==name,onClick={update(p.copy(fontCombo=name))})
            }
        }
    }
    SettingsCategoryHeader("HOME & ATMOSPHERE")
    SettingsPanel {
        SettingsToggleRow("Artwork ambience","Cover-inspired colour on detail screens",p.playerAmbience){update(p.copy(playerAmbience=it))}
        SettingsToggleRow("Daily mixes","Your personal soundtrack on Home",p.showMixes){update(p.copy(showMixes=it))}
        SettingsToggleRow("Artist shelf","Artists you'll love",p.showArtists){update(p.copy(showArtists=it))}
        SettingsToggleRow("Album shelf","Albums and long-play journeys",p.showAlbums){update(p.copy(showAlbums=it))}
        TextButton(onClick={update(p.resetAppearance())}){Text("Restore coral & square defaults")}
    }
}

@Composable fun ComponentStudio(p: Preferences,update: (Preferences)->Unit) {
    SettingsCategoryHeader("EXPANDED PLAYER")
    SettingsPanel {
        Text("Make room for your music",style=MaterialTheme.typography.titleLarge,color=TextPrimary)
        Text("All layouts keep the same queue and playback controls.",style=MaterialTheme.typography.bodySmall,color=TextSecondary,modifier=Modifier.padding(top=6.dp))
        Choice("Player layout",listOf("Classic","Immersive","Compact"),p.playerLayout){update(p.copy(playerLayout=it))}
        Choice("Artwork size",listOf("Small","Default","Large"),p.playerArtworkSize){update(p.copy(playerArtworkSize=it))}
        Choice("Player background",listOf("Artwork","Solid","Accent"),p.playerBackground){update(p.copy(playerBackground=it))}
        Choice("Play button",listOf("Accent","White"),p.playerControls){update(p.copy(playerControls=it))}
        SettingsToggleRow("Playing-from context","Album name above your artwork",p.showPlayerContext){update(p.copy(showPlayerContext=it))}
        SettingsToggleRow("Quick actions","Lyrics, queue and download shortcuts",p.showPlayerShortcuts){update(p.copy(showPlayerShortcuts=it))}
        Text("Quick actions also remain in the player menu. Artwork is always shown in full.",style=MaterialTheme.typography.bodySmall,color=TextMuted)
    }
    SettingsCategoryHeader("ALBUMS & SONGS")
    SettingsPanel {
        Choice("Album opener",listOf("Editorial","Gallery","Compact"),p.albumLayout){update(p.copy(albumLayout=it))}
        Choice("Song spacing",listOf("Compact","Comfortable","Spacious"),p.trackSpacing){update(p.copy(trackSpacing=it))}
        SettingsToggleRow("Song artwork","Covers beside song titles",p.showTrackArtwork){update(p.copy(showTrackArtwork=it))}
        SettingsToggleRow("Song duration","Time beside each track",p.showTrackDuration){update(p.copy(showTrackDuration=it))}
    }
    SettingsCategoryHeader("NAVIGATION & MINI PLAYER")
    SettingsPanel {
        Choice("Mini player",listOf("Floating","Edge"),p.miniPlayerStyle){update(p.copy(miniPlayerStyle=it))}
        SettingsToggleRow("Mini-player progress","A discreet playback progress line",p.showMiniProgress){update(p.copy(showMiniProgress=it))}
        SettingsToggleRow("Navigation labels","Names beneath the navigation icons",p.showNavLabels){update(p.copy(showNavLabels=it))}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Choice(title: String,choices: List<String>,selected: String,select: (String)->Unit) {
    Text(title,style=MaterialTheme.typography.titleSmall,color=TextPrimary,modifier=Modifier.padding(top=16.dp,bottom=4.dp))
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        choices.forEach {value->FilterChip(selected=selected==value,onClick={select(value)},label={Text(value)},leadingIcon=if(selected==value){{Icon(Icons.Default.Check,null,Modifier.size(14.dp))}}else null)}
    }
}
