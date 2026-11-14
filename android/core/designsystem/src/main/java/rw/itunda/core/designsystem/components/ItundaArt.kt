package rw.itunda.core.designsystem.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG

/** The art sets shipped in assets/itunda: `color`/`line`/`duo`/`solid`/`orig` icons, `proto` (the prototype's own picks) and `faces`. */
enum class ItundaArtSet(internal val dir: String) { Color("icons/color"), Line("icons/line"), Duo("icons/duo"), Solid("icons/solid"), Orig("icons/orig"), Proto("proto"), Face("faces") }

private val parsed = LruCache<String, SVG>(256)

private fun load(context: Context, set: ItundaArtSet, name: String): SVG? {
    val key = "${set.dir}/$name"
    parsed.get(key)?.let { return it }
    return try {
        context.assets.open("itunda/$key.svg").use { SVG.getFromInputStream(it) }.also { parsed.put(key, it) }
    } catch (_: Exception) { null }
}

/**
 * Draws one of itunda's own illustrations (the itundaicons and itundaface libraries the prototype uses).
 * [tint] only affects `line`/`duo`/`solid` icons, which are drawn in `currentColor`; the coloured sets keep their own colours.
 * An unknown [name] draws nothing, so a typo shows up as a gap in review rather than a wrong picture.
 */
@Composable
fun ItundaArt(name: String, set: ItundaArtSet = ItundaArtSet.Color, size: Dp = 24.dp, tint: Color? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val svg = remember(set, name) { load(context, set, name) }
    val css = remember(tint) { tint?.let { "svg { color: #%06X }".format(it.toArgb() and 0xFFFFFF) } }
    Canvas(modifier.size(size)) {
        val s = svg ?: return@Canvas
        drawIntoCanvas { c ->
            val o = RenderOptions().viewPort(0f, 0f, this.size.width, this.size.height)
            if (css != null) o.css(css)
            s.renderToCanvas(c.nativeCanvas, o)
        }
    }
}
