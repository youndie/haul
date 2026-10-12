package io.github.youndie.haul.seed

/**
 * What a picture's object is drawn with: its [body] colour — the product's colour, or the motif's own — a
 * [shade] and a [light] of it, and an [accent] from the canvas's roles that is not the body's.
 */
internal class Paint(
    val body: String,
    val shade: String,
    val light: String,
    val accent: String,
) {
    companion object {
        // The canvas's roles (research §1.6): Ink, white, Cobalt, Acid, Hot, and the sale red.
        const val INK = "#0F0F0F"
        const val WHITE = "#FFFFFF"
        const val COBALT = "#2F2BFF"
        const val ACID = "#DFFF3A"
        const val HOT = "#FF3D2E"

        /** A leaf's green: no role has one, so a plant is drawn in the seed's Sage. */
        const val LEAF = "#7FA77A"
    }
}

/**
 * One object of the seed's pictures, drawn flat in a 100 × 100 box ([SeedArt] places and sizes it): [plain] is
 * its body colour when the product names none, [accent] the canvas role its details take.
 */
internal class Motif(
    val slug: String,
    val plain: String,
    val accent: String,
    val draw: Svg.(Paint) -> Unit,
)

/**
 * The seed's objects: one per category of the sample catalog's leaves (research §6) and one per top-level
 * category, drawn from rectangles, circles and a few curves, in the canvas's flat style — no gradient, no
 * outline, an ink detail here and there. [PARCEL] is the picture of a category the seed does not know.
 */
@Suppress("LongMethod", "MagicNumber")
internal object SeedMotifs {
    private const val INK = Paint.INK
    private const val WHITE = Paint.WHITE
    private const val PAPER = "#F5F3EE"

    val PARCEL =
        Motif("parcel", "#D9C3A0", Paint.COBALT) { p ->
            rect(18, 30, 64, 54, p.body, rx = 4)
            path("M18,30 L30,16 L70,16 L82,30 Z", p.light)
            rect(44, 16, 12, 68, p.shade)
            rect(56, 58, 20, 14, WHITE, rx = 2)
            rect(59, 62, 14, 2, p.accent)
            rect(59, 67, 9, 2, INK, opacity = 0.4)
        }

    private val headphones =
        Motif("headphones", "#232329", Paint.ACID) { p ->
            stroke("M24,58 C24,18 76,18 76,58", p.body, 7)
            stroke("M28,52 C30,28 70,28 72,52", p.shade, 2, opacity = 0.6)
            rect(14, 50, 20, 34, p.body, rx = 9)
            rect(66, 50, 20, 34, p.body, rx = 9)
            rect(30, 54, 8, 26, p.shade, rx = 4)
            rect(62, 54, 8, 26, p.shade, rx = 4)
            circle(24, 67, 3, p.accent)
        }

    private val speakers =
        Motif("speakers", "#2A2B31", Paint.ACID) { p ->
            rect(28, 12, 44, 76, p.body, rx = 9)
            circle(50, 62, 15, p.shade)
            circle(50, 62, 7, INK)
            circle(50, 31, 8, p.shade)
            circle(50, 31, 3, INK)
            circle(64, 19, 2, p.accent)
        }

    private val tvs =
        Motif("tvs", "#2A2B31", Paint.COBALT) { p ->
            rect(8, 18, 84, 54, p.body, rx = 4)
            rect(12, 22, 76, 46, p.accent, rx = 2)
            circle(66, 36, 7, Paint.ACID)
            path("M12,68 L40,44 L56,58 L66,50 L88,68 Z", INK, opacity = 0.35)
            rect(46, 72, 8, 9, p.shade)
            rect(32, 81, 36, 4, p.body, rx = 2)
        }

    private val projectors =
        Motif("projectors", "#E9E7E1", Paint.COBALT) { p ->
            path("M38,48 L4,22 L4,82 L38,60 Z", Paint.ACID, opacity = 0.55)
            rect(22, 38, 66, 32, p.body, rx = 9)
            circle(38, 54, 11, p.shade)
            circle(38, 54, 6, INK)
            rect(58, 46, 22, 3, p.shade, rx = 1.5)
            rect(58, 52, 22, 3, p.shade, rx = 1.5)
            rect(58, 58, 14, 3, p.accent, rx = 1.5)
            rect(30, 70, 8, 5, INK, rx = 1)
            rect(72, 70, 8, 5, INK, rx = 1)
        }

    private val bedding =
        Motif("duvet-covers", "#EDE4D3", Paint.COBALT) { p ->
            rect(12, 62, 76, 16, p.body, rx = 5)
            rect(16, 46, 68, 16, p.shade, rx = 5)
            rect(20, 30, 60, 16, p.body, rx = 5)
            rect(20, 36, 60, 2, p.accent, opacity = 0.7)
            rect(12, 68, 76, 2, p.accent, opacity = 0.7)
            rect(16, 52, 68, 2, WHITE, opacity = 0.6)
        }

    private val pillows =
        Motif("pillows", "#EDE4D3", Paint.HOT) { p ->
            path("M16,30 Q50,22 84,30 Q78,52 84,74 Q50,82 16,74 Q22,52 16,30 Z", p.body)
            path("M24,36 Q50,30 76,36 Q71,52 76,68 Q50,74 24,68 Q29,52 24,36 Z", p.shade, opacity = 0.35)
            circle(50, 52, 4, p.accent)
        }

    private val mugs =
        Motif("mugs", "#F6F3EC", Paint.COBALT) { p ->
            stroke("M60,34 C66,24 60,18 64,10", INK, 2.5, opacity = 0.25)
            stroke("M46,34 C52,24 46,18 50,10", INK, 2.5, opacity = 0.25)
            stroke("M70,46 C86,46 86,72 70,72", p.shade, 7)
            rect(26, 36, 46, 50, p.body, rx = 7)
            ellipse(49, 37, 23, 4, p.shade)
            rect(26, 56, 46, 8, p.accent)
        }

    private val cookware =
        Motif("cookware", "#2F3036", Paint.HOT) { p ->
            rect(4, 50, 16, 6, INK, rx = 3)
            rect(80, 50, 16, 6, INK, rx = 3)
            rect(18, 44, 64, 38, p.body, rx = 7)
            ellipse(50, 42, 36, 7, p.shade)
            rect(44, 30, 12, 8, p.accent, rx = 3)
            rect(18, 60, 64, 3, WHITE, opacity = 0.25)
        }

    private val sneaker =
        Motif("running-shoes", "#F2F0EA", Paint.HOT) { p ->
            rect(8, 66, 84, 12, WHITE, rx = 6)
            rect(8, 72, 84, 6, INK, rx = 3, opacity = 0.15)
            path(
                "M10,66 L10,48 C10,40 18,38 24,42 L38,48 L52,30 C56,25 62,26 66,31 L86,54 C93,58 93,66 88,66 Z",
                p.body,
            )
            path("M24,62 C40,56 58,58 80,52 L84,58 C64,64 44,64 26,66 Z", p.accent)
            stroke("M46,40 L54,46 M50,36 L58,42 M54,32 L62,38", INK, 2.5, opacity = 0.7)
            path("M10,48 C10,42 16,40 20,42 L20,62 L10,62 Z", p.shade)
        }

    private val jacket =
        Motif("running-jackets", "#2F2BFF", Paint.ACID) { p ->
            path("M36,14 L50,20 L64,14 L84,24 L92,62 L80,64 L74,40 L74,88 L26,88 L26,40 L20,64 L8,62 L16,24 Z", p.body)
            path("M36,14 L50,20 L64,14 L60,26 L50,30 L40,26 Z", p.shade)
            rect(49, 28, 2, 60, INK, opacity = 0.6)
            rect(32, 60, 12, 2, p.accent)
            rect(56, 60, 12, 2, p.accent)
            rect(78, 56, 13, 4, p.accent, rx = 2)
            rect(9, 56, 13, 4, p.accent, rx = 2)
        }

    private val yogaMat =
        Motif("yoga-mats", "#9DB59A", Paint.COBALT) { p ->
            rect(12, 34, 66, 36, p.body, rx = 4)
            rect(12, 34, 66, 7, p.light, opacity = 0.6)
            ellipse(78, 52, 10, 18, p.shade)
            ring(78, 52, 7, p.light, 2.5)
            circle(78, 52, 3, p.shade)
            rect(30, 32, 6, 40, p.accent, rx = 2)
            rect(56, 32, 6, 40, p.accent, rx = 2)
        }

    private val dumbbells =
        Motif("dumbbells", "#2F3036", Paint.ACID) { p ->
            rect(24, 47, 52, 6, INK, rx = 3)
            rect(10, 30, 14, 40, p.body, rx = 4)
            rect(24, 36, 7, 28, p.shade, rx = 2)
            rect(76, 30, 14, 40, p.body, rx = 4)
            rect(69, 36, 7, 28, p.shade, rx = 2)
            rect(13, 34, 3, 32, p.accent, rx = 1.5)
            rect(84, 34, 3, 32, p.accent, rx = 1.5)
        }

    private val tshirt =
        Motif("fashion", "#F6F3EC", Paint.HOT) { p ->
            path(
                "M32,16 L42,12 C44,20 56,20 58,12 L68,16 L90,32 L80,46 L72,40 L72,88 L28,88 L28,40 L20,46 L10,32 Z",
                p.body,
            )
            path("M42,12 C44,20 56,20 58,12 L55,11 C53,16 47,16 45,11 Z", p.shade)
            rect(56, 44, 10, 10, p.accent, rx = 2)
            rect(28, 80, 44, 3, p.shade, opacity = 0.6)
        }

    private val beauty =
        Motif("beauty", "#F1C9C0", Paint.HOT) { p ->
            rect(18, 36, 30, 50, p.body, rx = 8)
            rect(25, 22, 16, 15, INK, rx = 3)
            rect(23, 54, 20, 16, WHITE, rx = 2, opacity = 0.75)
            rect(26, 59, 14, 2, p.shade)
            rect(56, 54, 24, 32, INK, rx = 3)
            rect(59, 40, 18, 15, p.shade, rx = 2)
            path("M61,40 L61,24 C61,20 75,16 75,22 L75,40 Z", p.accent)
        }

    private val toys =
        Motif("kids-toys", "#FF3D2E", Paint.COBALT) { p ->
            rect(14, 56, 32, 30, p.body, rx = 4)
            rect(52, 56, 32, 30, p.accent, rx = 4)
            rect(33, 24, 32, 32, Paint.ACID, rx = 4)
            circle(30, 71, 6, WHITE, opacity = 0.8)
            path("M68,63 L76,79 L60,79 Z", WHITE, opacity = 0.8)
            rect(43, 34, 12, 12, INK, rx = 1, opacity = 0.75)
        }

    private val grocery =
        Motif("grocery", "#D9C3A0", Paint.HOT) { p ->
            stroke("M30,40 L22,14", Paint.LEAF, 4)
            stroke("M34,40 L40,12", "#E2B44B", 6)
            path("M58,40 L76,16 L82,22 L64,42 Z", p.accent)
            stroke("M74,18 L80,10 M78,20 L88,16", Paint.LEAF, 3)
            path("M20,38 L80,38 L76,88 L24,88 Z", p.body)
            rect(20, 38, 60, 8, p.shade)
            rect(38, 58, 24, 14, WHITE, rx = 2, opacity = 0.7)
        }

    private val car =
        Motif("auto", "#2F2BFF", Paint.ACID) { p ->
            path(
                "M6,66 L8,52 C8,48 12,46 16,46 L28,44 L40,30 C42,28 44,28 46,28 L66,28 C70,28 72,30 74,32 L84,44 C90,45 94,48 94,54 L94,66 Z",
                p.body,
            )
            path("M33,44 L43,32 L56,32 L56,44 Z", p.light)
            path("M60,32 L68,32 C70,32 71,33 72,34 L80,44 L60,44 Z", p.light)
            rect(84, 50, 8, 5, p.accent, rx = 2)
            circle(27, 68, 11, INK)
            circle(27, 68, 5, WHITE, opacity = 0.85)
            circle(73, 68, 11, INK)
            circle(73, 68, 5, WHITE, opacity = 0.85)
        }

    private val books =
        Motif("books", "#2F2BFF", Paint.ACID) { p ->
            rect(12, 70, 76, 14, p.body, rx = 2)
            rect(16, 56, 66, 14, p.accent, rx = 2)
            rect(10, 42, 72, 14, Paint.HOT, rx = 2)
            rect(18, 28, 60, 14, p.shade, rx = 2)
            rect(76, 72, 10, 10, WHITE, opacity = 0.85)
            rect(74, 58, 6, 10, WHITE, opacity = 0.85)
            rect(72, 44, 8, 10, WHITE, opacity = 0.85)
            rect(70, 30, 6, 10, WHITE, opacity = 0.85)
        }

    private val pets =
        Motif("pets", "#FF3D2E", Paint.ACID) { p ->
            path("M14,62 L86,62 L78,86 L22,86 Z", p.body)
            rect(14, 60, 72, 6, p.shade, rx = 3)
            circle(50, 74, 5, WHITE, opacity = 0.7)
            group(50, 36, degrees = -18) {
                rect(-22, -5, 44, 10, p.accent, rx = 5)
                circle(-22, -6, 7, p.accent)
                circle(-22, 6, 7, p.accent)
                circle(22, -6, 7, p.accent)
                circle(22, 6, 7, p.accent)
            }
        }

    private val garden =
        Motif("garden", "#D9744A", Paint.ACID) { p ->
            path("M50,58 C30,50 22,30 30,16 C44,22 52,40 50,58 Z", Paint.LEAF)
            path("M50,58 C68,48 78,30 72,14 C58,20 48,40 50,58 Z", Paint.LEAF)
            stroke("M50,58 L50,26", "#5E8A5A", 3)
            path("M26,58 L74,58 L68,90 L32,90 Z", p.body)
            rect(22, 56, 56, 9, p.shade, rx = 2)
            circle(64, 22, 4, p.accent)
        }

    private val office =
        Motif("office", "#2F2BFF", Paint.ACID) { p ->
            group(40, 44, degrees = -14) { rect(-3, -34, 6, 40, p.accent, rx = 1) }
            group(54, 42, degrees = 8) { rect(-3, -36, 6, 40, Paint.HOT, rx = 1) }
            group(62, 46, degrees = 22) { rect(-3, -30, 6, 34, INK, rx = 1) }
            rect(26, 46, 44, 42, p.body, rx = 5)
            rect(26, 54, 44, 3, WHITE, opacity = 0.35)
            rect(26, 74, 44, 3, WHITE, opacity = 0.35)
        }

    private val health =
        Motif("health", "#F6F3EC", Paint.HOT) { p ->
            stroke("M38,32 L38,22 L62,22 L62,32", INK, 5)
            rect(14, 30, 72, 54, p.body, rx = 8)
            rect(14, 30, 72, 10, p.shade, rx = 8, opacity = 0.5)
            rect(44, 46, 12, 32, p.accent, rx = 2)
            rect(34, 56, 32, 12, p.accent, rx = 2)
        }

    private val music =
        Motif("music", "#2F2BFF", Paint.ACID) { p ->
            circle(50, 50, 38, INK)
            ring(50, 50, 30, WHITE, 1, opacity = 0.18)
            ring(50, 50, 24, WHITE, 1, opacity = 0.18)
            path("M50,12 A38,38 0 0 1 86,38 L50,50 Z", WHITE, opacity = 0.08)
            circle(50, 50, 13, p.body)
            circle(50, 50, 6, p.accent)
            circle(50, 50, 2, INK)
        }

    private val movies =
        Motif("movies", "#2A2B31", Paint.ACID) { p ->
            rect(16, 44, 68, 42, p.body, rx = 3)
            rect(16, 44, 68, 10, WHITE)
            stroke("M24,44 L32,54 M44,44 L52,54 M64,44 L72,54", INK, 5)
            group(16, 40, degrees = -16) {
                rect(0, -10, 68, 10, WHITE)
                stroke("M8,-10 L16,0 M28,-10 L36,0 M48,-10 L56,0", INK, 5)
            }
            rect(24, 64, 30, 3, p.accent)
            rect(24, 72, 20, 3, WHITE, opacity = 0.5)
        }

    private val games =
        Motif("games", "#2A2B31", Paint.ACID) { p ->
            path(
                "M30,32 L70,32 C84,32 92,48 94,64 C96,78 84,84 76,74 L68,64 L32,64 L24,74 C16,84 4,78 6,64 C8,48 16,32 30,32 Z",
                p.body,
            )
            rect(24, 44, 6, 18, p.light, rx = 1)
            rect(18, 50, 18, 6, p.light, rx = 1)
            circle(70, 46, 4, p.accent)
            circle(78, 54, 4, Paint.HOT)
            circle(62, 54, 4, Paint.COBALT)
            circle(70, 62, 4, WHITE)
        }

    private val tools =
        Motif("tools", "#FF3D2E", Paint.ACID) { p ->
            group(50, 52, degrees = 40) {
                rect(-4, -10, 8, 46, p.body, rx = 3)
                rect(-18, -26, 36, 14, INK, rx = 2)
            }
            group(50, 52, degrees = -40) {
                rect(-4, -26, 8, 52, p.accent, rx = 3)
                circle(0, -30, 10, p.accent)
                rect(-3, -42, 6, 12, PAPER)
            }
        }

    private val baby =
        Motif("baby", "#F6F3EC", Paint.COBALT) { p ->
            path("M44,12 C44,6 56,6 56,12 L58,22 L42,22 Z", "#E8C9A8")
            rect(36, 22, 28, 10, p.accent, rx = 3)
            rect(34, 32, 32, 56, p.body, rx = 10)
            rect(34, 44, 32, 30, p.shade, opacity = 0.25)
            stroke("M40,48 L46,48 M40,56 L48,56 M40,64 L46,64", INK, 2, opacity = 0.5)
        }

    private val jewelry =
        Motif("jewelry", "#E2B44B", Paint.COBALT) { p ->
            ring(50, 62, 22, p.body, 8)
            ring(50, 62, 22, WHITE, 1.5, opacity = 0.35)
            path("M38,36 L44,26 L56,26 L62,36 L50,46 Z", p.accent)
            path("M44,26 L50,36 L56,26 Z", WHITE, opacity = 0.35)
            path("M38,36 L62,36 L50,46 Z", INK, opacity = 0.2)
            path("M70,22 L72,16 L74,22 L80,24 L74,26 L72,32 L70,26 L64,24 Z", Paint.ACID)
        }

    private val luggage =
        Motif("luggage", "#2F2BFF", Paint.ACID) { p ->
            stroke("M40,24 L40,14 L60,14 L60,24", INK, 4)
            rect(24, 24, 52, 60, p.body, rx = 8)
            rect(36, 24, 4, 60, p.shade)
            rect(60, 24, 4, 60, p.shade)
            rect(24, 46, 52, 6, p.accent)
            circle(32, 88, 4, INK)
            circle(68, 88, 4, INK)
        }

    private val crafts =
        Motif("crafts", "#FF3D2E", Paint.ACID) { p ->
            stroke("M18,30 L86,72", INK, 3)
            stroke("M14,46 L84,22", INK, 3)
            circle(48, 58, 26, p.body)
            stroke("M26,48 C40,40 60,42 72,52", p.shade, 3)
            stroke("M24,60 C40,54 60,56 74,64", p.shade, 3)
            stroke("M30,74 C42,68 58,70 68,78", p.shade, 3)
            stroke("M70,74 C80,80 88,76 92,84", p.body, 3)
            circle(86, 72, 3, p.accent)
        }

    private val tent =
        Motif("outdoors", "#E0703A", Paint.ACID) { p ->
            path("M50,16 L90,82 L10,82 Z", p.body)
            path("M50,16 L90,82 L70,82 Z", p.shade)
            path("M50,48 L62,82 L38,82 Z", INK, opacity = 0.75)
            rect(4, 82, 92, 3, INK, rx = 1.5, opacity = 0.4)
            stroke("M50,16 L50,6", INK, 2)
            path("M50,6 L62,9 L50,12 Z", p.accent)
        }

    private val party =
        Motif("party", "#FF3D2E", Paint.ACID) { p ->
            stroke("M30,52 C34,66 40,74 46,90", INK, 1.5, opacity = 0.5)
            stroke("M58,46 C56,62 52,76 48,90", INK, 1.5, opacity = 0.5)
            stroke("M76,60 C70,72 60,82 50,90", INK, 1.5, opacity = 0.5)
            ellipse(30, 34, 16, 19, p.body)
            ellipse(58, 28, 16, 19, p.accent)
            ellipse(76, 46, 13, 16, Paint.COBALT)
            ellipse(25, 28, 4, 6, WHITE, opacity = 0.4)
        }

    private val armchair =
        Motif("furniture", "#2F2BFF", Paint.ACID) { p ->
            rect(24, 18, 52, 46, p.body, rx = 12)
            rect(12, 44, 18, 32, p.shade, rx = 8)
            rect(70, 44, 18, 32, p.shade, rx = 8)
            rect(26, 54, 48, 20, p.body, rx = 6)
            rect(26, 54, 48, 5, WHITE, opacity = 0.2)
            rect(18, 76, 6, 12, INK, rx = 2)
            rect(76, 76, 6, 12, INK, rx = 2)
            circle(50, 36, 3, p.accent)
        }

    private val lamp =
        Motif("lighting", "#F6F3EC", Paint.ACID) { p ->
            circle(50, 44, 30, p.accent, opacity = 0.45)
            rect(48, 40, 4, 40, INK)
            ellipse(50, 84, 18, 5, INK)
            path("M30,42 L38,14 L62,14 L70,42 Z", p.body)
            path("M30,42 L70,42 L68,38 L32,38 Z", p.shade)
        }

    private val washer =
        Motif("appliances", "#F6F3EC", Paint.COBALT) { p ->
            rect(20, 12, 60, 76, p.body, rx = 6)
            rect(20, 12, 60, 14, p.shade, rx = 6)
            circle(30, 19, 3, p.accent)
            rect(56, 17, 18, 4, INK, rx = 2, opacity = 0.6)
            circle(50, 56, 22, p.shade)
            circle(50, 56, 16, p.accent, opacity = 0.65)
            path("M38,52 C44,46 56,46 62,52", WHITE, opacity = 0.5)
        }

    private val laptop =
        Motif("computers", "#C8C9CE", Paint.COBALT) { p ->
            rect(18, 20, 64, 44, p.shade, rx = 4)
            rect(22, 24, 56, 36, p.accent, rx = 2)
            rect(28, 30, 26, 4, WHITE, rx = 2, opacity = 0.85)
            rect(28, 38, 18, 4, Paint.ACID, rx = 2)
            path("M8,66 L92,66 L88,74 L12,74 Z", p.body)
            rect(42, 66, 16, 3, INK, rx = 1.5, opacity = 0.3)
        }

    private val phone =
        Motif("phones", "#232329", Paint.COBALT) { p ->
            rect(30, 10, 40, 80, p.body, rx = 9)
            rect(34, 16, 32, 68, p.accent, rx = 5)
            rect(44, 18, 12, 4, p.body, rx = 2)
            circle(50, 46, 9, Paint.ACID)
            rect(38, 64, 24, 4, WHITE, rx = 2, opacity = 0.8)
            rect(38, 72, 16, 4, WHITE, rx = 2, opacity = 0.5)
        }

    private val camera =
        Motif("cameras", "#232329", Paint.HOT) { p ->
            rect(28, 24, 22, 12, p.shade, rx = 3)
            rect(12, 32, 76, 50, p.body, rx = 9)
            rect(12, 44, 76, 4, WHITE, opacity = 0.15)
            circle(50, 58, 18, p.shade)
            circle(50, 58, 12, INK)
            circle(46, 54, 4, WHITE, opacity = 0.6)
            rect(70, 38, 10, 6, p.accent, rx = 2)
        }

    private val watch =
        Motif("watches", "#2A2B31", Paint.HOT) { p ->
            rect(38, 6, 24, 30, p.body, rx = 4)
            rect(38, 64, 24, 30, p.body, rx = 4)
            circle(50, 50, 24, p.shade)
            circle(50, 50, 19, WHITE)
            rect(73, 46, 5, 8, p.shade, rx = 2)
            stroke("M50,50 L50,37 M50,50 L59,55", INK, 2.5)
            circle(50, 50, 2, p.accent)
        }

    private val tote =
        Motif("bags", "#D9C3A0", Paint.HOT) { p ->
            stroke("M34,40 C34,18 66,18 66,40", p.shade, 5)
            path("M18,38 L82,38 L76,90 L24,90 Z", p.body)
            rect(18, 38, 64, 8, p.shade)
            rect(36, 58, 28, 4, p.accent)
            rect(42, 66, 16, 4, p.accent)
        }

    /** A falling leaf, about 40 units long, for the sale's picture ([SeedArt]). */
    fun Svg.leaf(colour: String) {
        path("M0,-22 C14,-10 12,12 0,22 C-12,12 -14,-10 0,-22 Z", colour)
        stroke("M0,-16 L0,26", colour, 2)
    }

    /**
     * Each motif by the category whose products it draws: the sample catalog's leaves by their own (research §6),
     * every other category by its top level's ([SeedArt] walks up). Electronics, Home & Kitchen and Sports
     * draw a leaf of theirs; Shoes draws the running shoe.
     */
    val byCategory: Map<String, Motif> =
        listOf(
            headphones,
            speakers,
            tvs,
            projectors,
            bedding,
            pillows,
            mugs,
            cookware,
            sneaker,
            jacket,
            yogaMat,
            dumbbells,
            tshirt,
            beauty,
            toys,
            grocery,
            car,
            books,
            pets,
            garden,
            office,
            health,
            music,
            movies,
            games,
            tools,
            baby,
            jewelry,
            luggage,
            crafts,
            tent,
            party,
            armchair,
            lamp,
            washer,
            laptop,
            phone,
            camera,
            watch,
            tote,
        ).associateBy { it.slug } +
            mapOf(
                "electronics" to headphones,
                "home-kitchen" to mugs,
                "sports" to sneaker,
                "shoes" to sneaker,
            )

    /** Every motif once, by its own slug: what a picture's address names. */
    val bySlug: Map<String, Motif> = (byCategory.values + PARCEL).associateBy { it.slug }
}
