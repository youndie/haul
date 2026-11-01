package io.github.youndie.haul.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLengthUnit

// The seed's pictures drawn as vectors (B-181). Until B-181 Coil's SVG decoder rasterised each picture once, at the
// size of its box rounded up to 64 px (B-149), and the bitmap was what the page drew: scaled down to the box with a
// bilinear filter, scaled up 6 % under a hovered card's lean and 4 % in a photo's arrival, and kept at the larger size
// after a zoom out — never at one pixel per pixel, so never quite sharp. Now an SVG is parsed once by Skia's SVG
// renderer and recorded as a Skia picture: its drawing commands, not its pixels. The page replays the picture at draw
// time, through whatever the canvas's transform is — the page's density, a card's lean, a zoom — so its edges are
// rasterised where they land, at every size. Replaying a recorded picture skips the SVG tree's walk, which a frame of
// a scrolled grid would otherwise pay per tile («Measured in B-181»).

/** An SVG, parsed once and recorded: drawn at any size by [VectorPicturePainter], sharp at every one. */
public class VectorPicture internal constructor(
    internal val picture: Picture,
    /** The picture's own width and height, in the SVG's units: its aspect, and its size where nothing sizes it. */
    public val width: Float,
    public val height: Float,
) {
    /** Draws the picture into [canvas] at its own size; the canvas's transform sizes and places it. */
    internal fun draw(canvas: Canvas) {
        canvas.drawPicture(picture)
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
    val width = root.width.takeIf { it.unit == SVGLengthUnit.NUMBER || it.unit == SVGLengthUnit.PX }?.value ?: box?.width
    val height =
        root.height.takeIf { it.unit == SVGLengthUnit.NUMBER || it.unit == SVGLengthUnit.PX }?.value ?: box?.height
    if (width == null || height == null || width <= 0f || height <= 0f) return null
    svg.setContainerSize(width, height)
    val recorder = PictureRecorder()
    svg.render(recorder.beginRecording(Rect.makeWH(width, height)))
    return VectorPicture(recorder.finishRecordingAsPicture(), width, height)
}

/** Paints a [VectorPicture] into the painter's box, stretched to it: the picture replayed, never a bitmap of it. */
public class VectorPicturePainter(
    private val picture: VectorPicture,
) : Painter() {
    override val intrinsicSize: Size = Size(picture.width, picture.height)

    override fun DrawScope.onDraw() {
        val canvas = drawContext.canvas.nativeCanvas
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
        val bytes = source.source().use { it.readByteArray() }
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
