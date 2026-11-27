package com.music.bitchord.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * The colours an album, playlist or artist page paints itself in.
 *
 * Apple Music's release pages are not one design tinted five ways — the whole
 * page is derived from the sleeve, down to which grey the metadata line is. So
 * rather than hand callers a raw swatch and let each of them guess, this is the
 * finished set: a page tint, an accent that is legible *on that tint*, and the
 * two text colours and hairline that go with them.
 *
 * Every value is theme-aware. The same sleeve yields a near-black tint in dark
 * mode and a pale wash of the same hue in light mode, which is the only way the
 * pages stay readable when the app's theme disagrees with the artwork's.
 */
@Immutable
data class ArtworkPalette(
    /** The page's background wash. */
    val background: Color,
    /**
     * The colour the artwork's own bottom edge blurs down to.
     *
     * A blur wide enough to lose the picture leaves the mean of what it
     * sampled, so a page that starts from this colour where the artwork stops
     * reads as that blur carrying on rather than as a second surface beginning.
     * Lighter than [background], which the page still settles into further
     * down — the artwork's colour is strongest right under the artwork.
     */
    val wash: Color,
    /** Fill for the glass buttons and chips that sit on [background]. */
    val elevated: Color,
    /** The artwork's own colour, contrast-corrected — titles, icons, Play. */
    val accent: Color,
    val onBackground: Color,
    val onBackgroundVariant: Color,
    val divider: Color,
    /**
     * The tint for coloured glass: what the sleeve is mostly made of, a little
     * more vivid, at a middle lightness so it reads on dark and light glass
     * alike. Unspecified until the artwork has been read.
     */
    val glass: Color = Color.Unspecified,
)

/**
 * Pulls [ArtworkPalette] out of the artwork at [imageUrl].
 *
 * Artwork that has already been read once is tinted on the very first frame,
 * off [seedCache] — a sheet opened from a page it shares a cover with, or a
 * page opened twice, has nothing to wait for and nothing to fade. Only a sleeve
 * genuinely being seen for the first time starts from the theme's own colours
 * and warms into the artwork's, so it never flashes a placeholder tint.
 * "Reduce animation" turns that crossfade into a cut.
 */
@Composable
fun rememberArtworkPalette(
    imageUrl: String?,
    dark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
    /**
     * The artwork size to read, which should be whichever one the surface
     * already has on screen, matching the backdrop drawn from it.
     *
     * A quantiser cares about a thumbnail's resolution no more than a blur
     * does, so the only thing this choice decides is whether the read comes
     * out of the cache or off the network.
     */
    artPx: Int = CARD_ART_PX,
): ArtworkPalette = rememberArtworkPaletteOf(
    key = imageUrl,
    model = imageUrl.artworkAt(artPx),
    dark = dark,
)

/**
 * As [rememberArtworkPalette], but for any Coil model (an artist portrait, say)
 * rather than only an artwork URL, so a page can take its colours from the exact
 * picture it shows.
 *
 * [key] identifies that picture in the seed cache. With [tintFromTone] the page
 * tint follows the picture's dominant *colour* (what it is mostly made of,
 * ignoring near-black and near-white), instead of the mean of its bottom edge.
 */
@Composable
fun rememberArtworkPaletteOf(
    key: String?,
    model: Any?,
    dark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
    tintFromTone: Boolean = false,
): ArtworkPalette {
    val imageUrl = key
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    // The two swatches everything else is derived from, or null until read.
    var seed by remember(imageUrl) { mutableStateOf(imageUrl?.let(::getCachedArtworkSeed)) }
    // Whether the colours were there from the first frame. If they were, there
    // is nothing to crossfade *from* and animating would only put a delay in
    // front of a surface that could already be right.
    val knownUpFront = remember(imageUrl) { seed != null }

    LaunchedEffect(imageUrl, model) {
        if (imageUrl == null || seed != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            // The size the artwork is *displayed* at, deliberately: the fetch
            // then shares a disk-cache entry with the row, card or backdrop
            // drawing the same artwork, instead of pulling its own copy over
            // the wire — which is the difference between a surface that is
            // tinted as it opens and one that turns colour a second later.
            .data(model)
            .size(PALETTE_PX) // palette quality holds up here, and it's far faster
            .allowHardware(false) // Palette needs pixel access
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
        // Quantising 128² pixels is not free, and this coroutine is on the main
        // dispatcher — left there it stutters whatever is animating the surface in.
        val found = withContext(Dispatchers.Default) { seedOf(bitmap) } ?: return@LaunchedEffect
        putCachedArtworkSeed(imageUrl, found)
        seed = found
    }

    val target = seed?.toPalette(dark, tintFromTone) ?: ArtworkPalette(
        background = scheme.background,
        wash = scheme.background,
        elevated = scheme.surfaceVariant,
        accent = scheme.primary,
        onBackground = scheme.onBackground,
        onBackgroundVariant = scheme.onSurfaceVariant,
        divider = scheme.outline,
    )

    val spec: AnimationSpec<Color> = if (reduceAnimation || knownUpFront) {
        snap()
    } else {
        tween(TINT_FADE_MS)
    }
    return ArtworkPalette(
        background = animateColorAsState(target.background, spec, label = "tintBackground").value,
        wash = animateColorAsState(target.wash, spec, label = "tintWash").value,
        elevated = animateColorAsState(target.elevated, spec, label = "tintElevated").value,
        accent = animateColorAsState(target.accent, spec, label = "tintAccent").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "tintOn").value,
        onBackgroundVariant = animateColorAsState(
            target.onBackgroundVariant, spec, label = "tintOnVariant",
        ).value,
        divider = animateColorAsState(target.divider, spec, label = "tintDivider").value,
        // Not animated here: the glass keeps the last song's colour while the
        // next sleeve is read and fades across itself (see MainActivity).
        glass = target.glass,
    )
}

/**
 * Colours already read, keyed by artwork URL.
 *
 * Reading them again costs a decode and a quantise for an answer that cannot
 * have changed — the artwork at a URL is the artwork at that URL. Access is
 * from composition and from the resumption of [rememberArtworkPalette]'s
 * effect, both on the main thread, so it needs no locking of its own.
 */
private val seedCache = object : LinkedHashMap<String, Seed>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, Seed>) = size > SEED_CACHE_ENTRIES
}

private val SIZE_HINT_PATTERN = Regex("""w\d+-h\d+""")

private fun normalizeSeedKey(url: String): String = url.replace(SIZE_HINT_PATTERN, "")

private fun getCachedArtworkSeed(url: String): Seed? =
    seedCache[url] ?: seedCache[normalizeSeedKey(url)]

private fun putCachedArtworkSeed(url: String, seed: Seed) {
    seedCache[url] = seed
    val norm = normalizeSeedKey(url)
    if (norm != url) {
        seedCache[norm] = seed
    }
}

/** Deep enough to cover a session's browsing without holding a screenful of colours. */
private const val SEED_CACHE_ENTRIES = 128

private const val PALETTE_PX = 128

/** Short: this is a surface settling into its colour, not an effect in itself. */
private const val TINT_FADE_MS = 260

/**
 * The raw artwork colours: what the page is mostly made of, its brightest
 * note, and what its bottom edge averages out to.
 */
private data class Seed(val dominant: Color, val vibrant: Color?, val edge: Color, val tone: Color = dominant)

private fun seedOf(bitmap: Bitmap): Seed? {
    fun swatches(builder: Palette.Builder) =
        builder.maximumColorCount(SWATCH_COUNT).generate().swatches

    // The default filter throws away near-black and near-white, which on a
    // monochrome sleeve is the entire image — see MeshGradientBackground, which
    // hit the same wall.
    val found = swatches(Palette.from(bitmap)).ifEmpty {
        swatches(Palette.from(bitmap).clearFilters())
    }
    if (found.isEmpty()) return null

    val dominant = found.maxBy { it.population }
    // Ignore small saturated specks before ranking. Scoring every swatch by
    // saturation alone lets a tiny pink logo or ribbon tint an entire screen.
    // Near-grays are excluded too: their hue is arbitrary (often zero/red),
    // and forcing saturation on those swatches produced unexplained pink for
    // monochrome and muted album covers.
    val strongestPopulation = found.maxOf { it.population }
    val vibrant = found.asSequence()
        .filter { it.population >= strongestPopulation * MIN_ACCENT_POPULATION_RATIO }
        .mapNotNull { swatch ->
            val hsl = FloatArray(3).also { ColorUtils.colorToHSL(swatch.rgb, it) }
            if (hsl[1] < MIN_ACCENT_SATURATION) null
            else swatch to (hsl[1] * sqrt(swatch.population.toFloat()))
        }
        .maxByOrNull { it.second }
        ?.first
        ?.let { Color(it.rgb) }
        ?: Color(dominant.rgb).takeIf { color ->
            FloatArray(3).also { hsl -> ColorUtils.colorToHSL(color.toArgb(), hsl) }[1] >= MIN_ACCENT_SATURATION
        }
    // The colour the picture reads as overall. The most populous swatch alone can
    // be the black of a jacket or the white of a wall, which would turn the whole
    // page black or grey; so among the swatches that cover a fair share of the
    // picture, and are neither near-black nor near-white, favour the one with the
    // most population and colour. A genuinely grey picture still comes out grey.
    val swatchTone = found.asSequence()
        .filter { it.population >= strongestPopulation * TONE_MIN_POPULATION_RATIO }
        .mapNotNull { swatch ->
            val hsl = FloatArray(3).also { ColorUtils.colorToHSL(swatch.rgb, it) }
            if (hsl[2] < TONE_MIN_LIGHTNESS || hsl[2] > TONE_MAX_LIGHTNESS) null
            else swatch to (swatch.population * (TONE_SATURATION_BASE + hsl[1]))
        }
        .maxByOrNull { it.second }
        ?.first
        ?.let { Color(it.rgb) }
        ?: Color(dominant.rgb)
    // What the picture reads as to the eye: the hue that covers most of it, so
    // a red wall wins over the gold of a few faces. Swatches are only the
    // fallback for pictures with no real colour in them.
    val tone = bitmap.dominantHueTone() ?: swatchTone
    return Seed(Color(dominant.rgb), vibrant, bitmap.bottomEdgeColor(), tone)
}

/**
 * The colour a picture is mostly made of, found by weighing every pixel's hue
 * rather than ranking quantiser swatches (which split one wall into several
 * near-identical swatches and let a small warm patch outrank it).
 *
 * Near-black, near-white and grey pixels carry no hue and are ignored; the rest
 * vote for their hue in proportion to how colourful and how mid-toned they are.
 * Neighbouring hue bins are summed so one colour split across a bin edge still
 * wins. Null when almost nothing in the picture has a colour.
 */
private fun Bitmap.dominantHueTone(): Color? {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)

    val weight = FloatArray(HUE_BINS)
    val sat = FloatArray(HUE_BINS)
    val light = FloatArray(HUE_BINS)
    val sinH = FloatArray(HUE_BINS)
    val cosH = FloatArray(HUE_BINS)
    val hsl = FloatArray(3)
    for (pixel in pixels) {
        ColorUtils.colorToHSL(pixel, hsl)
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]
        if (l < HUE_MIN_LIGHTNESS || l > HUE_MAX_LIGHTNESS || s < HUE_MIN_SATURATION) continue
        val w = s * (1f - kotlin.math.abs(2f * l - 1f))
        val bin = (h / 360f * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
        val rad = Math.toRadians(h.toDouble())
        weight[bin] += w
        sat[bin] += s * w
        light[bin] += l * w
        sinH[bin] += (kotlin.math.sin(rad) * w).toFloat()
        cosH[bin] += (kotlin.math.cos(rad) * w).toFloat()
    }

    fun wrapBin(i: Int) = (i + HUE_BINS) % HUE_BINS
    var best = -1
    var bestScore = 0f
    for (i in 0 until HUE_BINS) {
        val score = weight[wrapBin(i - 1)] * 0.5f + weight[i] + weight[wrapBin(i + 1)] * 0.5f
        if (score > bestScore) { bestScore = score; best = i }
    }
    if (best < 0 || bestScore < pixels.size * HUE_MIN_COVERAGE) return null

    var w = 0f; var sSum = 0f; var lSum = 0f; var sinSum = 0f; var cosSum = 0f
    for (i in intArrayOf(wrapBin(best - 1), best, wrapBin(best + 1))) {
        w += weight[i]; sSum += sat[i]; lSum += light[i]; sinSum += sinH[i]; cosSum += cosH[i]
    }
    if (w <= 0f) return null
    var hue = Math.toDegrees(kotlin.math.atan2(sinSum.toDouble(), cosSum.toDouble())).toFloat()
    if (hue < 0f) hue += 360f
    return Color(ColorUtils.HSLToColor(floatArrayOf(hue, sSum / w, lSum / w)))
}

private const val HUE_BINS = 24
private const val HUE_MIN_LIGHTNESS = 0.12f
private const val HUE_MAX_LIGHTNESS = 0.90f
private const val HUE_MIN_SATURATION = 0.18f
/** Share of the picture that must have a real colour before it is trusted over the swatches. */
private const val HUE_MIN_COVERAGE = 0.02f

private const val SWATCH_COUNT = 24
private const val MIN_ACCENT_POPULATION_RATIO = 0.12f
private const val MIN_ACCENT_SATURATION = 0.18f
private const val TONE_MIN_POPULATION_RATIO = 0.30f
private const val TONE_MIN_LIGHTNESS = 0.15f
private const val TONE_MAX_LIGHTNESS = 0.85f
private const val TONE_SATURATION_BASE = 0.35f

/**
 * The mean of the artwork's bottom band — what a blur wide enough to lose the
 * picture leaves behind at that edge.
 *
 * A flat mean rather than a quantised swatch on purpose: a blur has no notion
 * of which colour is *important*, and the page under the artwork has to match
 * what the blur actually produced, not what the picture is about.
 */
private fun Bitmap.bottomEdgeColor(): Color {
    val band = (height * EDGE_BAND).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    getPixels(pixels, 0, width, 0, height - band, width, band)

    var red = 0L
    var green = 0L
    var blue = 0L
    pixels.forEach { pixel ->
        red += (pixel shr 16) and 0xFF
        green += (pixel shr 8) and 0xFF
        blue += pixel and 0xFF
    }
    val count = pixels.size.coerceAtLeast(1)
    return Color(
        red = (red / count).toInt(),
        green = (green / count).toInt(),
        blue = (blue / count).toInt(),
    )
}

/** How much of the artwork's height the edge colour is read from. */
private const val EDGE_BAND = 0.18f

private fun Seed.toPalette(dark: Boolean, fromTone: Boolean = false): ArtworkPalette = toThemePalette(dark, fromTone).copy(
    // The dominant colour rather than the vibrant one: a small bright patch of
    // the sleeve spread over every glass surface reads far louder than it is.
    glass = dominant.withHsl(
        saturation = { (it * 1.25f).coerceAtMost(0.85f) },
        lightness = { it.coerceIn(0.35f, 0.60f) },
    ),
)

private fun Seed.toThemePalette(dark: Boolean, fromTone: Boolean = false): ArtworkPalette = if (dark) {
    val accent = vibrant ?: Color(0xFFE0E0E0)
    // [fromTone] derives the whole tint from the picture's overall colour.
    val base = if (fromTone) tone else dominant
    ArtworkPalette(
        // Deep enough that white body text clears contrast on any sleeve, but
        // not so deep the hue is gone — the whole point is that the page is
        // recognisably *this* record's colour.
        background = base.withHsl(saturation = { it.coerceIn(0.20f, 0.62f) }, lightness = { 0.13f }),
        // Follows the edge's own brightness within a band that stays clear of
        // white body text at the top and of [background] at the bottom: a
        // sleeve that ends dark hands over almost invisibly, one that ends
        // bright leaves a page that is visibly lit from under the artwork.
        wash = if (fromTone) {
            tone.withHsl(
                saturation = { (it * 1.1f).coerceAtMost(0.65f) },
                lightness = { it.coerceIn(0.16f, 0.30f) },
            )
        } else {
            edge.withHsl(
                saturation = { it.coerceIn(0.18f, 0.58f) },
                lightness = { it.coerceIn(0.14f, 0.24f) },
            )
        },
        elevated = base.withHsl(saturation = { it.coerceIn(0.20f, 0.62f) }, lightness = { 0.22f }),
        accent = accent.withHsl(
            saturation = { it.coerceIn(MIN_ACCENT_SATURATION, 0.78f) },
            lightness = { it.coerceIn(0.62f, 0.78f) },
        ),
        onBackground = Color.White,
        // Well above the grey the untinted screens use for secondary text. A
        // tint is a *coloured* background, not a black one, so the contrast a
        // dim grey has against black is not the contrast it has here — artist
        // names were sinking into the wash on mid-toned sleeves.
        onBackgroundVariant = Color.White.copy(alpha = 0.80f),
        divider = Color.White.copy(alpha = 0.12f),
    )
} else {
    val accent = vibrant ?: Color(0xFF424242)
    val base = if (fromTone) tone else dominant
    ArtworkPalette(
        background = base.withHsl(saturation = { it.coerceIn(0.14f, 0.50f) }, lightness = { 0.91f }),
        wash = if (fromTone) {
            tone.withHsl(
                saturation = { it.coerceAtMost(0.50f) },
                lightness = { it.coerceIn(0.78f, 0.90f) },
            )
        } else {
            edge.withHsl(
                saturation = { it.coerceIn(0.12f, 0.46f) },
                lightness = { it.coerceIn(0.78f, 0.90f) },
            )
        },
        elevated = base.withHsl(saturation = { it.coerceIn(0.14f, 0.50f) }, lightness = { 0.83f }),
        accent = accent.withHsl(
            saturation = { it.coerceIn(MIN_ACCENT_SATURATION, 0.78f) },
            lightness = { it.coerceIn(0.30f, 0.44f) },
        ),
        onBackground = Color.Black,
        onBackgroundVariant = Color.Black.copy(alpha = 0.70f),
        divider = Color.Black.copy(alpha = 0.10f),
    )
}

private fun Color.withHsl(
    saturation: (Float) -> Float = { it },
    lightness: (Float) -> Float = { it },
): Color {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }
    hsl[1] = saturation(hsl[1]).coerceIn(0f, 1f)
    hsl[2] = lightness(hsl[2]).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Perceived brightness, used only to tell a dark theme from a light one. */
private fun Color.luminance(): Float = ColorUtils.calculateLuminance(toArgb()).toFloat()
