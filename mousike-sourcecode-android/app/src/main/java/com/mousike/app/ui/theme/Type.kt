package com.mousike.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mousike.app.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

private val BaseManrope=FontFamily(Font(R.font.manrope_regular,FontWeight.Normal),Font(R.font.manrope_semibold,FontWeight.SemiBold),Font(R.font.manrope_bold,FontWeight.Bold),Font(R.font.manrope_extrabold,FontWeight.ExtraBold))
private val BaseInter=FontFamily(Font(R.font.inter_regular,FontWeight.Normal),Font(R.font.inter_medium,FontWeight.Medium),Font(R.font.inter_semibold,FontWeight.SemiBold),Font(R.font.inter_bold,FontWeight.Bold))
private val BaseRoboto=FontFamily(Font(R.font.roboto_regular,FontWeight.Normal),Font(R.font.roboto_medium,FontWeight.Medium),Font(R.font.roboto_bold,FontWeight.Bold))
private fun family(regular: Int,semibold: Int,bold: Int)=FontFamily(Font(regular,FontWeight.Normal),Font(semibold,FontWeight.SemiBold),Font(bold,FontWeight.Bold))
private val Outfit=family(R.font.outfit_regular,R.font.outfit_semibold,R.font.outfit_bold)
private val DMSans=family(R.font.dmsans_regular,R.font.dmsans_semibold,R.font.dmsans_bold)
private val Lora=family(R.font.lora_regular,R.font.lora_semibold,R.font.lora_bold)
private val Jakarta=family(R.font.plusjakartasans_regular,R.font.plusjakartasans_semibold,R.font.plusjakartasans_bold)
data class FontPair(val heading: FontFamily,val body: FontFamily,val controls: FontFamily)
val LocalFonts=staticCompositionLocalOf {FontPair(BaseManrope,BaseInter,BaseRoboto)}
val FontCombinations=linkedMapOf("Signature" to "Manrope · Inter · Roboto","Modern" to "Outfit · DM Sans · Inter","Editorial" to "Lora · Inter · Roboto","Studio" to "Plus Jakarta Sans · Roboto · Inter","Minimal" to "Inter · Inter · Roboto","Expressive" to "Outfit · Manrope · DM Sans")
fun fontsFor(name: String)=when(name) {
    "Modern"->FontPair(Outfit,DMSans,BaseInter)
    "Editorial"->FontPair(Lora,BaseInter,BaseRoboto)
    "Studio"->FontPair(Jakarta,BaseRoboto,BaseInter)
    "Minimal"->FontPair(BaseInter,BaseInter,BaseRoboto)
    "Expressive"->FontPair(Outfit,BaseManrope,DMSans)
    else->FontPair(BaseManrope,BaseInter,BaseRoboto)
}
// Existing screens use these semantic roles; every explicit family follows the selected combo.
val Manrope: FontFamily @Composable get()=LocalFonts.current.heading
val Inter: FontFamily @Composable get()=LocalFonts.current.body
val Roboto: FontFamily @Composable get()=LocalFonts.current.controls
private fun style(family: FontFamily,size: Int,line: Int,weight: FontWeight=FontWeight.Normal,spacing: Float=0f)=TextStyle(fontFamily=family,fontSize=size.sp,lineHeight=line.sp,fontWeight=weight,letterSpacing=spacing.sp)
fun typographyFor(fonts: FontPair): Typography { val Manrope=fonts.heading;val Inter=fonts.body;val Roboto=fonts.controls;return Typography(
    displayLarge=style(Manrope,48,54,FontWeight.ExtraBold,-1f),displayMedium=style(Manrope,40,46,FontWeight.ExtraBold,-.8f),displaySmall=style(Manrope,34,40,FontWeight.Bold,-.6f),
    headlineLarge=style(Manrope,30,36,FontWeight.Bold,-.6f),headlineMedium=style(Manrope,26,32,FontWeight.Bold,-.4f),headlineSmall=style(Manrope,23,29,FontWeight.Bold,-.3f),
    titleLarge=style(Manrope,21,27,FontWeight.Bold,-.3f),titleMedium=style(Manrope,16,23,FontWeight.SemiBold,-.1f),titleSmall=style(Manrope,14,20,FontWeight.SemiBold),
    bodyLarge=style(Inter,16,24),bodyMedium=style(Inter,14,21),bodySmall=style(Inter,12,18),
    labelLarge=style(Roboto,14,20,FontWeight.Medium,.1f),labelMedium=style(Roboto,12,16,FontWeight.Medium,.2f),labelSmall=style(Roboto,11,15,FontWeight.Medium,.3f)
)}
