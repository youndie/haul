package io.github.youndie.haul.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.skiaCanvas
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLengthUnit
import kotlin.math.roundToInt

// The seed's pictures drawn as vectors (B-181). Until B-181 Coil's SVG decoder rasterised each picture once, at the
// size of its box rounded up to 64 px (B-149), and the bitmap was what the page drew: scaled down to the box with a
// bilinear filter, scaled up 6 % under a hovered card's lean and 4 % in a photo's arrival, and kept at the larger size
// after a zoom out — never at one pixel per pixel, so never quite sharp. Now an SVG is parsed once by Skia's SVG
// renderer and recorded as a Skia picture: its drawing commands, not its pixels. At draw time the picture is
// rasterised at exactly the device pixels it covers — read off the canvas's transform: the page's density, a zoom, a
// card's lean drawn in the draw phase — and that raster is kept and drawn one pixel per pixel while the size holds.
// Replaying the vectors on every frame instead was sharp too, but a scrolled grid of them dropped half its frames at
// 2× in software GL; the kept raster scrolls as the bitmaps did («Measured in B-181»). A transform that turns the
// picture (a banner's tilt) draws the vectors themselves.

/** An SVG, parsed once and recorded: drawn at any size by [VectorPicturePainter], sharp at every one. */
public class VectorPicture internal constructor(
    internal val picture: Picture,
    /** The picture's own width and height, in the SVG's units: its aspect, and its size where nothing sizes it. */
    public val width: Float,
    public val height: Float,
) {
    /** The rasters kept, newest last: a few sizes, as one picture is drawn in a card, a line and a dialog at once. */
    private val rasters = ArrayList<Pair<Long, Image>>(KEPT_RASTERS)

    /**
     * Draws the picture into [canvas] at its own size; the canvas's transform sizes and places it. Scaled and moved
     * only, it is drawn as a raster of the device pixels it covers, placed on whole pixels; turned or skewed, as
     * vectors.
     */
    internal fun draw(canvas: Canvas) {
        val m = canvas.localToDeviceAsMatrix33.mat
        val turned = m[1] != 0f || m[3] != 0f || m[6] != 0f || m[7] != 0f || m[0] <= 0f || m[4] <= 0f
        val w = (width * m[0]).roundToInt()
        val h = (height * m[4]).roundToInt()
        if (turned || w <= 0 || h <= 0 || w > MAX_RASTER || h > MAX_RASTER) {
            canvas.drawPicture(picture)
            return
        }
        val raster = rasterAt(w, h)
        canvas.save()
        canvas.resetMatrix()
        canvas.drawImage(raster, m[2].roundToInt().toFloat(), m[5].roundToInt().toFloat())
        canvas.restore()
    }

    private fun rasterAt(
        w: Int,
        h: Int,
    ): Image {
        val key = w.toLong() shl Int.SIZE_BITS or h.toLong()
        rasters.firstOrNull { it.first == key }?.let { return it.second }
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.scale(w / width, h / height)
        surface.canvas.drawPicture(picture)
        val raster = surface.makeImageSnapshot()
        surface.close()
        // A raster dropped here may still be in a frame recorded before: the recording holds its own reference.
        if (rasters.size == KEPT_RASTERS) rasters.removeAt(0).second.close()
        rasters += key to raster
        return raster
    }

    private companion object {
        const val KEPT_RASTERS = 3

        /** A side past which a picture is drawn as vectors rather than kept as a raster: no page draws one so large. */
        const val MAX_RASTER = 4096
    }
}

/**
 * [bytes] as a picture, or `null` when they are no SVG Skia can draw. Its size is the root's `width` and `height` when
 * they are absolute, else its `viewBox`'s.
 */
public fun vectorPicture(bytes: ByteArray): VectorPicture? {
    val svg = runCatching { SVGDOM(Data.makeFromBytes(bytes)) }.getOrNull() ?: return null
    val root = svg.root ?: return null
    val box = root.viewBox
    val width =
        root.width.takeIf { it.unit == SVGLengthUnit.NUMBER || it.unit == SVGLengthUnit.PX }?.value ?: box?.width
    val height =
        root.height.takeIf { it.unit == SVGLengthUnit.NUMBER || it.unit == SVGLengthUnit.PX }?.value ?: box?.height
    if (width == null || height == null || width <= 0f || height <= 0f) return null
    svg.setContainerSize(width, height)
    val recorder = PictureRecorder()
    svg.render(recorder.beginRecording(Rect.makeWH(width, height)))
    return VectorPicture(recorder.finishRecordingAsPicture(), width, height)
}

/** Paints a [VectorPicture] into the painter's box, stretched to it, at the device pixels the box covers. */
public class VectorPicturePainter(
    private val picture: VectorPicture,
) : Painter() {
    override val intrinsicSize: Size = Size(picture.width, picture.height)

    override fun DrawScope.onDraw() {
        val canvas = drawContext.canvas.skiaCanvas
        canvas.save()
        canvas.scale(size.width / picture.width, size.height / picture.height)
        picture.draw(canvas)
        canvas.restore()
    }
}

/** A picture as Coil holds it: drawn by Coil's painter into the box it is given, through the canvas's transform. */
private class VectorImage(
    val picture: VectorPicture,
) : coil3.Image {
    override val size: Long = picture.picture.approximateBytesUsed.toLong()
    override val width: Int = picture.width.toInt()
    override val height: Int = picture.height.toInt()
    override val shareable: Boolean = true

    override fun draw(canvas: Canvas) = picture.draw(canvas)
}

/**
 * Coil's decoder for an SVG — the seed's pictures and stored SVG photos, served as `image/svg+xml` — that records it
 * as a [VectorPicture] instead of rasterising it. The result does not depend on the size asked for, so it is not
 * sampled: the memory cache serves it to every box that asks for the same address.
 */
internal class VectorPictureDecoder(
    private val source: ImageSource,
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes =
            try {
                source.source().readByteArray()
            } finally {
                source.close()
            }
        val picture = checkNotNull(vectorPicture(bytes)) { "an SVG Skia cannot draw" }
        return DecodeResult(VectorImage(picture), isSampled = false)
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? = if (result.mimeType == SVG_TYPE) VectorPictureDecoder(result.source) else null
    }

    private companion object {
        const val SVG_TYPE = "image/svg+xml"
    }
}
