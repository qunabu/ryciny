package pl.wojczal.ryciny.frame

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import pl.wojczal.ryciny.R
import pl.wojczal.ryciny.art.Art
import pl.wojczal.ryciny.art.ArtRequest
import pl.wojczal.ryciny.art.Kind
import pl.wojczal.ryciny.data.Catalog
import pl.wojczal.ryciny.planes.art
import pl.wojczal.ryciny.planes.planeArt
import pl.wojczal.ryciny.ui.BirdSubject
import pl.wojczal.ryciny.ui.DogSubject
import pl.wojczal.ryciny.ui.PL
import pl.wojczal.ryciny.ui.PlaneSubject
import pl.wojczal.ryciny.ui.SoundSubject
import pl.wojczal.ryciny.ui.Subject
import pl.wojczal.ryciny.ui.hhmm
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.random.Random

/** What one plate says: its engraving and the lines under it, the same words the plate screen shows. */
internal data class PlateContent(val art: ArtRequest?, val title: String, val lines: List<String>)

internal fun Subject.content(catalog: Catalog): PlateContent = when (this) {
    is PlaneSubject -> {
        val live = live
        val pass = pass
        if (live != null) {
            PlateContent(
                planeArt(live.typeCode, live.airlineIcao, live.title, live.airline),
                live.title,
                listOf(listOf(live.airline, live.callsign).filter { it.isNotBlank() }.joinToString(" · "), live.route),
            )
        } else {
            PlateContent(
                pass!!.art(),
                pass.title,
                listOf(listOf(pass.airline, pass.callsign).filter { it.isNotBlank() }.joinToString(" · "), pass.route, "słychać było o ${hhmm(pass.at)}"),
            )
        }
    }
    is BirdSubject -> PlateContent(
        ArtRequest(Kind.BIRD, bird.sci, bird.sci),
        bird.name.replaceFirstChar { it.uppercase() },
        listOf(bird.sci, "ostatnio ${hhmm(bird.lastAt)}" + if (count > 1) " · ${count}×" else ""),
    )
    is DogSubject -> {
        val count = barks.sumOf { it.count }
        val last = barks.maxOf { it.lastAt }
        val dog = dog
        if (dog != null) {
            PlateContent(ArtRequest(Kind.DOG, dog.breed, dog.breedEn), dog.name, listOf(dog.breed, "szczekał ${count}× · ostatnio ${hhmm(last)}"))
        } else {
            val size = barks.first().size
            val mongrel = catalog.mongrel(size)
            PlateContent(
                ArtRequest(Kind.DOG, mongrel.pl, mongrel.en),
                if (size == "?") "Nieznany pies" else "Nieznany pies, raczej $size",
                listOf("szczekał ${count}× · ostatnio ${hhmm(last)}"),
            )
        }
    }
    is SoundSubject -> PlateContent(
        catalog.soundArt(sound, event.variant),
        sound.past,
        listOf(event.title, event.subtitle, "${hhmm(event.at)}–${hhmm(event.lastAt)}"),
    )
}.let { it.copy(lines = it.lines.filter { line -> line.isNotBlank() }) }

/**
 * The plate as one picture for a screen elsewhere, here The Frame in Art Mode: the engraving on laid paper
 * with its caption, 16:9, full colour. A JPEG that stays under the TV's 1.9 MB upload limit.
 */
internal object PlateImage {
    private const val PAPER = 0xFFF3EAD7.toInt()
    private const val INK = 0xFF2B2118.toInt()
    private const val INK_SOFT = 0xFF6B5B4A.toInt()
    const val MAX_BYTES = 1_900_000

    suspend fun render(context: Context, art: Art, content: PlateContent?, width: Int = 3840, height: Int = 2160): Bitmap {
        val engraving = content?.art?.let { req -> art.load(req)?.let { BitmapFactory.decodeFile(it.path) } }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        paper(c, width, height)

        val regular = ResourcesCompat.getFont(context, R.font.garamond) ?: Typeface.SERIF
        val italic = ResourcesCompat.getFont(context, R.font.garamond_italic) ?: Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        val unit = height / 100f
        text(c, SimpleDateFormat("d MMMM yyyy, HH:mm", PL).format(Date()), italic, 2.6f * unit, INK_SOFT, width / 2f, 9f * unit, width)

        if (content == null) {
            text(c, "Cisza: nic nie przeleciało, nie zaśpiewało ani nie zaszczekało", italic, 3.6f * unit, INK_SOFT, width / 2f, 50f * unit, width)
            return out
        }
        if (engraving != null) {
            val box = RectF(width * 0.18f, 13f * unit, width * 0.82f, 72f * unit)
            val scale = minOf(box.width() / engraving.width, box.height() / engraving.height)
            val w = engraving.width * scale
            val h = engraving.height * scale
            val dst = RectF(box.centerX() - w / 2, box.bottom - h, box.centerX() + w / 2, box.bottom)
            c.drawBitmap(engraving, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        text(c, content.title, regular, 6.2f * unit, INK, width / 2f, 81f * unit, width)
        content.lines.take(3).forEachIndexed { i, line ->
            text(c, line, italic, 3.2f * unit, if (i == 0) INK else INK_SOFT, width / 2f, (86.5f + i * 4.2f) * unit, width)
        }
        return out
    }

    /** JPEG at the highest quality that fits the TV's limit. */
    fun jpeg(bitmap: Bitmap): ByteArray {
        var quality = 92
        while (true) {
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            if (bytes.size <= MAX_BYTES || quality <= 50) return bytes
            quality -= 7
        }
    }

    private fun paper(c: Canvas, width: Int, height: Int) {
        c.drawColor(PAPER)
        val tile = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val rnd = Random(7)
        for (y in 0 until 256) for (x in 0 until 256) {
            val n = rnd.nextInt(-9, 10) + if (y % 8 == 0) -3 else 0
            tile.setPixel(x, y, Color.rgb(243 + n / 2, 234 + n / 2, 215 + n))
        }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) })
        val inset = height * 0.035f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = INK; alpha = 140; strokeWidth = height * 0.0015f }
        c.drawRect(inset, inset, width - inset, height - inset, line)
        val inner = inset + height * 0.012f
        c.drawRect(inner, inner, width - inner, height - inner, line.apply { alpha = 90; strokeWidth = height * 0.0008f })
    }

    /** One centred line, shrunk until it fits inside the frame. */
    private fun text(c: Canvas, s: String, face: Typeface, size: Float, color: Int, x: Float, baseline: Float, width: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = size; this.color = color; textAlign = Paint.Align.CENTER }
        val max = width * 0.86f
        while (paint.measureText(s) > max && paint.textSize > size * 0.5f) paint.textSize *= 0.95f
        c.drawText(s, x, baseline, paint)
    }
}
