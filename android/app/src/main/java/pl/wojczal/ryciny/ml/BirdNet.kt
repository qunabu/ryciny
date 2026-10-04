package pl.wojczal.ryciny.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.min

/**
 * BirdNET 6K v2.4: 3 s of 48 kHz mono in, 6522 logits out. The metadata model turns
 * (lat, lon, week) into which of those species occur there at all, the same range
 * filter BirdNET-Go applies. Models and labels: CC BY-NC-SA 4.0, K. Lisa Yang Center.
 */
class BirdNet(context: Context) {
    data class Label(val sci: String, val name: String, val bird: Boolean)

    private val model = Interpreter(context.mapAsset("models/birdnet.tflite"), Interpreter.Options().setNumThreads(2))
    private val meta = Interpreter(context.mapAsset("models/birdnet_meta.tflite"))
    private var range: Triple<Double, Double, Int>? = null
    private var mask: BooleanArray? = null

    val labels: List<Label> = context.assets.open("models/birdnet_labels_pl.txt").bufferedReader().readLines()
        .filter { it.isNotBlank() }
        .map { line ->
            val sci = line.substringBefore('_')
            val name = line.substringAfter('_', sci)
            Label(sci, name, isBird(sci))
        }

    /** Sigmoid confidences for one 144 000-sample window. */
    fun predict(window: FloatArray): FloatArray {
        val out = Array(1) { FloatArray(labels.size) }
        model.run(arrayOf(window), out)
        return FloatArray(labels.size) { 1f / (1f + exp(-out[0][it])) }
    }

    /** Species plausible at this place this week; cached until either changes. */
    fun rangeMask(lat: Double, lon: Double, date: LocalDate = LocalDate.now()): BooleanArray {
        val week = (date.monthValue - 1) * 4 + min(4, (date.dayOfMonth - 1) / 7 + 1)
        val key = Triple(Math.round(lat * 10) / 10.0, Math.round(lon * 10) / 10.0, week)
        mask?.takeIf { range == key }?.let { return it }
        val out = Array(1) { FloatArray(labels.size) }
        meta.run(arrayOf(floatArrayOf(lat.toFloat(), lon.toFloat(), week.toFloat())), out)
        return BooleanArray(labels.size) { out[0][it] >= RANGE_THRESHOLD }.also {
            mask = it
            range = key
        }
    }

    companion object {
        const val SAMPLES = 144_000
        const val RANGE_THRESHOLD = 0.03f

        // The v2.4 labels that are noise classes, frogs, insects and mammals; read off
        // fugleramme's taxa.py so both agree on what a bird is.
        private val NON_BIRD_GENERA = """
            dog engine environmental fireworks gun human noise power siren
            acris anaxyrus dryophytes eleutherodactylus gastrophryne hyliola incilius
            lithobates pseudacris scaphiopus spea
            allonemobius amblycorypha anaxipha apis atlanticus conocephalus cyrtoxipha
            eunemobius gryllus hapithus microcentrum miogryllus neoconocephalus neonemobius
            oecanthus orchelimum orocharis phyllopalpus pterophylla scudderia
            alouatta canis odocoileus sciurus tamias tamiasciurus
        """.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()

        fun isBird(sci: String) = sci.substringBefore(' ').lowercase() !in NON_BIRD_GENERA
    }
}
