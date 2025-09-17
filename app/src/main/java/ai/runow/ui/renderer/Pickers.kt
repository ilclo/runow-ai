





@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdown(
    value: String,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var internal by remember { mutableStateOf(value) }

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = internal,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = {
                            internal = opt
                            expanded = false
                            onSelect(opt)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NamedColorPickerPlus(
    current: String,
    label: String,
    allowRoles: Boolean = false,
    onPick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val display = if (current.isBlank()) "(default)" else current

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowRoles) {
                val cs = MaterialTheme.colorScheme
                fun roleColor(role: String): Color = when (role) {
                    "primary","onPrimary"     -> cs.primary
                    "secondary","onSecondary" -> cs.secondary
                    "tertiary","onTertiary"   -> cs.tertiary
                    "error","onError"         -> cs.error
                    "surface","onSurface"     -> cs.surface
                    else -> cs.primary
                }
                MATERIAL_ROLES.forEach { role ->
                    val c = roleColor(role)
                    DropdownMenuItem(
                        leadingIcon = { Box(Modifier.size(16.dp).background(c, RoundedCornerShape(3.dp))) },
                        text = { Text(role) },
                        onClick = { onPick(role); expanded = false }
                    )
                }
                Divider()
            }
            NAMED_SWATCHES.forEach { (name, argb) ->
                val c = Color(argb)
                DropdownMenuItem(
                    leadingIcon = { Box(Modifier.size(16.dp).background(c, RoundedCornerShape(3.dp))) },
                    text = { Text(name) },
                    onClick = {
                        val hex = "#%06X".format(argb and 0xFFFFFF)
                        onPick(hex); expanded = false
                    }
                )
            }
            DropdownMenuItem(text = { Text("(default)") }, onClick = { onPick(""); expanded = false })
        }
    }
}


private val ICONS = listOf(
    "settings", "more_vert", "tune",
    "play_arrow", "pause", "stop", "add",
    "flag", "queue_music", "widgets", "palette",
    "home", "menu", "close", "more_horiz", "list", "tab", "grid_on",
    "directions_run", "directions_walk", "directions_bike",
    "fitness_center", "timer", "timer_off", "watch_later",
    "map", "my_location", "place", "speed",
    "bolt", "local_fire_department", "sports_score", "toggle_on"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerField(
    value: MutableState<String>,
    label: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value.value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = { if (value.value.isNotBlank()) NamedIconEx(value.value, null) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ICONS.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = { NamedIconEx(name, null) },
                    onClick = { onSelected(name); expanded = false }
                )
            }
        }
    }
}


@Composable
fun ImagePickerRow(
    label: String,
    current: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit,
    previewHeight: Dp = 80.dp
) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* permesso già preso o non necessario */ }
            onChange("uri:${uri}")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = current,
                onValueChange = { onChange(it) },
                label = { Text(label) },
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { launcher.launch(arrayOf("image/*")) }) { Text("Sfoglia…") }
            TextButton(onClick = { onClear() }) { Text("Rimuovi") }
        }
        if (current.isNotBlank()) {
            val isRes = current.startsWith("res:")
            val isUri = current.startsWith("uri:") || current.startsWith("content:") || current.startsWith("file:")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                when {
                    isRes -> {
                        val id = ctx.resources.getIdentifier(current.removePrefix("res:"), "drawable", ctx.packageName)
                        if (id != 0) {
                            Image(
                                painter = painterResource(id),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }
                    isUri -> {
                        val bmp = rememberImageBitmapFromUri(current.removePrefix("uri:"))
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StepperField(
    label: String,
    state: MutableState<String>,
    step: Double = 1.0,
    onChangeValue: (Double) -> Unit
) {
    fun current(): Double = state.value.toDoubleOrNull() ?: 0.0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val v = current() - step
                state.value = "%.2f".format(max(v, -10000.0))
                onChangeValue(current())
            }) { Text("-") }

            OutlinedTextField(
                value = state.value,
                onValueChange = {
                    state.value = it
                    val num = it.toDoubleOrNull()
                    if (num != null) onChangeValue(num)
                },
                singleLine = true,
                modifier = Modifier.width(120.dp)
            )

            OutlinedButton(onClick = {
                val v = current() + step
                state.value = "%.2f".format(min(v, 10000.0))
                onChangeValue(current())
            }) { Text("+") }
        }
    }
}

@Composable
fun SegmentedButtons(
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,               // <— prima
    onSelect: (String) -> Unit                   // <— ultimo
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(opt) }
            )
        }
    }
}

@Composable
fun ColorRow(
    current: String,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit
) {
    val swatches = listOf(
        "#000000", "#333333", "#666666", "#999999", "#CCCCCC", "#FFFFFF",
        "#E53935", "#8E24AA", "#3949AB", "#1E88E5", "#039BE5", "#00897B",
        "#43A047", "#7CB342", "#FDD835", "#FB8C00"
    )
    Row(modifier.horizontalScroll(rememberScrollState())) {
        swatches.forEach { hex ->
            val c = colorFromHex(hex) ?: Color.Black
            val sel = current.equals(hex, ignoreCase = true)
            Box(
                Modifier
                    .size(28.dp)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(c)
                    .border(
                        2.dp,
                        if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onPick(hex) }
            )
            Spacer(Modifier.width(6.dp))
        }
    }
}