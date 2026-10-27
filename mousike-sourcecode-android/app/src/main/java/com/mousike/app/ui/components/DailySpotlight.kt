package com.mousike.app.ui.components

import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mousike.app.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Presentation-only frequency cap. No ad SDK, network request or player interaction. */
object DailySpotlightGate {
    fun due(prefs: SharedPreferences,day: LocalDate)=prefs.getString("last_shown",null)!=day.toString()
    fun shown(prefs: SharedPreferences,day: LocalDate){prefs.edit().putString("last_shown",day.toString()).apply()}
}

@Composable fun DailyFeatureSpotlight(enabled: Boolean,onCustomise: ()->Unit) {
    val context=LocalContext.current
    val store=remember(context){context.getSharedPreferences("mousike_feature_spotlight",0)}
    var visible by remember {mutableStateOf(false)}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    var foreground by remember {mutableIntStateOf(0)}
    DisposableEffect(lifecycle) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event ->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)foreground++}
        lifecycle.lifecycle.addObserver(observer);onDispose {lifecycle.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(enabled,foreground) {
        if(!enabled)visible=false
        if(enabled && DailySpotlightGate.due(store,LocalDate.now())) {
            delay(1300)
            DailySpotlightGate.shown(store,LocalDate.now());visible=true
        }
    }
    if(visible && enabled)FeatureSpotlightDialog(onDismiss={visible=false},onCustomise={visible=false;onCustomise()})
}

@Composable fun FeatureSpotlightDialog(onDismiss: ()->Unit,onCustomise: ()->Unit) {
    val reduce=LocalAppearance.current.reduceMotion
    val maxCardHeight=LocalConfiguration.current.screenHeightDp.dp*.88f
    var entered by remember {mutableStateOf(false)}
    LaunchedEffect(Unit){entered=true}
    var closing by remember {mutableStateOf(false)}
    var customise by remember {mutableStateOf(false)}
    fun close(openSettings: Boolean=false){if(!closing){customise=openSettings;closing=true;entered=false}}
    val entrance=if(reduce)rememberUpdatedState(if(entered)1f else 0f) else animateFloatAsState(if(entered)1f else 0f,tween(if(closing)180 else 380,easing=FastOutSlowInEasing),label="spotlight_entrance")
    LaunchedEffect(closing){if(closing){delay(if(reduce)0 else 180);if(customise)onCustomise() else onDismiss()}}
    Dialog(onDismissRequest={close()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Box(Modifier.fillMaxWidth().padding(20.dp).graphicsLayer {alpha=entrance.value;scaleX=.94f+.06f*entrance.value;scaleY=scaleX;translationY=(1f-entrance.value)*12.dp.toPx()},contentAlignment=Alignment.Center) {
                Column(Modifier.widthIn(max=420.dp).fillMaxWidth().heightIn(max=maxCardHeight).clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF412B30),Color(0xFF21191D),Color(0xFF11151D))))
                    .border(1.dp,Color.White.copy(alpha=.13f),RoundedCornerShape(30.dp))
                    .verticalScroll(rememberScrollState()).padding(22.dp).testTag("daily_spotlight")) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        MousikeLogo(Modifier.size(40.dp));Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {Text("Mousiké",color=Color.White,fontFamily=Manrope,fontWeight=FontWeight.Bold,fontSize=13.sp,letterSpacing=2.sp);Text("Sound In motion",color=Color(0xFFA3C5B7),fontSize=10.sp)}
                        IconButton({close()},Modifier.size(48.dp).testTag("spotlight_close")){Icon(Icons.Default.Close,"Close feature spotlight",tint=Color.White)}
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("A LITTLE MORE FREEDOM",color=Color(0xFFFFB1A7),fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.8.sp)
                    Text("Your music.\nYour rules.",fontFamily=Manrope,fontWeight=FontWeight.ExtraBold,fontSize=38.sp,lineHeight=42.sp,color=Color.White,modifier=Modifier.padding(top=8.dp,bottom=10.dp))
                    Text("All the ways to make Mousiké yours.",fontSize=14.sp,color=Color(0xFFB9C9C3),lineHeight=21.sp)
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        SpotlightBenefit(Icons.Default.Headphones,"Ad-free listening","More music, fewer interruptions",Modifier.weight(1f))
                        SpotlightBenefit(Icons.Default.GraphicEq,"Quality that fits","Super high, High or Low",Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        SpotlightBenefit(Icons.Default.Palette,"Make your move","Your colours, your look",Modifier.weight(1f))
                        SpotlightBenefit(Icons.Default.Download,"Take it with you","Downloads for offline days",Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(22.dp))
                    Button({close(true)},Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("spotlight_customise"),shape=CircleShape,
                        colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFFFB1A7),contentColor=Color(0xFF0A2116))) {
                        Text("Make it yours",fontWeight=FontWeight.Bold);Spacer(Modifier.width(8.dp));Icon(Icons.Default.ArrowForward,null,Modifier.size(18.dp))
                    }
                    TextButton({close()},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("spotlight_cancel")){Text("Keep listening",color=Color(0xFFBDCCC5),fontSize=13.sp)}
                }
            }
    }
}

@Composable private fun SpotlightBenefit(icon: ImageVector,title: String,subtitle: String,modifier: Modifier) {
    Column(modifier.background(Color.White.copy(alpha=.055f),RoundedCornerShape(16.dp)).padding(12.dp)) {
        Icon(icon,null,Modifier.size(24.dp),tint=Color(0xFFFFB1A7));Spacer(Modifier.height(10.dp))
        Text(title,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=12.sp,lineHeight=17.sp)
        Text(subtitle,color=Color(0xFFA8BDB4),fontSize=10.sp,lineHeight=15.sp,modifier=Modifier.padding(top=3.dp))
    }
}
