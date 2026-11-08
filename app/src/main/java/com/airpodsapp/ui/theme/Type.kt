package com.airpodsapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.airpodsapp.R

/**
 * Google Sans Flex (SIL OFL 1.1, bundled in res/font). It is a variable font:
 * each weight is an instance of the same file. `ROND` rounds the corners (0..100).
 */
private fun googleSansFlex(weight: FontWeight, roundness: Float) = Font(
    resId = R.font.google_sans_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("ROND", roundness),
    ),
)

private fun family(roundness: Float) = FontFamily(
    googleSansFlex(FontWeight.Normal, roundness),
    googleSansFlex(FontWeight.Medium, roundness),
    googleSansFlex(FontWeight.SemiBold, roundness),
    googleSansFlex(FontWeight.Bold, roundness),
)

/** Text family. */
val GoogleSansFlex = family(roundness = 0f)

/** Fully rounded variant, used for large numbers (battery percentages). */
val GoogleSansFlexRounded = family(roundness = 100f)

private fun TextStyle.with(family: FontFamily, weight: FontWeight? = null) =
    copy(fontFamily = family, fontWeight = weight ?: fontWeight)

private val base = Typography()

/** Material 3 type scale on Google Sans Flex; display/headline styles use the rounded variant. */
val PodsTypography = Typography(
    displayLarge = base.displayLarge.with(GoogleSansFlexRounded, FontWeight.Medium),
    displayMedium = base.displayMedium.with(GoogleSansFlexRounded, FontWeight.Medium),
    displaySmall = base.displaySmall.with(GoogleSansFlexRounded, FontWeight.Medium),
    headlineLarge = base.headlineLarge.with(GoogleSansFlexRounded),
    headlineMedium = base.headlineMedium.with(GoogleSansFlexRounded),
    headlineSmall = base.headlineSmall.with(GoogleSansFlexRounded),
    titleLarge = base.titleLarge.with(GoogleSansFlex, FontWeight.Medium),
    titleMedium = base.titleMedium.with(GoogleSansFlex),
    titleSmall = base.titleSmall.with(GoogleSansFlex),
    bodyLarge = base.bodyLarge.with(GoogleSansFlex),
    bodyMedium = base.bodyMedium.with(GoogleSansFlex),
    bodySmall = base.bodySmall.with(GoogleSansFlex),
    labelLarge = base.labelLarge.with(GoogleSansFlex),
    labelMedium = base.labelMedium.with(GoogleSansFlex),
    labelSmall = base.labelSmall.with(GoogleSansFlex),
)
