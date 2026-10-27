package com.mousike.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mousike.app.R
import com.mousike.app.ui.theme.Manrope
import com.mousike.app.ui.theme.SurfaceCard
import java.util.Locale

/** Bounded Coil requests, full-source artwork and a stable identity when no image exists. */
@Composable fun MusicArtwork(
    model: Any?, contentDescription: String?, modifier: Modifier=Modifier,
    contentScale: ContentScale=ContentScale.Fit
) {
    val context=LocalContext.current
    val source=remember(model) {if(model is String) model.trim().let {if(it.startsWith("//")) "https:$it" else it}.takeIf {it.isNotBlank()} else model}
    var loaded by remember(source) {mutableStateOf(false)}
    val title=contentDescription.orEmpty()
    val initials=remember(title) {title.trim().split(Regex("\\s+")).filter {it.isNotBlank()}.take(2).map {String(Character.toChars(it.codePointAt(0)))}.joinToString("").uppercase(Locale.ROOT).ifBlank {"♪"}}
    val colors=remember(title) {
        val palettes=listOf(0xFF2E746E to 0xFF143C4D,0xFF8065AD to 0xFF35375D,0xFFB3684B to 0xFF5A394B,0xFF547A9D to 0xFF283D63,0xFF917542 to 0xFF48432B)
        val pair=palettes[(title.hashCode() and Int.MAX_VALUE)%palettes.size]
        listOf(Color(pair.first),Color(pair.second))
    }
    val request=remember(source,context) {ImageRequest.Builder(context).data(source).crossfade(false).build()}
    Box(modifier.background(if(loaded) Brush.linearGradient(listOf(SurfaceCard,SurfaceCard)) else Brush.linearGradient(colors)),contentAlignment=Alignment.Center) {
        if(!loaded) BoxWithConstraints(Modifier.fillMaxSize().testTag("artwork_placeholder"),contentAlignment=Alignment.Center) {
            val size=(minOf(maxWidth.value,maxHeight.value)*.25f).coerceIn(12f,38f)
            Text(initials,color=Color.White.copy(alpha=.9f),fontFamily=Manrope,fontSize=size.sp,fontWeight=FontWeight.Bold,maxLines=1)
        }
        AsyncImage(model=request,contentDescription=contentDescription,modifier=Modifier.fillMaxSize(),contentScale=contentScale,
            onSuccess={loaded=true},onError={loaded=false})
    }
}

@Composable fun MousikeLogo(modifier: Modifier=Modifier) {
    Image(painterResource(R.drawable.mousike_logo),"Mousiké logo",modifier.clip(RoundedCornerShape(22)),contentScale=ContentScale.Fit)
}
