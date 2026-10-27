package com.mousike.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.mousike.app.settings.Preferences
import com.mousike.app.settings.AppSettings

val LocalAppearance=staticCompositionLocalOf {Preferences()}

@Composable
fun MousikeTheme(content: @Composable ()->Unit) {
    val p by AppSettings.get(LocalContext.current).state.collectAsState()
    val light=p.theme=="Light" || p.theme=="System" && !isSystemInDarkTheme()
    val amoled=p.theme=="AMOLED"
    val accent=when(p.accent){"Lavender"->Color(0xFFB39DFF);"Coral"->Color(0xFFFF8E80);"Sky"->Color(0xFF61D5FA);"Rose"->Color(0xFFF49BC2);"Amber"->Color(0xFFE9BA6C);"Mint"->Color(0xFF75DCC4);else->Color(0xFF1ED760)}
    val palette=if(light) Palette(Color(0xFFF5F6F8),Color.White,Color(0xFFE9ECF0),Color(0xFFDFE3E8),Color(0xFFD3D9E1),Color(0xFFCED5DE),Color(0xFF11151B),Color(0xFF45505E),Color(0xFF5C6573),if(p.accent=="Green")Color(0xFF0C8539)else accent)
    else Palette(if(amoled)Color.Black else if(p.theme=="Warm")Color(0xFF15110F) else Color(0xFF090A0C),if(p.theme=="Warm")Color(0xFF211B18) else Color(0xFF121315),if(p.theme=="Warm")Color(0xFF2A221E) else Color(0xFF191B1E),Color(0xFF24272B),Color(0xFF303438),Color(0xFF292D31),Color(0xFFF8FAFC),Color(0xFFB1B5BC),Color(0xFF848B95),accent)
    val scheme=if(light) lightColorScheme(primary=palette.accent,onPrimary=Color(0xFF18181B),background=palette.background,surface=palette.surface,onSurface=palette.text,onBackground=palette.text,surfaceVariant=palette.card,onSurfaceVariant=palette.secondary)
        else darkColorScheme(primary=accent,onPrimary=Color.Black,background=palette.background,surface=palette.surface,onSurface=palette.text,onBackground=palette.text,surfaceVariant=palette.card,onSurfaceVariant=palette.secondary)
    val density=LocalDensity.current
    val size=when(p.textSize){"Small"->.92f;"Large"->1.12f;else->1f}
    val fonts=remember(p.fontCombo){fontsFor(p.fontCombo)}
    val typography=remember(fonts){typographyFor(fonts)}
    CompositionLocalProvider(LocalPalette provides palette,LocalAppearance provides p,LocalFonts provides fonts,LocalDensity provides Density(density.density,density.fontScale*size)) { MaterialTheme(colorScheme=scheme,typography=typography,content=content) }
}
