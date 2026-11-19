package com.example.model

enum class ParticleStyle(val label: String, val subtitle: String) {
    NONE("Pure DSLR", "Zero overlay"),
    GOLDEN_DUST("Golden Dust", "Warm champagne bokeh"),
    SUNBEAM_PRISM("Sunbeam Prism", "Golden light rays & flecks"),
    TEAL_FIREFLIES("Teal Fireflies", "Bioluminescent twilight glow"),
    TOUCH_TRAIL("Particle Brush", "Touch & paint live light");

    companion object {
        fun fromRaw(raw: String?): ParticleStyle {
            return entries.firstOrNull { it.name == raw } ?: NONE
        }
    }
}

data class GradingRecipe(
    val name: String = "Wedding Teal & Warm",
    val tealShadows: Float = 0.54f,
    val warmHighlights: Float = 0.64f,
    val deepGreens: Float = 0.72f,
    val contrast: Float = 0.56f,
    val exposure: Float = 0.03f,
    val vibrance: Float = 0.42f,
    val skinProtection: Float = 0.92f,
    val highlightSoftness: Float = 0.72f,
    val adaptiveLighting: Boolean = true,
    val particleStyle: ParticleStyle = ParticleStyle.NONE,
    val particleDensity: Float = 0.40f,
    val particleGlow: Float = 0.56f,
    val particleSpeed: Float = 0.36f,
    val bakeParticlesOnExport: Boolean = false
)

object CuratedRecipes {
    val signature = GradingRecipe(
        name = "Wedding Teal & Warm",
        tealShadows = 0.54f,
        warmHighlights = 0.64f,
        deepGreens = 0.72f,
        contrast = 0.56f,
        exposure = 0.03f,
        vibrance = 0.42f,
        skinProtection = 0.92f,
        highlightSoftness = 0.72f,
        adaptiveLighting = true,
        particleStyle = ParticleStyle.NONE,
        bakeParticlesOnExport = false
    )

    val velvetGarden = GradingRecipe(
        name = "Velvet Garden",
        tealShadows = 0.62f,
        warmHighlights = 0.56f,
        deepGreens = 0.86f,
        contrast = 0.58f,
        exposure = 0.02f,
        vibrance = 0.45f,
        skinProtection = 0.92f,
        highlightSoftness = 0.74f,
        adaptiveLighting = true,
        particleStyle = ParticleStyle.NONE,
        bakeParticlesOnExport = false
    )

    val goldenVeil = GradingRecipe(
        name = "Golden Veil",
        tealShadows = 0.42f,
        warmHighlights = 0.74f,
        deepGreens = 0.58f,
        contrast = 0.50f,
        exposure = 0.06f,
        vibrance = 0.42f,
        skinProtection = 0.94f,
        highlightSoftness = 0.78f,
        adaptiveLighting = true,
        particleStyle = ParticleStyle.SUNBEAM_PRISM,
        particleDensity = 0.42f,
        particleGlow = 0.60f,
        bakeParticlesOnExport = false
    )

    val twilightReception = GradingRecipe(
        name = "Twilight Reception",
        tealShadows = 0.68f,
        warmHighlights = 0.64f,
        deepGreens = 0.64f,
        contrast = 0.60f,
        exposure = 0.05f,
        vibrance = 0.40f,
        skinProtection = 0.90f,
        highlightSoftness = 0.68f,
        adaptiveLighting = true,
        particleStyle = ParticleStyle.TEAL_FIREFLIES,
        particleDensity = 0.46f,
        particleGlow = 0.64f,
        bakeParticlesOnExport = false
    )

    val all: List<GradingRecipe> = listOf(signature, velvetGarden, goldenVeil, twilightReception)
}
