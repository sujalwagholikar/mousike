package com.mousike.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.mousike.app.ui.theme.*
import com.mousike.app.ui.viewmodel.MousikeViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(vm: MousikeViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var languages by remember { mutableStateOf(setOf("Hindi","English")) }
    var genres by remember { mutableStateOf(setOf("Indie","Pop")) }
    var artists by remember { mutableStateOf(emptySet<String>()) }
    androidx.activity.compose.BackHandler(enabled=page>0) { page-- }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MousikeGreen.copy(alpha=.12f),ObsidianDark,ObsidianDark)))
        .windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(28.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("Mousiké",color=MousikeGreen,letterSpacing=3.sp,fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Black,fontSize=14.sp)
            TextButton(onClick={vm.completeOnboarding("Listener",languages,genres,artists)},modifier=Modifier.testTag("onboarding_skip")) { Text("Skip",color=TextSecondary) }
        }
        Spacer(Modifier.height(32.dp))
        com.mousike.app.ui.components.MousikeLogo(Modifier.size(94.dp))
        Spacer(Modifier.height(36.dp))
        AnimatedContent(targetState=page,label="onboarding") { step ->
            Column {
                Text(when(step){0->"Your music.\nYour rhythm.";1->"Find your\nfrequency.";else->"A little more\nlike you."},fontSize=42.sp,lineHeight=46.sp,color=TextPrimary,fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(14.dp))
                Text(when(step){0->"Sound In motion. A home for your music, made around your taste.";1->"Choose your languages and sounds. Your mixes keep learning as you listen.";else->"Pick a few artists you love. You can change everything in Settings."},fontSize=16.sp,lineHeight=24.sp,color=TextSecondary)
                Spacer(Modifier.height(28.dp))
                if(step==0) {
                    OutlinedTextField(name,{name=it.take(40)},label={Text("What should we call you?")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("onboarding_name"),shape=RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(24.dp))
                    listOf(Icons.Default.Headphones to "Listen with the screen off",Icons.Default.AutoAwesome to "Mixes that learn your taste",Icons.Default.LockOpen to "No account required").forEach { (icon,label) ->
                        Row(Modifier.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) { Icon(icon,null,tint=MousikeGreen,modifier=Modifier.size(22.dp));Spacer(Modifier.width(14.dp));Text(label,color=TextPrimary,fontSize=14.sp) }
                    }
                } else if(step==1) {
                    Text("LANGUAGES",color=MousikeGreen,fontSize=11.sp,letterSpacing=2.sp,fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Hindi","English","Marathi","Punjabi","Tamil","Telugu","Bengali","Korean").forEach { choice ->
                        FilterChip(selected=choice in languages,onClick={languages=if(choice in languages) languages-choice else languages+choice},label={Text(choice)})
                    } }
                    Spacer(Modifier.height(20.dp));Text("YOUR SOUNDS",color=MousikeGreen,fontSize=11.sp,letterSpacing=2.sp,fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Indie","Pop","Bollywood","Hip-Hop","Lo-Fi","Electronic","Rock","Classical","Devotional").forEach { choice ->
                        FilterChip(selected=choice in genres,onClick={genres=if(choice in genres) genres-choice else genres+choice},label={Text(choice)})
                    } }
                } else {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Arijit Singh","Shreya Ghoshal","A. R. Rahman","Pritam","Atif Aslam","The Weeknd","Taylor Swift","Diljit Dosanjh","Lana Del Rey","Coldplay","Ajay-Atul","BTS").forEach { choice ->
                        FilterChip(selected=choice in artists,onClick={artists=if(choice in artists) artists-choice else artists+choice},label={Text(choice)})
                    } }
                    Spacer(Modifier.height(20.dp));Text("Gemini is optional. Your recommendations already work on this device.",fontSize=13.sp,color=TextSecondary,lineHeight=20.sp)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { repeat(3) { i -> Box(Modifier.width(if(i==page)26.dp else 7.dp).height(5.dp).clip(CircleShape).background(if(i==page)MousikeGreen else SurfaceElevated)) } }
        Spacer(Modifier.height(22.dp))
        Button(onClick={if(page<2)page++ else vm.completeOnboarding(name,languages,genres,artists)},shape=CircleShape,modifier=Modifier.fillMaxWidth().height(54.dp).testTag("onboarding_continue"),colors=ButtonDefaults.buttonColors(containerColor=MousikeGreen,contentColor=Color.Black)) {
            Text(if(page<2)"Continue"else"Build my music world",fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,fontSize=16.sp);Spacer(Modifier.width(12.dp));Icon(Icons.Default.ArrowForward,null)
        }
        Spacer(Modifier.height(18.dp));Text("Private by default. Your likes and listening history stay on your device. Optional AI sends selected music metadata to Google when enabled.",color=TextMuted,fontSize=11.sp,lineHeight=17.sp)
    }
}
