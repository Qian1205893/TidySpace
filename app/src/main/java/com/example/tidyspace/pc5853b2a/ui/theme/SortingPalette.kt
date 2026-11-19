package com.example.tidyspace.pc5853b2a.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// App-owned object colors from design-spec.json theme.brandColors; native ColorScheme unchanged.
object SortingPalette {
    val trayOak = Color(0xFFCBA87F) // design-style: fixed-figma-color design-spec.json#theme.brandColors.trayOak
    val trayEdge = Color(0xFFAB865F) // design-style: fixed-figma-color design-spec.json#theme.brandColors.trayEdge
    val trayCavity = Color(0xFFB69370) // design-style: fixed-figma-color design-spec.json#theme.brandColors.trayCavity
    val paperCream = Color(0xFFF4EBD8) // design-style: fixed-figma-color design-spec.json#theme.brandColors.paperCream
    val coralObject = Color(0xFFDE927E) // design-style: fixed-figma-color design-spec.json#theme.brandColors.coralObject
    val mintObject = Color(0xFF9BBCAD) // design-style: fixed-figma-color design-spec.json#theme.brandColors.mintObject
    val skyObject = Color(0xFF92ADC1) // design-style: fixed-figma-color design-spec.json#theme.brandColors.skyObject
    val goldObject = Color(0xFFD3AD69) // design-style: fixed-figma-color design-spec.json#theme.brandColors.goldObject
    val inkObject = Color(0xFF4F5C64) // design-style: fixed-figma-color design-spec.json#theme.brandColors.inkObject
    val slotGlow = Color(0xFF93C7A7) // design-style: fixed-figma-color design-spec.json#theme.brandColors.slotGlow
    val hex = mapOf(
        "trayOak" to trayOak, "trayEdge" to trayEdge, "trayCavity" to trayCavity,
        "paperCream" to paperCream, "coralObject" to coralObject, "mintObject" to mintObject,
        "skyObject" to skyObject, "goldObject" to goldObject, "inkObject" to inkObject,
        "slotGlow" to slotGlow,
    ).mapValues { (_,color) -> color.toArgb() and 0xFFFFFF }
}
