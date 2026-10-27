package com.mousike.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class Palette(val background: Color,val surface: Color,val card: Color,val elevated: Color,val highlight: Color,val border: Color,val text: Color,val secondary: Color,val muted: Color,val accent: Color)
val LocalPalette=staticCompositionLocalOf { Palette(Color(0xFF090A0C),Color(0xFF121315),Color(0xFF191B1E),Color(0xFF24272B),Color(0xFF303438),Color(0xFF292D31),Color(0xFFF8FAFC),Color(0xFFB1B5BC),Color(0xFF848B95),Color(0xFFFF8E80)) }
val MousikeGreen: Color @Composable get()=LocalPalette.current.accent
val MousikeGreenDark: Color @Composable get()=LocalPalette.current.accent.copy(alpha=.65f)
val MousikeGreenLight: Color @Composable get()=LocalPalette.current.accent
val MousikeMint=Color(0xFF10B981)
val MousikeAccent: Color @Composable get()=LocalPalette.current.accent
val ObsidianDark: Color @Composable get()=LocalPalette.current.background
val CharcoalSurface: Color @Composable get()=LocalPalette.current.surface
val SurfaceCard: Color @Composable get()=LocalPalette.current.card
val SurfaceElevated: Color @Composable get()=LocalPalette.current.elevated
val SurfaceHighlight: Color @Composable get()=LocalPalette.current.highlight
val SurfaceBorder: Color @Composable get()=LocalPalette.current.border
val TextPrimary: Color @Composable get()=LocalPalette.current.text
val TextSecondary: Color @Composable get()=LocalPalette.current.secondary
val TextMuted: Color @Composable get()=LocalPalette.current.muted
val TextDark=Color(0xFF18181B)
val GenrePurple=Color(0xFF8B5CF6)
val GenrePink=Color(0xFFEC4899)
val GenreBlue=Color(0xFF3B82F6)
val GenreOrange=Color(0xFFF97316)
val GenreCyan=Color(0xFF06B6D4)
val GenreIndigo=Color(0xFF6366F1)
val GenreRose=Color(0xFFF43F5E)
val GenreEmerald=Color(0xFF10B981)
val GenreAmber=Color(0xFFF59E0B)
