package com.mousike.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.mousike.app.data.model.Track
import com.mousike.app.data.repository.RecommendationResult
import com.mousike.app.ui.theme.*

data class PersonalMix(val id: String,val title: String,val line: String,val words: String,val color: Long,val number: String)
val PersonalMixes=listOf(
    PersonalMix("daily-1","Daily Mix 1","Your favourites, in good company","DAILY\nMIX",0xFF6B4DCC,"01"),
    PersonalMix("daily-2","Daily Mix 2","A fresh side of your sound","DAILY\nMIX",0xFF087D71,"02"),
    PersonalMix("english","English Mix","Your English-language rotation","ENGLISH\nMIX",0xFF3665B4,"03"),
    PersonalMix("bollywood","Bollywood Punch","Hindi hits with a little more energy","BOLLYWOOD\nPUNCH",0xFFB44A39,"04"),
    PersonalMix("indie","Indie Afterhours","Independent sounds, your way","INDIE\nAFTERHOURS",0xFF94588E,"05"),
    PersonalMix("chill","Soft Focus","Acoustic, mellow and lo-fi moments","SOFT\nFOCUS",0xFF3B807C,"06"),
    PersonalMix("repeat","On Repeat","The songs you come back to","ON\nREPEAT",0xFFA06B28,"07"),
    PersonalMix("discovery","Discovery Dose","A little outside your usual orbit","DISCOVERY\nDOSE",0xFF574DB0,"08")
)
fun personalMixForAlbum(id: String)=when(id){"personal-daily-mix-1"->PersonalMixes[0];"personal-daily-mix-2"->PersonalMixes[1];else->PersonalMixes.firstOrNull {id=="personal-collection-${it.id}"}}

/** A presentation of existing ranked recommendations; no extraction/ranking/AI service is changed. */
fun personalMixTracks(mix: PersonalMix,result: RecommendationResult,catalog: List<Track>): List<Track> {
    val ranked=(result.madeForYou+result.dailyMix1+result.dailyMix2+result.discoverWeekly+result.heavyRotation+catalog).distinctBy {it.id}
    fun Track.has(vararg terms: String)=terms.any {term->genre.contains(term,true)||mood.contains(term,true)}
    val selected=when(mix.id) {
        "daily-1"->result.dailyMix1
        "daily-2"->result.dailyMix2
        "english"->ranked.filter {it.language.equals("English",true) && !it.has("Bollywood","Hindi")}
        "bollywood"->ranked.filter {it.language.equals("Hindi",true)||it.has("Bollywood","Hindi")}
        "indie"->ranked.filter {it.has("Indie","Alternative","Rock")}
        "chill"->ranked.filter {it.has("Lo-Fi","Lofi","Acoustic","Ambient","Chill","Mellow")}
        "repeat"->(result.heavyRotation+result.madeForYou).distinctBy {it.id}
        else->(result.discoverWeekly+result.madeForYou).distinctBy {it.id}
    }.filter {it.endMs!=null || it.durationSec in 1..480}.distinctBy {it.id}
    val counts=HashMap<String,Int>()
    return selected.filter {t->val key=t.artist.lowercase();val n=counts[key] ?: 0;if(n>=3)false else {counts[key]=n+1;true}}.take(24)
}

@Composable fun PersonalMixCard(mix: PersonalMix,tracks: List<Track>,onClick: ()->Unit) {
    Column(Modifier.width(176.dp).clickable(onClick=onClick).testTag("build_${mix.title.replace(" ","_").lowercase()}")) {
        PersonalMixCover(mix,tracks,Modifier.size(176.dp))
        Text(mix.title,color=TextPrimary,fontFamily=Manrope,fontWeight=FontWeight.Bold,fontSize=14.sp,modifier=Modifier.padding(top=10.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(mix.line,color=TextMuted,fontSize=11.sp,lineHeight=16.sp,maxLines=2,modifier=Modifier.padding(top=4.dp))
    }
}

@Composable fun PersonalMixCover(mix: PersonalMix,tracks: List<Track>,modifier: Modifier=Modifier) {
    val base=Color(mix.color)
    BoxWithConstraints(modifier.clip(ArtworkShape).background(Brush.linearGradient(listOf(base,base.copy(red=base.red*.40f,green=base.green*.40f,blue=base.blue*.45f))))) {
        val edge=maxWidth
        Canvas(Modifier.fillMaxSize()) {
            repeat(5){i->drawCircle(Color.White.copy(alpha=.06f),size.width*(.32f+i*.16f),Offset(size.width*.88f,size.height*.16f),style=androidx.compose.ui.graphics.drawscope.Stroke(size.width*.025f))}
            drawCircle(Color.White.copy(alpha=.12f),size.width*.30f,Offset(size.width*.06f,size.height*.92f))
        }
        Row(Modifier.fillMaxWidth().padding(edge*.075f),verticalAlignment=Alignment.CenterVertically) {
            MousikeLogo(Modifier.size(edge*.12f));Spacer(Modifier.width(5.dp));Text("Mousiké",color=Color.White.copy(alpha=.9f),fontWeight=FontWeight.Bold,fontSize=(edge.value*.045f).sp,letterSpacing=1.sp)
            Spacer(Modifier.weight(1f));Icon(Icons.Default.AutoAwesome,null,tint=Color.White.copy(alpha=.8f),modifier=Modifier.size(edge*.09f))
        }
        Text(mix.number,color=Color.White.copy(alpha=.13f),fontFamily=Manrope,fontWeight=FontWeight.ExtraBold,fontSize=(edge.value*.42f).sp,modifier=Modifier.align(Alignment.TopEnd).padding(top=edge*.14f,end=edge*.04f))
        val art=tracks.distinctBy {it.artist}.take(3)
        Row(Modifier.align(Alignment.CenterEnd).padding(end=edge*.075f).offset(y=-edge*.015f),horizontalArrangement=Arrangement.spacedBy(-edge*.12f)) {
            art.forEachIndexed {index,t->Box(Modifier.size(edge*.35f).graphicsLayer {rotationZ=(index-1)*10f}.clip(RoundedCornerShape(edge*.045f)).background(base)) {
                MusicArtwork(t.coverUrl,t.artist,Modifier.fillMaxSize().padding(3.dp),ContentScale.Fit)
            }}
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(edge*.52f).background(Brush.verticalGradient(listOf(Color.Transparent,base.copy(red=base.red*.33f,green=base.green*.33f,blue=base.blue*.35f)))))
        Text(mix.words,color=Color.White,fontFamily=Manrope,fontWeight=FontWeight.ExtraBold,fontSize=(edge.value*(if(mix.words.lines().maxOf {it.length}>=9) .11f else .15f)).sp,lineHeight=(edge.value*.16f).sp,letterSpacing=(-.7).sp,
            modifier=Modifier.align(Alignment.BottomStart).padding(edge*.075f))
    }
}
