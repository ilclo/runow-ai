package ai.runow.ui.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import ai.runow.R

object FontCatalog {

    // Opzioni da mostrare nei dropdown
    val FONT_FAMILY_OPTIONS = listOf(
        "inter", "manrope", "mulish", "poppins", "rubik", "space_grotesk", "urbanist",
        "ibm_plex_sans", "ibm_plex_mono", "jetbrains_mono"
    )

    @Composable
    fun resolveFontFamily(key: String?): FontFamily? = when (key?.lowercase()) {
        "inter" -> FontFamily(
            Font(R.font.inter_regular),
            Font(R.font.inter_medium),
            Font(R.font.inter_semibold),
            Font(R.font.inter_bold),
            Font(R.font.inter_italic)
        )
        "manrope" -> FontFamily(
            Font(R.font.manrope_regular),
            Font(R.font.manrope_medium),
            Font(R.font.manrope_semibold),
            Font(R.font.manrope_bold)
        )
        "mulish" -> FontFamily(
            Font(R.font.mulish_regular),
            Font(R.font.mulish_medium),
            Font(R.font.mulish_semibold),
            Font(R.font.mulish_bold),
            Font(R.font.mulish_italic)
        )
        "poppins" -> FontFamily(
            Font(R.font.poppins_regular),
            Font(R.font.poppins_medium),
            Font(R.font.poppins_semibold),
            Font(R.font.poppins_bold),
            Font(R.font.poppins_italic)
        )
        "rubik" -> FontFamily(
            Font(R.font.rubik_regular),
            Font(R.font.rubik_medium),
            Font(R.font.rubik_semibold),
            Font(R.font.rubik_bold),
            Font(R.font.rubik_italic)
        )
        "space_grotesk" -> FontFamily(
            Font(R.font.space_grotesk_regular),
            Font(R.font.space_grotesk_medium),
            Font(R.font.space_grotesk_semibold),
            Font(R.font.space_grotesk_bold)
        )
        "urbanist" -> FontFamily(
            Font(R.font.urbanist_regular),
            Font(R.font.urbanist_medium),
            Font(R.font.urbanist_semibold),
            Font(R.font.urbanist_bold),
            Font(R.font.urbanist_italic)
        )
        "ibm_plex_sans" -> FontFamily(
            Font(R.font.ibm_plex_sans_regular),
            Font(R.font.ibm_plex_sans_medium),
            Font(R.font.ibm_plex_sans_semibold),
            Font(R.font.ibm_plex_sans_bold),
            Font(R.font.ibm_plex_sans_italic)
        )
        "ibm_plex_mono" -> FontFamily(
            Font(R.font.ibm_plex_mono_regular),
            Font(R.font.ibm_plex_mono_medium),
            Font(R.font.ibm_plex_mono_bold),
            Font(R.font.ibm_plex_mono_italic)
        )
        "jetbrains_mono" -> FontFamily(
            Font(R.font.jetbrains_mono_regular),
            Font(R.font.jetbrains_mono_medium),
            Font(R.font.jetbrains_mono_bold),
            Font(R.font.jetbrains_mono_italic)
        )
        else -> null
    }
}
