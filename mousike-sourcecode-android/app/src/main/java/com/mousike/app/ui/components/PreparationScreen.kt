package com.mousike.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousike.app.ui.theme.*

@Composable fun PreparationScreen(title: String,steps: List<String>,stage: Int,error: String?=null,onBack: (() ->Unit)?=null,onRetry: (() ->Unit)?=null) {
    val reduce=LocalAppearance.current.reduceMotion
    val accent=MousikeGreen
    val rotation=if(reduce)rememberUpdatedState(0f) else {
        val transition=rememberInfiniteTransition(label="preparation_orbit")
        val angle=transition.animateFloat(0f,360f,infiniteRepeatable(tween(18000,easing=LinearEasing)),label="orbit_angle");angle
    }
    val progress=animateFloatAsState((stage+1)/steps.size.toFloat(),tween(if(reduce)0 else 420,easing=FastOutSlowInEasing),label="assembly_progress")
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha=.09f),ObsidianDark,ObsidianDark))).windowInsetsPadding(WindowInsets.safeDrawing).testTag("preparation_screen")) {
        val hero=minOf(200.dp,maxHeight*.29f)
        Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal=26.dp,vertical=18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                if(onBack!=null)IconButton(onBack){Icon(Icons.Default.Close,"Cancel",tint=TextSecondary)}
                Text("Mousiké",style=MaterialTheme.typography.labelLarge,color=TextPrimary,letterSpacing=3.sp)
                Spacer(Modifier.weight(1f));Text("MADE FOR YOU",style=MaterialTheme.typography.labelSmall,color=accent,letterSpacing=1.sp)
            }
            Spacer(Modifier.height(28.dp))
            Box(Modifier.size(hero),contentAlignment=Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().graphicsLayer {rotationZ=rotation.value}) {
                    val radius=size.minDimension*.43f
                    drawCircle(accent.copy(alpha=.10f),radius,style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.dp.toPx()))
                    repeat(8) {i ->
                        val rad=Math.toRadians(i*45.0)
                        drawCircle(accent.copy(alpha=.18f+i*.08f),if(i==0)4.dp.toPx() else 2.dp.toPx(),androidx.compose.ui.geometry.Offset(center.x+kotlin.math.cos(rad).toFloat()*radius,center.y+kotlin.math.sin(rad).toFloat()*radius))
                    }
                }
                Box(Modifier.size(124.dp).background(accent.copy(alpha=.05f),CircleShape),contentAlignment=Alignment.Center){MousikeLogo(Modifier.size(86.dp))}
            }
            Spacer(Modifier.height(24.dp))
            Text(title.uppercase(),style=MaterialTheme.typography.labelMedium,color=TextMuted,letterSpacing=2.sp)
            Spacer(Modifier.height(14.dp))
            AnimatedContent(targetState=steps[stage.coerceIn(steps.indices)],transitionSpec={
                (androidx.compose.animation.fadeIn(tween(if(reduce)0 else 280))+androidx.compose.animation.slideInVertically(tween(if(reduce)0 else 280)){it/5}) togetherWith androidx.compose.animation.fadeOut(tween(if(reduce)0 else 150))
            },label="assembly_headline") {text ->
                Text(if(error==null)text else "Let’s try that again",style=MaterialTheme.typography.headlineMedium,color=TextPrimary,textAlign=androidx.compose.ui.text.style.TextAlign.Center,modifier=Modifier.fillMaxWidth().heightIn(min=76.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                repeat(steps.size){index ->Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if(index<=stage)accent else SurfaceElevated))}
            }
            Spacer(Modifier.height(24.dp))
            steps.forEachIndexed {index,text ->
                Row(Modifier.fillMaxWidth().padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).background(if(index<=stage)accent.copy(alpha=.14f) else SurfaceCard,CircleShape),contentAlignment=Alignment.Center) {
                        if(index<stage)Icon(Icons.Default.Check,null,Modifier.size(14.dp),tint=accent)
                        else Text("${index+1}",style=MaterialTheme.typography.labelSmall,color=if(index==stage)accent else TextMuted)
                    }
                    Spacer(Modifier.width(12.dp));Text(text,color=if(index==stage)TextPrimary else TextMuted,style=MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(20.dp))
            if(error!=null) {
                Text(error,color=TextSecondary,style=MaterialTheme.typography.bodyMedium)
                onRetry?.let {Button(it,Modifier.padding(top=12.dp)){Text("Try again")}}
            } else LinearProgressIndicator(progress={progress.value},modifier=Modifier.fillMaxWidth().height(2.dp),color=accent.copy(alpha=.6f),trackColor=SurfaceCard)
            Spacer(Modifier.height(24.dp));Text("Sound In motion. A little more like you.",color=TextMuted,style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable fun SearchLoadingPanel() {
    val reduce=LocalAppearance.current.reduceMotion
    val alpha=if(reduce).65f else {
        val transition=rememberInfiniteTransition(label="search_loading")
        val a by transition.animateFloat(.35f,.8f,infiniteRepeatable(tween(850),RepeatMode.Reverse),label="skeleton_alpha");a
    }
    Column(Modifier.fillMaxWidth().padding(20.dp).testTag("search_loading")) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=MousikeGreen)
            Spacer(Modifier.width(12.dp));Text("Finding your music",style=MaterialTheme.typography.titleMedium,color=TextPrimary)
        }
        Text("Searching songs, artists and albums",style=MaterialTheme.typography.bodySmall,color=TextMuted,modifier=Modifier.padding(top=8.dp,bottom=22.dp))
        repeat(5) {row ->
            Row(Modifier.fillMaxWidth().padding(vertical=9.dp).graphicsLayer {this.alpha=alpha},verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(SurfaceElevated,RoundedCornerShape(9.dp)))
                Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(if(row%2==0).8f else .65f).height(12.dp).background(SurfaceElevated,CircleShape))
                    Spacer(Modifier.height(9.dp));Box(Modifier.fillMaxWidth(.45f).height(9.dp).background(SurfaceElevated,CircleShape))
                }
            }
        }
    }
}
@Composable fun SearchEmptyPanel(query: String,error: String?,onRetry: ()->Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp).testTag("search_empty"),horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).background(SurfaceCard,CircleShape),contentAlignment=Alignment.Center){Icon(if(error==null)Icons.Default.Search else Icons.Default.WifiOff,null,tint=MousikeGreen,modifier=Modifier.size(28.dp))}
        Spacer(Modifier.height(18.dp));Text(if(error==null)"A different search might find it" else "Your connection needs a moment",style=MaterialTheme.typography.titleLarge,color=TextPrimary)
        Spacer(Modifier.height(10.dp));Text(error ?: "No matches for “$query” in this category. Try a song title, artist or another spelling.",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
        if(error!=null)TextButton(onRetry){Text("Retry search")}
    }
}
