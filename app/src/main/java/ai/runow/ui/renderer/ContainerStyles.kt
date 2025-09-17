




private data class ResolvedContainer(
    val shape: CornerBasedShape,
    val bgBrush: Brush?,             // null -> trasparente
    val bgAlpha: Float,
    val border: BorderStroke?,       // null -> nessun bordo
    val elevation: Dp,               // ombra
    val widthMode: String,
    val heightMode: String,
    val widthDp: Dp,
    val heightDp: Dp,
    val widthFraction: Float?,
    val contentColor: Color,
    val borderMode: String
)

@Composable
private fun resolveContainer(cfg: JSONObject?): ai.runow.ui.renderer.ResolvedContainer {
    val cs = colorsFallback()   // palette m3
    val style = cfg?.optString("style", "surface") ?: "surface"  // text | outlined | tonal | primary | surface
    val shapeName = cfg?.optString("shape", "rounded") ?: "rounded"
    val corner = cfg?.optDouble("corner", 12.0)?.toFloat() ?: 12f
    val elevationDp = cfg?.optDouble("elevationDp", if (style in listOf("surface","primary","tonal")) 1.0 else 0.0)?.toFloat() ?: 0f

    val shape: CornerBasedShape = when (shapeName) {
        "pill"    -> RoundedCornerShape(percent = 50)
        "cut"     -> CutCornerShape(corner.dp)
        else      -> RoundedCornerShape(corner.dp)
    }

    val baseBg = when (style) {
        "text"     -> Color.Transparent
        "outlined" -> Color.Transparent
        "topbottom"-> Color.Transparent
        "tonal"    -> cs.surfaceVariant
        "primary"  -> cs.primary
        "full"     -> cs.surface
        else       -> cs.surface
    }

// eventuale customColor (hex o ruolo) – per ruoli null => fallback a baseBg
    val customColor = parseColorOrRole(cfg?.optString("customColor",""))
    val bgColor = customColor ?: baseBg

// gradiente (grad2 opzionale, default a singolo colore)
    val g1 = parseColorOrRole(cfg?.optString("gradient1","")) ?: bgColor
    val g2 = parseColorOrRole(cfg?.optString("gradient2",""))
    val orientation = cfg?.optString("gradientOrientation","horizontal") ?: "horizontal"
    val bgBrush: Brush? = if (style == "text") {
        null
    } else if (g2 != null) {
        if (orientation == "vertical") Brush.linearGradient(listOf(g1, g2), start = Offset(0f,0f), end = Offset(0f,1000f))
        else Brush.linearGradient(listOf(g1, g2), start = Offset(0f,0f), end = Offset(1000f,0f))
    } else {
        SolidColor(g1)
    }

    val bgAlpha = cfg?.optDouble("bgAlpha", 1.0)?.toFloat()?.coerceIn(0f,1f) ?: 1f

// border
    val borderMode = cfg?.optString("borderMode", if (style=="outlined") "full" else "none") ?: "none"
    val borderThickness = cfg?.optDouble("borderThicknessDp", if (borderMode!="none") 1.0 else 0.0)?.toFloat() ?: 0f
    val borderColor = parseColorOrRole(cfg?.optString("borderColor","")) ?: Color.Black
    val border: BorderStroke? = if (style == "text" || borderMode == "none" || borderThickness <= 0f) null
    else BorderStroke(borderThickness.dp, borderColor)

// content color
    val contentColor = if (style == "text") LocalContentColor.current
    else if (customColor != null || g2 != null) bestOnColor(g1)
    else when (style) {
        "primary" -> cs.onPrimary
        "tonal"   -> cs.onSurface
        else      -> cs.onSurface
    }

// dimensioni
    val widthMode = cfg?.optString("widthMode","wrap") ?: "wrap"     // wrap | fill | fixed_dp | fraction
    val heightMode = cfg?.optString("heightMode","wrap") ?: "wrap"   // wrap | fixed_dp
    val widthDp = (cfg?.optDouble("widthDp", 160.0) ?: 160.0).toFloat().dp  // default 160 come richiesto
    val heightDp = (cfg?.optDouble("heightDp", 48.0) ?: 48.0).toFloat().dp
    val widthFraction = cfg?.optDouble("widthFraction", Double.NaN)?.takeIf { !it.isNaN() }?.toFloat()

    return ResolvedContainer(
        shape = shape,
        bgBrush = if (style == "outlined") null else bgBrush,
        bgAlpha = if (style == "text") 0f else bgAlpha,
        border = border,
        elevation = elevationDp.dp,
        widthMode = widthMode,
        heightMode = heightMode,
        widthDp = widthDp,
        heightDp = heightDp,
        widthFraction = widthFraction,
        contentColor = contentColor,
        borderMode = borderMode
    )
}


private fun Modifier.topBottomBorder(width: Dp, color: Color) = this.then(
    Modifier.drawBehind {
        val w = width.toPx()
// top
        drawLine(color, start = Offset(0f, 0f + w/2f), end = Offset(size.width, 0f + w/2f), strokeWidth = w)
// bottom
        drawLine(color, start = Offset(0f, size.height - w/2f), end = Offset(size.width, size.height - w/2f), strokeWidth = w)
    }
)

@Composable
fun StyledContainer(
    cfg: JSONObject,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues? = null,
    content: @Composable BoxScope.() -> Unit
) {
// === Stile e shape ===
    val style = cfg.optString("style", "full").lowercase()
    val shapeName = cfg.optString("shape", "rounded")
    val corner = cfg.optDouble("corner", 12.0).toFloat()
    val cs = MaterialTheme.colorScheme

    val shape = when (shapeName) {
        "cut"       -> CutCornerShape(corner.dp)
        "pill"      -> RoundedCornerShape(percent = 50)
        "topBottom" -> RoundedCornerShape(0.dp)
        else        -> RoundedCornerShape(corner.dp)
    }

// === Dimensioni ===
    val widthMode = cfg.optString("widthMode", "wrap")             // wrap | fill | fixed_dp | fraction
    val heightMode = cfg.optString("heightMode", "wrap")           // wrap | fixed_dp
    val widthDp = cfg.optDouble("widthDp", 160.0).toFloat().dp
    val heightDp = cfg.optDouble("heightDp", 0.0).toFloat().dp
    val widthFraction = cfg.optDouble("widthFraction", Double.NaN)
        .let { if (it.isNaN()) null else it.toFloat().coerceIn(0f, 1f) }

// Outer wrapper per ancoraggio di crescita (sx/centro/dx) rispetto al parent
    val outerAlignment = when (cfg.optString("hAlign", "start")) {
        "center" -> Alignment.Center
        "end"    -> Alignment.CenterEnd
        else     -> Alignment.CenterStart
    }
// Se la larghezza è controllata (fill/fraction/fixed) ancoriamo sul parent full width,
// se è wrap lasciamo intatto il modifier passato.
    val outerMod = if (widthMode == "wrap") modifier else modifier.fillMaxWidth()

// Modificatore dimensionale applicato al contenitore vero e proprio
    val sizeMod =
        when (widthMode) {
            "fill"      -> Modifier.fillMaxWidth()
            "fixed_dp"  -> Modifier.width(widthDp)
            "fraction"  -> Modifier.fillMaxWidth(widthFraction ?: 1f)
            else        -> Modifier
        }.then(
            when (heightMode) {
                "fixed_dp", "fixed" -> Modifier.height(heightDp)
                else                -> Modifier
            }
        )

// === Colori / Gradiente ===
    val customColor = parseColorOrRole(cfg.optString("customColor", ""))
    val bgAlpha = cfg.optDouble("bgAlpha", 1.0).toFloat().coerceIn(0f, 1f)

// Gradiente (nuovo oggetto) + legacy color1/color2 con fix di color2
    val gradObj = cfg.optJSONObject("gradient")
    val gradBrush: Brush? = gradObj?.let { g ->
        val arr = g.optJSONArray("colors")
        val cols = (0 until (arr?.length() ?: 0))
            .mapNotNull { i -> parseColorOrRole(arr!!.optString(i)) }
        if (cols.size >= 2) {
            if (g.optString("direction", "vertical") == "horizontal")
                Brush.horizontalGradient(cols)
            else
                Brush.verticalGradient(cols)
        } else null
    } ?: run {
// Legacy: color1 / color2 – ora supportano sia ruoli che HEX (fix)
        val c1s = cfg.optString("color1", "")
        val c2s = cfg.optString("color2", "")
        val a = c1s.takeIf { it.isNotBlank() }?.let { parseColorOrRole(it) }
            ?: c1s.takeIf { it.isNotBlank() }?.let {
                runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
            }
        val b = c2s.takeIf { it.isNotBlank() }?.let { parseColorOrRole(it) }
            ?: c2s.takeIf { it.isNotBlank() }?.let {
                runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
            }
        if (a != null && b != null) {
            if (cfg.optString("gradientDirection", "horizontal") == "vertical")
                Brush.verticalGradient(listOf(a, b))
            else
                Brush.horizontalGradient(listOf(a, b))
        } else null
    }

    val baseBgColor = customColor ?: when (style) {
        "primary" -> cs.primary
        "tonal"   -> cs.surfaceVariant
        else      -> cs.surface
    }
    val transparentBg = cfg.optBoolean("transparentBg", false)

// === Immagine di sfondo ===
    val imageCfg = cfg.optJSONObject("image")
    val imageSrc = imageCfg?.optString("source", "").orEmpty()
    val imageScale = when (imageCfg?.optString("contentScale","fill")) {
        "fit"  -> ContentScale.Fit
        "crop" -> ContentScale.Crop
        else   -> ContentScale.FillBounds
    }
    val imageAlpha = imageCfg?.optDouble("alpha", 1.0)?.toFloat()?.coerceIn(0f,1f) ?: 1f
    val ctx = LocalContext.current
    val resId = if (imageSrc.startsWith("res:"))
        ctx.resources.getIdentifier(imageSrc.removePrefix("res:"), "drawable", ctx.packageName)
    else 0
    val uriStr = when {
        imageSrc.startsWith("uri:")     -> imageSrc.removePrefix("uri:")
        imageSrc.startsWith("content:") -> imageSrc
        imageSrc.startsWith("file:")    -> imageSrc
        else -> null
    }
    val bmp = if (uriStr != null) rememberImageBitmapFromUri(uriStr) else null

// === Bordi & separatori ===
    val thick = when {
        cfg.has("thick")               -> cfg.optDouble("thick", 0.0).toFloat()
        cfg.has("borderThicknessDp")   -> cfg.optDouble("borderThicknessDp", 0.0).toFloat()
        style == "outlined" || style == "topbottom" -> 1f
        else -> 0f
    }.coerceAtLeast(0f)
    val borderColor = parseColorOrRole(cfg.optString("borderColor","")) ?: cs.outline
    val drawSeparators = style == "topbottom" && thick > 0f
    val showFill = (style in listOf("full","surface","primary","tonal")) && !transparentBg
    val showBorder = (style == "outlined" && thick > 0f) ||
            ((style in listOf("full","surface","primary","tonal")) && thick > 0f)

// === Elevazione ===
    val elevationDp = cfg.optDouble("elevationDp", if (showFill) 1.0 else 0.0).toFloat().coerceAtLeast(0f)

// === Modificatori finali del contenitore ===
    val base = if (elevationDp > 0f) sizeMod.shadow(elevationDp.dp, shape, clip = false) else sizeMod
    val withSeparators = if (drawSeparators) base.topBottomBorder(thick.dp, borderColor) else base
    val withBorder = if (showBorder && !drawSeparators) withSeparators.border(BorderStroke(thick.dp, borderColor), shape) else withSeparators
    val clipped = withBorder.clip(shape)

// === Allineamenti interni del contenuto ===
    val hAlign = cfg.optString("hAlign", "start")
    val vAlign = cfg.optString("vAlign", "center")
    val innerAlignment = when (hAlign) {
        "center" -> when (vAlign) {
            "top"    -> Alignment.TopCenter
            "bottom" -> Alignment.BottomCenter
            else     -> Alignment.Center
        }
        "end" -> when (vAlign) {
            "top"    -> Alignment.TopEnd
            "bottom" -> Alignment.BottomEnd
            else     -> Alignment.CenterEnd
        }
        else -> when (vAlign) {
            "top"    -> Alignment.TopStart
            "bottom" -> Alignment.BottomStart
            else     -> Alignment.CenterStart
        }
    }

// Se widthMode è wrap NON forziamo l'inner a riempire la larghezza: si adatta al contenuto.
    val innerPad = contentPadding?.let { Modifier.padding(it) } ?: Modifier
    val contentContainerMod = if (widthMode == "wrap") innerPad else innerPad.fillMaxWidth()

// === Render ===
    Box(modifier = outerMod, contentAlignment = outerAlignment) {
        Box(modifier = clipped) {
// Layer di background (solo se non "text")
            if (style != "text") {
                if (showFill && gradBrush == null) {
                    Box(Modifier.matchParentSize().background(baseBgColor.copy(alpha = bgAlpha)))
                }
                if (imageSrc.isNotBlank()) {
                    when {
                        resId != 0 -> Image(
                            painter = painterResource(resId),
                            contentDescription = null,
                            contentScale = imageScale,
                            modifier = Modifier.matchParentSize(),
                            alpha = imageAlpha
                        )
                        bmp != null -> Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = imageScale,
                            modifier = Modifier.matchParentSize(),
                            alpha = imageAlpha
                        )
                    }
                }
                if (showFill && gradBrush != null) {
                    Box(Modifier.matchParentSize().background(gradBrush).alpha(bgAlpha))
                }
            }

// Contenuto (allineabile)
            Box(modifier = contentContainerMod, contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.align(innerAlignment)) {
                    content()
                }
            }
        }
    }
}

