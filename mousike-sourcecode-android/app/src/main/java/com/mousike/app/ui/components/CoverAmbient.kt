package com.mousike.app.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Derive the player's ambient color from the actual artwork without cropping it. */
@Composable fun coverAmbient(url: String): Color {
    if(!com.mousike.app.ui.theme.LocalAppearance.current.playerAmbience) return com.mousike.app.ui.theme.SurfaceCard
    val context=LocalContext.current
    var color by remember(url) { mutableStateOf(Color(0xFF334C43)) }
    LaunchedEffect(url) {
        if(url.isNotBlank()) runCatching {
            withContext(Dispatchers.IO) {
                val result=context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).size(100).build())
                if(result is SuccessResult) Palette.from(result.drawable.toBitmap()).maximumColorCount(8).generate().getDominantColor(0xFF334C43.toInt()) else 0xFF334C43.toInt()
            }
        }.onSuccess { color=Color(it) }
    }
    return color
}
