package io.github.youndie.haul

/**
 * Where a picture's dark version is served (B-181): beside the light one, the same address with `.dark.svg` for its
 * `.svg` — `/images/art/p_headphones_0_silver_e6e4ff.3f1c9a0e5b.svg` has it at
 * `/images/art/p_headphones_0_silver_e6e4ff.3f1c9a0e5b.dark.svg`. A tree names the light address only; the client
 * asks for the dark one on a dark page, and the server answers it for every SVG it serves: the seed's pictures and
 * the sample products' stored photos are drawn in both schemes, and a stored SVG with no dark version of its own is
 * answered with its light one. A photo that is not an SVG — a seller's raster upload — has no dark version.
 *
 * Both halves read this rule: the server to serve the dark address, the client to ask for it.
 */
public object PictureVersions {
    private const val SVG = ".svg"

    /** What a dark version's address ends with. */
    public const val DARK_SVG: String = ".dark.svg"

    /** The address of [address]'s dark version, or `null` when it has none: it is no SVG, or already the dark one. */
    public fun dark(address: String): String? =
        if (address.endsWith(SVG) && !address.endsWith(DARK_SVG)) address.removeSuffix(SVG) + DARK_SVG else null

    /** The light picture whose dark version is at [address], or `null` when [address] is no dark version's. */
    public fun light(address: String): String? =
        if (address.endsWith(DARK_SVG)) address.removeSuffix(DARK_SVG) + SVG else null
}
