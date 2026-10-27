package com.mousike.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import com.mousike.app.ui.theme.*
import com.mousike.app.ui.viewmodel.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(viewModel: MousikeViewModel,modifier: Modifier=Modifier) {
    val p by viewModel.preferences.collectAsState(); val playback by viewModel.browsingPlaybackState.collectAsState()
    val context=LocalContext.current
    var section by rememberSaveable {mutableStateOf("")}
    var showSpotlight by remember {mutableStateOf(false)}
    BackHandler(enabled=section.isNotBlank()){section=""}
    var hasSavedKey by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) {hasSavedKey=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){viewModel.settings.apiKey().isNotBlank()}}
    var key by remember { mutableStateOf("") };var model by remember(p.model) { mutableStateOf(p.model) }
    var name by remember(p.name) { mutableStateOf(p.name) };var artists by remember(p.artists) { mutableStateOf(p.artists.joinToString(", ")) }
    var clear by remember { mutableStateOf(false) };var export by remember { mutableStateOf("") }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(export.toByteArray()) } }
            .onSuccess { viewModel.showSnackbar("Backup saved") }.onFailure { viewModel.showSnackbar("Could not save backup") }
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
            .onSuccess { text -> if(text!=null && text.length<5_000_000) viewModel.importData(text) else viewModel.showSnackbar("Choose a JSON backup under 5 MB") }
            .onFailure { viewModel.showSnackbar("Could not read backup") }
    }
    Crossfade(targetState=section,animationSpec=tween(if(p.reduceMotion)0 else 180),label="settings_section") {activeSection ->
    LazyColumn(modifier.fillMaxSize().background(ObsidianDark).testTag("settings_list"),contentPadding=PaddingValues(20.dp,12.dp,20.dp,28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            if(activeSection.isBlank()) {
                Text("Settings",color=TextPrimary,fontFamily=Manrope,fontWeight=FontWeight.ExtraBold,fontSize=30.sp)
                Text("A space that sounds like you.",color=TextMuted,fontSize=13.sp,modifier=Modifier.padding(top=4.dp,bottom=22.dp))
                Row(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(MousikeGreen.copy(alpha=.14f),SurfaceCard)),RoundedCornerShape(24.dp)).clickable {section="personal"}.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).background(MousikeGreen.copy(alpha=.14f),CircleShape),contentAlignment=Alignment.Center) {Text(p.name.take(1).uppercase(),fontFamily=Manrope,fontWeight=FontWeight.Bold,fontSize=23.sp,color=MousikeGreen)}
                    Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)) {Text(p.name,color=TextPrimary,fontFamily=Manrope,fontWeight=FontWeight.Bold,fontSize=19.sp);Text("Your listening identity",color=TextSecondary,fontSize=12.sp,modifier=Modifier.padding(top=4.dp))}
                    Icon(Icons.Default.ChevronRight,null,tint=TextMuted)
                }
                Spacer(Modifier.height(12.dp))
            } else {
                Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton({section=""},Modifier.testTag("settings_back")){Icon(Icons.Default.ArrowBack,"Back to Settings",tint=TextPrimary)}
                    Text(settingsSections.first {it.id==activeSection}.title,color=TextPrimary,fontFamily=Manrope,fontWeight=FontWeight.Bold,fontSize=23.sp,modifier=Modifier.weight(1f))
                }
            }
        }
        if(activeSection.isBlank()) {
            item {SettingsCategoryHeader("YOUR EXPERIENCE")}
            settingsSections.forEach {entry -> item(key=entry.id) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceCard).clickable {section=entry.id}.testTag("settings_section_${entry.id}").padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(MousikeGreen.copy(alpha=.09f),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Icon(entry.icon,null,tint=MousikeGreen,modifier=Modifier.size(22.dp))}
                    Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f).padding(vertical=6.dp)) {Text(entry.title,color=TextPrimary,fontWeight=FontWeight.SemiBold,fontSize=15.sp);Text(entry.subtitle,color=TextMuted,fontSize=11.sp,lineHeight=16.sp,modifier=Modifier.padding(top=3.dp))}
                    Icon(Icons.Default.ChevronRight,null,tint=TextMuted,modifier=Modifier.size(19.dp))
                }
            }}
        }
        if(activeSection=="personal") item {
            SettingsCategoryHeader("YOUR LISTENING TASTE")
            SettingsPanel {
                OutlinedTextField(name,{name=it.take(40)},label={Text("Your name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp));OutlinedTextField(artists,{artists=it.take(500)},label={Text("Favorite artists, separated by commas")},modifier=Modifier.fillMaxWidth())
                TextButton(onClick={viewModel.updatePreferences { it.copy(name=name.ifBlank { "Listener" },artists=artists.split(',').map { s->s.trim() }.filter { s->s.isNotBlank() }.toSet()) };viewModel.showSnackbar("Profile updated")}) { Text("Save profile") }
                Text("Languages",color=TextPrimary)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Hindi","English","Marathi","Punjabi","Tamil","Telugu","Bengali","Korean").forEach { choice -> FilterChip(choice in p.languages,{viewModel.updatePreferences { it.copy(languages=if(choice in it.languages)it.languages-choice else it.languages+choice) }},{Text(choice)}) } }
                Spacer(Modifier.height(10.dp));Text("Sounds",color=TextPrimary)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Indie","Pop","Bollywood","Lo-Fi","Rock","Hip-Hop","Classical","Electronic").forEach { choice -> FilterChip(choice in p.genres,{viewModel.updatePreferences { it.copy(genres=if(choice in it.genres)it.genres-choice else it.genres+choice) }},{Text(choice)}) } }
                Text("Discovery • ${(p.discovery*100).toInt()}% exploration",color=TextPrimary,modifier=Modifier.padding(top=12.dp))
                Slider(p.discovery,{v->viewModel.updatePreferences { it.copy(discovery=v) }},valueRange=0f..1f)
                Text("Likes, listening time, skips and feedback tune your local ranking. Artist diversity and repeat penalties shape Play Next.",fontSize=12.sp,color=TextSecondary,lineHeight=18.sp)
            }
        }
        if(activeSection=="appearance") item { AppearanceStudio(p) {value->viewModel.updatePreferences {value}} }
        if(activeSection=="interface") item { ComponentStudio(p) {value->viewModel.updatePreferences {value}} }
        if(activeSection=="playback") item {
            SettingsCategoryHeader("PLAYBACK")
            SettingsPanel {
                SettingsToggleRow("Smart Play Next","Continue with music shaped by your activity",p.autoplay) { b->viewModel.updatePreferences { it.copy(autoplay=b) } }
                SettingsToggleRow("Detect long albums","Open track discovery for videos longer than 8 minutes",p.detectAlbums) { b->viewModel.updatePreferences { it.copy(detectAlbums=b) } }
                Text("Streaming quality",color=TextPrimary)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Super high","High","Low").forEach { choice -> FilterChip(p.audioQuality==choice,{viewModel.updatePreferences { it.copy(audioQuality=choice) }},{Text(choice)}) } }
                Text("Super high: best available source • High: target up to 160 kbps • Low: target up to 64 kbps. Availability and compatibility fallbacks determine the actual bitrate. Applies on the next song; current playback continues.",style=MaterialTheme.typography.bodySmall,color=TextSecondary,lineHeight=18.sp)
                SettingsToggleRow("Prefer YouTube Music songs","Music-catalog recordings first in search and discovery",p.musicFirst){b->viewModel.updatePreferences {it.copy(musicFirst=b)}}
                Text("Playback speed",color=TextPrimary)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(.75f,1f,1.25f,1.5f,2f).forEach { speed -> FilterChip(playback.playbackSpeed==speed,{viewModel.setPlaybackSpeed(speed)},{Text("${speed}×")}) } }
                SettingsClickableRow(Icons.Default.Equalizer,"Equalizer",playback.equalizerPreset,{viewModel.setEqualizerDialogVisible(true)})
                SettingsClickableRow(Icons.Default.Schedule,"Sleep timer",if(playback.sleepTimerMinutes==null)"Off" else "${playback.sleepTimerRemainingSec/60}m remaining",{viewModel.setSleepTimerDialogVisible(true)})
            }
        }
        if(activeSection=="downloads") item {
            SettingsCategoryHeader("OFFLINE LISTENING")
            SettingsPanel {
                SettingsToggleRow("Wi-Fi downloads only","Wait for an unmetered connection",p.wifiDownloads) { b->viewModel.updatePreferences { it.copy(wifiDownloads=b) } }
                SettingsClickableRow(Icons.Default.Download,"Downloads","Manage offline songs and progress",{viewModel.navigateTo(NavScreen.DOWNLOADS)})
                SettingsClickableRow(Icons.Default.Headphones,"Offline sound check","Try original bundled demo tracks",{viewModel.loadSoundCheck()})
            }
        }
        if(activeSection=="display") item {
            SettingsCategoryHeader("SMOOTHNESS & RESPONSE")
            SettingsPanel {
                SettingsToggleRow("Reduce motion","Gentle transitions and a static visualizer",p.reduceMotion) {b->viewModel.updatePreferences {it.copy(reduceMotion=b)}}
                com.mousike.app.ui.components.RefreshRateControls(p.refreshRate) {value->viewModel.updatePreferences {it.copy(refreshRate=value)}}
                SettingsToggleRow("Prepare upcoming songs","Warm one audio connection without downloading music",p.preloadNext) {value->viewModel.updatePreferences {it.copy(preloadNext=value)}}
                Text("Animations follow your display rate. Use Reduce motion in Appearance for gentler transitions.",style=MaterialTheme.typography.bodySmall,color=TextSecondary)
            }
        }
        if(activeSection=="ai") item {
            SettingsCategoryHeader("OPTIONAL GEMINI BOOST")
            SettingsPanel {
                Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome,null,tint=MousikeGreen);Spacer(Modifier.width(10.dp));Text("A smarter second opinion",fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,color=TextPrimary) }
                SettingsToggleRow("Enable AI boost","Music keeps working without a key",p.aiEnabled) { b->viewModel.updatePreferences { it.copy(aiEnabled=b) } }
                Text("When enabled, song titles, artists and selected preferences go to Google to rerank existing candidates. No invented songs. API usage is subject to Google's quota and pricing.",fontSize=12.sp,lineHeight=18.sp,color=TextSecondary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(key,{key=it},label={Text("Gemini API key")},placeholder={Text(if(!hasSavedKey) "Enter your key" else "Key saved securely")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("gemini_key"))
                Spacer(Modifier.height(10.dp));OutlinedTextField(model,{model=it},label={Text("Model ID")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("gemini_model"))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={if(key.isNotBlank()) {viewModel.saveGeminiKey(key);hasSavedKey=true;key=""};viewModel.updatePreferences { it.copy(model=model) }}) {Text("Save")}
                    OutlinedButton(onClick={viewModel.checkGemini()}) {Text("Test connection")}
                }
                TextButton(onClick={viewModel.saveGeminiKey("");hasSavedKey=false;viewModel.updatePreferences { it.copy(aiEnabled=false) }}) { Text("Remove saved API key") }
                TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://aistudio.google.com/apikey")))}) { Text("Get a key from Google AI Studio") }
            }
        }
        if(activeSection=="data") item {
            SettingsCategoryHeader("YOUR DATA")
            SettingsPanel {
                SettingsClickableRow(Icons.Default.CloudDownload,"Export library","Save likes, playlists and full chapter metadata",{viewModel.exportData {json->export=json;exporter.launch("Mousiké-backup.json")} })
                SettingsClickableRow(Icons.Default.CloudUpload,"Import library","Restore a Mousiké JSON backup",{importer.launch(arrayOf("application/json","text/plain"))})
                SettingsClickableRow(Icons.Default.History,"Clear listening history","Reset listening signals",{viewModel.clearListeningHistory()})
                SettingsClickableRow(Icons.Default.DeleteForever,"Reset Mousiké","Remove local library, downloads and API key",{clear=true},Color(0xFFFF707C))
            }
        }
        if(activeSection=="support") item {
            SettingsCategoryHeader("SUPPORT & INFORMATION")
            SettingsPanel {
                SettingsClickableRow(Icons.Default.AutoAwesome,"Discover Mousiké","A quick look at your listening features",{showSpotlight=true})
                SettingsClickableRow(Icons.Default.SystemUpdate,"Update audio extractor","Refresh yt-dlp when YouTube changes",{viewModel.updateExtractor()})
                SettingsClickableRow(Icons.Default.Info,"About us","Mousiké • Developed by Sujal Wagholikar",{viewModel.navigateTo(NavScreen.ABOUT)})
                SettingsClickableRow(Icons.Default.ContentCopy,"Copy playback diagnostics","Current connection stages, without API keys",{
                    val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Mousiké playback",com.mousike.app.playback.PlaybackDiagnostics.copyText()))
                    viewModel.showSnackbar("Playback diagnostics copied")
                })
            }
        }
        item { Column(Modifier.fillMaxWidth().padding(top=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            com.mousike.app.ui.components.MousikeLogo(Modifier.size(32.dp));Text("Mousiké ${com.mousike.app.BuildConfig.VERSION_NAME}",fontFamily=Manrope,fontWeight=FontWeight.Bold,color=TextSecondary,fontSize=12.sp,modifier=Modifier.padding(top=8.dp));Text("Sound In motion",color=TextMuted,fontSize=10.sp,modifier=Modifier.padding(top=4.dp))
        }}
    }
    }
    if(showSpotlight)com.mousike.app.ui.components.FeatureSpotlightDialog(onDismiss={showSpotlight=false},onCustomise={showSpotlight=false;section="appearance"})
    if(clear) AlertDialog(onDismissRequest={clear=false},title={Text("Reset Mousiké?")},text={Text("This removes your likes, playlists, history, offline downloads, preferences and saved API key from this device.")},confirmButton={TextButton(onClick={clear=false;viewModel.clearAllData()}){Text("Reset",color=Color(0xFFFF707C))}},dismissButton={TextButton(onClick={clear=false}){Text("Cancel")}})
}
@Composable fun SettingsPanel(content: @Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxWidth().background(if(LocalAppearance.current.surfaceStyle=="Flat") CharcoalSurface else SurfaceCard,ComponentShape).then(if(LocalAppearance.current.surfaceStyle=="Outlined")Modifier.border(1.dp,SurfaceBorder,ComponentShape)else Modifier).padding(16.dp),content=content) }
@Composable fun SettingsCategoryHeader(title: String) { Text(title,color=MousikeGreen,fontSize=11.sp,fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,letterSpacing=1.8.sp,modifier=Modifier.padding(top=12.dp,bottom=8.dp)) }
@Composable fun SettingsClickableRow(icon: ImageVector,title: String,subtitle: String,onClick: ()->Unit,titleColor: Color=TextPrimary) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,tint=MousikeGreen,modifier=Modifier.size(23.dp));Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,color=titleColor,fontWeight=FontWeight.SemiBold,fontSize=15.sp);Text(subtitle,color=TextSecondary,fontSize=12.sp)};Icon(Icons.Default.ChevronRight,null,tint=TextMuted)
    }
}
@Composable fun SettingsToggleRow(title: String,subtitle: String,checked: Boolean,onCheckedChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text(title,color=TextPrimary,fontWeight=FontWeight.SemiBold,fontSize=15.sp);Text(subtitle,color=TextSecondary,fontSize=12.sp)};Spacer(Modifier.width(8.dp));Switch(checked,onCheckedChange) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun AppearanceChoices(title: String,choices: List<String>,selected: String,onSelect: (String)->Unit) {
    Text(title,color=TextPrimary,style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(top=12.dp))
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        choices.forEach {choice->FilterChip(selected==choice,{onSelect(choice)},label={Text(choice)},modifier=Modifier.testTag("appearance_${title.replace(" ","_").lowercase()}_${choice.lowercase()}"))}
    }
}

private data class SettingsSection(val id: String,val title: String,val subtitle: String,val icon: ImageVector)
private val settingsSections=listOf(
    SettingsSection("personal","Personalisation","Profile, languages and your listening taste",Icons.Default.Person),
    SettingsSection("appearance","Appearance","Themes, colours, typography and Home",Icons.Default.Palette),
    SettingsSection("interface","Player & components","Player layouts, albums, songs and navigation",Icons.Default.DashboardCustomize),
    SettingsSection("playback","Playback & audio","Quality, queue, equalizer and sleep timer",Icons.Default.Headphones),
    SettingsSection("downloads","Downloads","Offline music and download preferences",Icons.Default.Download),
    SettingsSection("display","Display & motion","Refresh rate, animations and preparation",Icons.Default.Speed),
    SettingsSection("ai","AI recommendations","Your optional Gemini connection",Icons.Default.AutoAwesome),
    SettingsSection("data","Library & privacy","Backup, restore and listening history",Icons.Default.Shield),
    SettingsSection("support","About & support","Developer, features and playback help",Icons.Default.Info)
)
