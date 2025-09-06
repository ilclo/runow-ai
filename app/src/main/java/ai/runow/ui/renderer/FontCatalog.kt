package ai.runow.ui.renderer

import androidx.compose.ui.text.font.*
import ai.runow.R

object FontCatalog {
    val FONT_FAMILY_OPTIONS = listOf("(default)", "Inter", "Manrope", "Poppins", "Urbanist", "Mulish", "Rubik", "Space Grotesk", "JetBrains Mono", "IBM Plex Sans", "IBM Plex Mono")
    fun resolveFontFamily(name: String?): FontFamily? {
        val k = name?.trim().orEmpty()
        return when (k) {
            "Inter" -> FontFamily(
                Font(R.font.inter_regular),
                Font(R.font.inter_medium, weight = FontWeight.Medium),
                Font(R.font.inter_semibold, weight = FontWeight.SemiBold),
                Font(R.font.inter_bold, weight = FontWeight.Bold),
                Font(R.font.inter_italic, style = FontStyle.Italic)
            )
            "Manrope" -> FontFamily(
                Font(R.font.manrope_regular),
                Font(R.font.manrope_medium, weight = FontWeight.Medium),
                Font(R.font.manrope_semibold, weight = FontWeight.SemiBold),
                Font(R.font.manrope_bold, weight = FontWeight.Bold)
            )
            "Poppins" -> FontFamily(
                Font(R.font.poppins_regular),
                Font(R.font.poppins_medium, weight = FontWeight.Medium),
                Font(R.font.poppins_semibold, weight = FontWeight.SemiBold),
                Font(R.font.poppins_bold, weight = FontWeight.Bold),
                Font(R.font.poppins_italic, style = FontStyle.Italic)
            )
            "Urbanist" -> FontFamily(
                Font(R.font.urbanist_regular),
                Font(R.font.urbanist_medium, weight = FontWeight.Medium),
                Font(R.font.urbanist_semibold, weight = FontWeight.SemiBold),
                Font(R.font.urbanist_bold, weight = FontWeight.Bold),
                Font(R.font.urbanist_italic, style = FontStyle.Italic)
            )
            "Mulish" -> FontFamily(
                Font(R.font.mulish_regular),
                Font(R.font.mulish_medium, weight = FontWeight.Medium),
                Font(R.font.mulish_semibold, weight = FontWeight.SemiBold),
                Font(R.font.mulish_bold, weight = FontWeight.Bold),
                Font(R.font.mulish_italic, style = FontStyle.Italic)
            )
            "Rubik" -> FontFamily(
                Font(R.font.rubik_regular),
                Font(R.font.rubik_medium, weight = FontWeight.Medium),
                Font(R.font.rubik_semibold, weight = FontWeight.SemiBold),
                Font(R.font.rubik_bold, weight = FontWeight.Bold),
                Font(R.font.rubik_italic, style = FontStyle.Italic)
            )
            "Space Grotesk" -> FontFamily(
                Font(R.font.space_grotesk_regular),
                Font(R.font.space_grotesk_medium, weight = FontWeight.Medium),
                Font(R.font.space_grotesk_semibold, weight = FontWeight.SemiBold),
                Font(R.font.space_grotesk_bold, weight = FontWeight.Bold)
            )
            "JetBrains Mono" -> FontFamily(
                Font(R.font.jetbrains_mono_regular),
                Font(R.font.jetbrains_mono_medium, weight = FontWeight.Medium),
                Font(R.font.jetbrains_mono_bold, weight = FontWeight.Bold),
                Font(R.font.jetbrains_mono_italic, style = FontStyle.Italic)
            )
            "IBM Plex Sans" -> FontFamily(
                Font(R.font.ibm_plex_sans_regular),
                Font(R.font.ibm_plex_sans_medium, weight = FontWeight.Medium),
                Font(R.font.ibm_plex_sans_semibold, weight = FontWeight.SemiBold),
                Font(R.font.ibm_plex_sans_bold, weight = FontWeight.Bold),
                Font(R.font.ibm_plex_sans_italic, style = FontStyle.Italic)
            )
            "IBM Plex Mono" -> FontFamily(
                Font(R.font.ibm_plex_mono_regular),
                Font(R.font.ibm_plex_mono_medium, weight = FontWeight.Medium),
                Font(R.font.ibm_plex_mono_bold, weight = FontWeight.Bold),
                Font(R.font.ibm_plex_mono_italic, style = FontStyle.Italic)
            )
            else -> null
        }
    }
}
