package com.mousike.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousike.app.BuildConfig
import com.mousike.app.ui.theme.*
import com.mousike.app.ui.viewmodel.MousikeViewModel

@Composable fun AboutScreen(viewModel: MousikeViewModel) {
    val uriHandler=androidx.compose.ui.platform.LocalUriHandler.current
    fun open(url: String){runCatching {uriHandler.openUri(url)}.onFailure {viewModel.showSnackbar("No app is available to open this link")}}
    LazyColumn(Modifier.fillMaxSize().background(ObsidianDark).testTag("about_screen"),
        contentPadding=PaddingValues(20.dp,12.dp,20.dp,24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton({viewModel.navigateBack()}) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back",tint=TextPrimary)}
            Text("About us",fontSize=22.sp,fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,color=TextPrimary)
        }}
        item {Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(MousikeGreen.copy(alpha=.17f),SurfaceCard)),RoundedCornerShape(28.dp)).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            com.mousike.app.ui.components.MousikeLogo(Modifier.size(88.dp))
            Spacer(Modifier.height(20.dp));Text("Mousiké",color=TextPrimary,fontSize=30.sp,fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Black,letterSpacing=4.sp)
            Text("Sound In motion",color=MousikeGreen,fontSize=15.sp,modifier=Modifier.padding(top=6.dp))
            Text("A space for your music. A rhythm that feels like you.",color=TextSecondary,fontSize=14.sp,lineHeight=22.sp,modifier=Modifier.padding(top=18.dp))
            Text("Version ${BuildConfig.VERSION_NAME}",color=TextMuted,fontSize=12.sp,modifier=Modifier.padding(top=20.dp))
        }}
        item {SettingsCategoryHeader("DESIGN & DEVELOPMENT")
            SettingsPanel {
                Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Image(
                        painter=androidx.compose.ui.res.painterResource(com.mousike.app.R.drawable.developer_sujal_2026),
                        contentDescription="Sujal Wagholikar, developer of Mousiké",
                        modifier=Modifier.size(200.dp).clip(RoundedCornerShape(26.dp)).testTag("developer_portrait"),
                        contentScale=androidx.compose.ui.layout.ContentScale.Fit)
                    Spacer(Modifier.height(20.dp))
                    Text("DEVELOPER & CREATOR",color=TextMuted,style=MaterialTheme.typography.labelSmall)
                    Text("Sujal Wagholikar",color=TextPrimary,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=8.dp))
                }

                Spacer(Modifier.height(18.dp))
                Text("An independent music experience built with care for discovery, personal taste and everyday listening.",color=TextSecondary,fontSize=14.sp,lineHeight=22.sp)
                Text("Developed by Sujal Wagholikar",color=MousikeGreen,fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=16.dp).testTag("developer_credit"))
                Spacer(Modifier.height(16.dp))
                SocialLink(com.mousike.app.R.drawable.ic_instagram,"Instagram","@sujalwagholikar","developer_instagram"){open("https://www.instagram.com/sujalwagholikar/")}
                SocialLink(com.mousike.app.R.drawable.ic_github,"GitHub","github.com/sujalwagholikar","developer_github"){open("https://github.com/sujalwagholikar")}
            }
        }
        item {SettingsCategoryHeader("MADE FOR YOUR LISTENING")
            SettingsPanel {
                AboutDetail(Icons.Default.Favorite,"Your library","Likes, playlists, history and backups stay with your app.")
                AboutDetail(Icons.Default.AutoAwesome,"Personal discovery","Listening activity, feedback and taste shape recommendations. Gemini is an optional boost.")
                AboutDetail(Icons.Default.Headphones,"Listen your way","A full player, queue, background controls and offline downloads.")
                AboutDetail(Icons.Default.Palette,"Your appearance","Themes, accents, full artwork and reduced motion.")
            }
        }
        item {SettingsCategoryHeader("PRIVACY & CREDITS")
            SettingsPanel {
                Text("No account is required. Music lookup and playback connect to YouTube. Optional Gemini sends selected music metadata and preferences to Google only when enabled.",color=TextSecondary,fontSize=13.sp,lineHeight=21.sp)
                Spacer(Modifier.height(16.dp))
                Text("Mousiké is an independent app and is not affiliated with YouTube or Spotify. Music and artwork belong to their respective owners.",color=TextMuted,fontSize=12.sp,lineHeight=20.sp)
                Spacer(Modifier.height(16.dp))
                Text("Kotlin · Jetpack Compose · Media3\nyt-dlp · Python · QuickJS · FFmpeg",color=TextMuted,fontSize=12.sp,lineHeight=20.sp)
                Text("GPL-3.0 • Third-party notices are included with the source.",color=TextMuted,fontSize=11.sp,modifier=Modifier.padding(top=12.dp))
            }
        }
    }
}
@Composable private fun SocialLink(icon: Int,title: String,handle: String,tag: String,onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().clip(ComponentShape).clickable(onClick=onClick).testTag(tag).padding(vertical=14.dp,horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(androidx.compose.ui.res.painterResource(icon),title,tint=MousikeGreen,modifier=Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)){Text(title,color=TextPrimary,style=MaterialTheme.typography.titleSmall);Text(handle,color=TextSecondary,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=4.dp))}
        Icon(Icons.Default.OpenInNew,"Open $title",tint=TextMuted,modifier=Modifier.size(18.dp))
    }
}
@Composable private fun AboutDetail(icon: androidx.compose.ui.graphics.vector.ImageVector,title: String,body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.Top) {
        Icon(icon,null,tint=MousikeGreen,modifier=Modifier.padding(top=2.dp).size(22.dp));Spacer(Modifier.width(14.dp))
        Column {Text(title,color=TextPrimary,fontWeight=FontWeight.SemiBold,fontSize=14.sp);Text(body,color=TextSecondary,fontSize=12.sp,lineHeight=19.sp,modifier=Modifier.padding(top=4.dp))}
    }
}
