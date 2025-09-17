


@Composable
private fun RenderTopBar(
    cfg: JSONObject,
    dispatch: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val variant = cfg.optString("variant", "small")
    val title = cfg.optString("title", "")
    val subtitle = cfg.optString("subtitle", "")

    val rounded = RoundedCornerShape(
        topStart = 0.dp, topEnd = 0.dp,
        bottomStart = (cfg.optDouble("roundedBottomStart", 0.0).toFloat()).dp,
        bottomEnd   = (cfg.optDouble("roundedBottomEnd",   0.0).toFloat()).dp
    )

// Container unificato con fallback dai campi legacy
    val containerCfg = cfg.optJSONObject("container")?.let { JSONObject(it.toString()) } ?: JSONObject()
// Legacy gradient
    cfg.optJSONObject("gradient")?.let { g ->
        if (!containerCfg.has("gradient")) containerCfg.put("gradient", JSONObject(g.toString()))
    }
// Legacy containerColor
    cfg.optString("containerColor","").takeIf { it.isNotBlank() }?.let { col ->
        if (!containerCfg.has("customColor")) containerCfg.put("customColor", col)
        if (!containerCfg.has("style")) containerCfg.put("style", "surface")
    }

    val resolved = resolveContainer(containerCfg)
    val titleColor     = parseColorOrRole(cfg.optString("titleColor", ""))   ?: resolved.contentColor
    val actionsColor   = parseColorOrRole(cfg.optString("actionsColor", "")) ?: titleColor

    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent, // sfondo lo gestisce StyledContainer
        titleContentColor = titleColor,
        actionIconContentColor = actionsColor,
        navigationIconContentColor = actionsColor
    )

    val actions = cfg.optJSONArray("actions") ?: JSONArray()

    StyledContainer(
        cfg = containerCfg,
        modifier = Modifier
            .fillMaxWidth()
            .clip(rounded) // arrotondamento inferiore legacy
    ) {
        when (variant) {
            "center" -> CenterAlignedTopAppBar(
                title = { TitleSubtitle(title, subtitle, titleColor) },
                actions = { RenderBarItemsRow(actions, dispatch) },
                colors = colors,
                scrollBehavior = scrollBehavior
            )
            "medium" -> MediumTopAppBar(
                title = { TitleSubtitle(title, subtitle, titleColor) },
                actions = { RenderBarItemsRow(actions, dispatch) },
                colors = colors,
                scrollBehavior = scrollBehavior
            )
            "large" -> LargeTopAppBar(
                title = { TitleSubtitle(title, subtitle, titleColor) },
                actions = { RenderBarItemsRow(actions, dispatch) },
                colors = colors,
                scrollBehavior = scrollBehavior
            )
            else -> TopAppBar(
                title = { TitleSubtitle(title, subtitle, titleColor) },
                actions = { RenderBarItemsRow(actions, dispatch) },
                colors = colors,
                scrollBehavior = scrollBehavior
            )
        }

        if (cfg.optBoolean("divider", false)) {
            Divider()
        }
    }
}

