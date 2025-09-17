package ai.runow.ui.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector

/** Mappa di nomi "material" comuni -> ImageVector */
fun namedIconVector(name: String?): ImageVector {
    return when (name?.lowercase()) {
        "add", "plus" -> Icons.Filled.Add
        "remove", "minus" -> Icons.Filled.Remove
        "delete", "trash" -> Icons.Filled.Delete
        "edit", "pencil" -> Icons.Filled.Edit
        "menu", "hamburger" -> Icons.Filled.Menu
        "settings", "gear" -> Icons.Filled.Settings
        "search" -> Icons.Filled.Search
        "favorite", "heart" -> Icons.Filled.Favorite
        "star" -> Icons.Filled.Star
        "home" -> Icons.Filled.Home
        "info" -> Icons.Filled.Info
        "warning" -> Icons.Filled.Warning
        "close", "clear", "x" -> Icons.Filled.Close
        "check", "done", "ok" -> Icons.Filled.Check
        "arrow_back", "back" -> Icons.Filled.ArrowBack
        "arrow_forward", "forward", "next" -> Icons.Filled.ArrowForward
        "arrow_up", "arrow_upward" -> Icons.Filled.ArrowUpward
        "arrow_down", "arrow_downward" -> Icons.Filled.ArrowDownward
        "more_vert" -> Icons.Filled.MoreVert
        "more_horiz" -> Icons.Filled.MoreHoriz
        "refresh" -> Icons.Filled.Refresh
        "share" -> Icons.Filled.Share
        "download" -> Icons.Filled.Download
        "upload" -> Icons.Filled.Upload
        "camera" -> Icons.Filled.CameraAlt
        "photo", "image" -> Icons.Filled.Image
        "visibility", "show" -> Icons.Filled.Visibility
        "visibility_off", "hide" -> Icons.Filled.VisibilityOff
        "play_arrow", "play" -> Icons.Filled.PlayArrow
        "pause" -> Icons.Filled.Pause
        "stop" -> Icons.Filled.Stop
        "save" -> Icons.Filled.Save
        "filter_list" -> Icons.Filled.FilterList
        "tune" -> Icons.Filled.Tune
        "expand_more" -> Icons.Filled.ExpandMore
        "expand_less" -> Icons.Filled.ExpandLess
        else -> Icons.Filled.Help
    }
}

@Composable
fun NamedIconEx(name: String?, contentDescription: String?) {
    val __ctx = LocalContext.current
    if (name.isNullOrBlank()) {
        Text("."); return
    }

// icona da risorsa drawable
    if (name.startsWith("res:")) {
        val resName = name.removePrefix("res:")
        val id = __ctx.resources.getIdentifier(resName, "drawable", __ctx.packageName)
        if (id != 0) { Icon(painterResource(id), contentDescription); return }
    }

// icona da uri/file/content (raster)
    if (name.startsWith("uri:") || name.startsWith("content:") || name.startsWith("file:")) {
        val bmp = rememberImageBitmapFromUri(name.removePrefix("uri:"))
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )
            return
        }
    }

// icone Material note
    val image = when (name) {
        "settings" -> Icons.Filled.Settings
        "more_vert" -> Icons.Filled.MoreVert
        "tune" -> Icons.Filled.Tune
        "play_arrow" -> Icons.Filled.PlayArrow
        "pause" -> Icons.Filled.Pause
        "stop" -> Icons.Filled.Stop
        "add" -> Icons.Filled.Add
        "flag" -> Icons.Filled.Flag
        "queue_music" -> Icons.Filled.QueueMusic
        "widgets" -> Icons.Filled.Widgets
        "palette" -> Icons.Filled.Palette
        "home" -> Icons.Filled.Home
        "menu" -> Icons.Filled.Menu
        "close" -> Icons.Filled.Close
        "more_horiz" -> Icons.Filled.MoreHoriz
        "list" -> Icons.Filled.List
        "tab" -> Icons.Filled.Tab
        "grid_on" -> Icons.Filled.GridOn
        "toggle_on" -> Icons.Filled.ToggleOn
        "bolt" -> Icons.Filled.Bolt
        else -> null
    }
    if (image != null) Icon(image, contentDescription) else Text(".")
}
