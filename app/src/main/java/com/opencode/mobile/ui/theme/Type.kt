package com.opencode.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.opencode.mobile.R

val OpenCodeMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
)

private val base = Typography()

val OpenCodeTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = OpenCodeMono),
    displayMedium = base.displayMedium.copy(fontFamily = OpenCodeMono),
    displaySmall = base.displaySmall.copy(fontFamily = OpenCodeMono),
    headlineLarge = base.headlineLarge.copy(fontFamily = OpenCodeMono),
    headlineMedium = base.headlineMedium.copy(fontFamily = OpenCodeMono),
    headlineSmall = base.headlineSmall.copy(fontFamily = OpenCodeMono),
    titleLarge = base.titleLarge.copy(fontFamily = OpenCodeMono),
    titleMedium = base.titleMedium.copy(fontFamily = OpenCodeMono),
    titleSmall = base.titleSmall.copy(fontFamily = OpenCodeMono),
    bodyLarge = base.bodyLarge.copy(fontFamily = OpenCodeMono),
    bodyMedium = base.bodyMedium.copy(fontFamily = OpenCodeMono),
    bodySmall = base.bodySmall.copy(fontFamily = OpenCodeMono),
    labelLarge = base.labelLarge.copy(fontFamily = OpenCodeMono),
    labelMedium = base.labelMedium.copy(fontFamily = OpenCodeMono),
    labelSmall = base.labelSmall.copy(fontFamily = OpenCodeMono),
)
