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

/** Wrapper composable usato in UiRenderer */
@Composable
fun NamedIconEx(name: String?, tint: Color? = null, modifier: Modifier = Modifier) {
    Icon(
        imageVector = namedIconVector(name),
        contentDescription = name ?: "",
        modifier = modifier,
        tint = tint ?: Color.Unspecified
    )
}
