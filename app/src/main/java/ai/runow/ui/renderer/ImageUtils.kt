package ai.runow.ui.renderer

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Decodifica pigramente un ImageBitmap da URI (con caching remember). */
@Composable
fun rememberImageBitmapFromUri(uri: Uri?, maxWidthPx: Int? = null, maxHeightPx: Int? = null): ImageBitmap?
