package cn.edu.gzus.qingke.data

internal data class GrayPng(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
)

private val LyuapCaptchaTemplates = mapOf(
    "0" to listOf("011110111111110011110011110001110011111011011110", "011110111111110011110011110011110011111011011110"),
    "1" to listOf("111100111100001100001100001100001100001100111111"),
    "2" to listOf("111110111111000111000111001110011100111000111111"),
    "3" to listOf("111111111111000001001111001111000011110011111111", "111111111111000011001111001111000011110111111111"),
    "4" to listOf("000110001110001010011010110010111111111111000010"),
    "5" to listOf("011111011110011000011111010111000001110111111110", "011111011110011000011111010111000001110111111111", "011111011110011000011111010111000001111111111111", "011111011110011000011111011111000011111111111111"),
    "6" to listOf("011111011111110000111110111111110001111011011111", "011111111111110000111110111111110011111011011111"),
    "7" to listOf("111111111111000011000111000110001110001110001100"),
    "8" to listOf("011110111111110001111111111111110001110011011110", "011111111111110011111111111111110011111011111111", "011111111111110011111111111111110011111111111111"),
    "9" to listOf("011110111111110011111011111111000011010111011110", "011110111111110011111011111111000011011111011110", "011110111111110011111111111111000011011111011110"),
    "+" to listOf("001100001100001100111111111111001100001100001100"),
    "*" to listOf("000100110101111111001110001110111111110101000100", "000100110101111111011111011111111111110101000100"),
    "=" to listOf("111111111111111111000000000000000000111111111111"),
)

internal fun solveLyuapCaptcha(bytes: ByteArray): String? {
    val png = decodePngGray(bytes) ?: return null
    val runs = inkColumns(png)
    if (runs.size < 3) return null
    val glyphs = runs.map { matchGlyph(png, it) ?: return null }
    val left = glyphs[0].toIntOrNull() ?: return null
    val op = glyphs[1]
    val right = glyphs.getOrNull(2)?.toIntOrNull() ?: return null
    return when (op) {
        "+" -> (left + right).toString()
        "*" -> (left * right).toString()
        "-" -> (left - right).toString()
        else -> null
    }
}

private fun inkColumns(png: GrayPng): List<IntRange> {
    val cols = IntArray(png.width) { x ->
        (0 until png.height).count { y -> png.pixels[y * png.width + x] < 250 }
    }
    val out = ArrayList<IntRange>(4)
    var start = -1
    for (x in cols.indices) {
        val ink = cols[x] >= 1
        if (ink && start < 0) start = x
        if (!ink && start >= 0) {
            out += start until x
            start = -1
        }
    }
    if (start >= 0) out += start until png.width
    return out
}

private fun matchGlyph(png: GrayPng, xs: IntRange): String? {
    val bits = glyphBits(png, xs) ?: return null
    var best: String? = null
    var bestDist = Int.MAX_VALUE
    LyuapCaptchaTemplates.forEach { (label, templates) ->
        templates.forEach { template ->
            val dist = hamming(bits, template)
            if (dist < bestDist) {
                bestDist = dist
                best = label
            }
        }
    }
    return if (bestDist <= 10) best else null
}

private fun glyphBits(png: GrayPng, xs: IntRange, gw: Int = 6, gh: Int = 8): String? {
    var y0 = png.height
    var y1 = -1
    for (y in 0 until png.height) {
        for (x in xs) {
            if (png.pixels[y * png.width + x] < 250) {
                if (y < y0) y0 = y
                if (y > y1) y1 = y
            }
        }
    }
    if (y1 < y0) return null
    val x0 = xs.first
    val x1 = xs.last
    val out = StringBuilder(gw * gh)
    for (gy in 0 until gh) {
        for (gx in 0 until gw) {
            val xa = x0 + gx * (x1 - x0 + 1) / gw
            val xb = x0 + (gx + 1) * (x1 - x0 + 1) / gw
            val ya = y0 + gy * (y1 - y0 + 1) / gh
            val yb = y0 + (gy + 1) * (y1 - y0 + 1) / gh
            var cells = 0
            var ink = 0
            var y = ya
            while (y < yb.coerceAtLeast(ya + 1)) {
                var x = xa
                while (x < xb.coerceAtLeast(xa + 1)) {
                    if (x in 0 until png.width && y in 0 until png.height) {
                        cells++
                        if (png.pixels[y * png.width + x] < 250) ink++
                    }
                    x++
                }
                y++
            }
            out.append(if (cells > 0 && ink * 2 >= cells) '1' else '0')
        }
    }
    return out.toString()
}

private fun hamming(left: String, right: String): Int {
    val n = minOf(left.length, right.length)
    var d = kotlin.math.abs(left.length - right.length)
    for (i in 0 until n) if (left[i] != right[i]) d++
    return d
}
