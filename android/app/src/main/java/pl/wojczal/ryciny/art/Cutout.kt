package pl.wojczal.ryciny.art

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lifts an engraving off its paper: flood-fills the paper colour in from the edges,
 * softens the rim and trims to the subject. Gives the alpha cut-outs fugleramme packs
 * by silhouette, whatever background the image model printed.
 */
object Cutout {
    private const val MAX_SIDE = 1200 // fugleramme's plate cap
    private const val TOLERANCE = 34

    fun fromPaper(source: Bitmap): Bitmap {
        val src = scaled(source)
        val w = src.width
        val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val paper = borderMedian(px, w, h)

        val bg = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun seed(i: Int) {
            if (!bg[i] && near(px[i], paper)) {
                bg[i] = true
                queue[tail++] = i
            }
        }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (x > 0) seed(i - 1)
            if (x < w - 1) seed(i + 1)
            if (y > 0) seed(i - w)
            if (y < h - 1) seed(i + w)
        }

        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (bg[i]) continue
            val rim = (x > 0 && bg[i - 1]) || (x < w - 1 && bg[i + 1]) || (y > 0 && bg[i - w]) || (y < h - 1 && bg[i + w])
            val c = px[i]
            val alpha = if (rim) (distance(c, paper) * 255f / (TOLERANCE * 2.5f)).roundToInt().coerceIn(60, 255) else 255
            out[i] = Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))
            x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y)
        }
        if (x1 < 0) return src
        val pad = (max(w, h) * 0.01f).roundToInt()
        x0 = max(0, x0 - pad); y0 = max(0, y0 - pad); x1 = min(w - 1, x1 + pad); y1 = min(h - 1, y1 + pad)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(out, 0, w, 0, 0, w, h) }
        return Bitmap.createBitmap(result, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }

    private fun scaled(b: Bitmap): Bitmap {
        val side = max(b.width, b.height)
        if (side <= MAX_SIDE) return b.copy(Bitmap.Config.ARGB_8888, false)
        val f = MAX_SIDE.toFloat() / side
        return Bitmap.createScaledBitmap(b, (b.width * f).roundToInt(), (b.height * f).roundToInt(), true)
    }

    private fun borderMedian(px: IntArray, w: Int, h: Int): Int {
        val edge = (0 until w).flatMap { listOf(px[it], px[(h - 1) * w + it]) } + (0 until h).flatMap { listOf(px[it * w], px[it * w + w - 1]) }
        fun med(f: (Int) -> Int) = edge.map(f).sorted()[edge.size / 2]
        return Color.rgb(med(Color::red), med(Color::green), med(Color::blue))
    }

    private fun distance(a: Int, b: Int) =
        max(abs(Color.red(a) - Color.red(b)), max(abs(Color.green(a) - Color.green(b)), abs(Color.blue(a) - Color.blue(b))))

    private fun near(a: Int, b: Int) = distance(a, b) <= TOLERANCE
}
