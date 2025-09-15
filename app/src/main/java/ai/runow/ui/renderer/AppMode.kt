package ai.runow.ui.renderer

import androidx.compose.runtime.compositionLocalOf

enum class AppMode { Real, Designer, Resize }
val LocalAppMode = compositionLocalOf { AppMode.Real }
