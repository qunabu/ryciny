package pl.wojczal.ryciny.ml

import android.content.Context
import org.tensorflow.lite.Interpreter

/**
 * YAMNet (AudioSet, 521 classes): 0.975 s of 16 kHz mono in, class scores and a
 * 1024-d embedding out. The embedding is what tells one neighbour's dog from another.
 */
class Yamnet(context: Context) {
    private val model = Interpreter(context.mapAsset("models/yamnet.tflite")).apply {
        resizeInput(0, intArrayOf(SAMPLES))
        allocateTensors()
    }
    private val scoresIndex = (0 until model.outputTensorCount).first { model.getOutputTensor(it).shape().last() == 521 }
    private val embeddingIndex = (0 until model.outputTensorCount).first { model.getOutputTensor(it).shape().last() == 1024 }

    val classes: List<String> = context.assets.open("models/yamnet_class_map.csv").bufferedReader().readLines()
        .drop(1)
        .map { it.split(",", limit = 3)[2].trim('"') }

    class Frame(val scores: FloatArray, val embedding: FloatArray)

    fun run(chunk: FloatArray): Frame {
        val outputs = HashMap<Int, Any>()
        (0 until model.outputTensorCount).forEach { i ->
            val shape = model.getOutputTensor(i).shape()
            outputs[i] = Array(shape[0]) { FloatArray(shape[1]) }
        }
        model.runForMultipleInputsOutputs(arrayOf<Any>(chunk), outputs)
        @Suppress("UNCHECKED_CAST")
        return Frame((outputs[scoresIndex] as Array<FloatArray>)[0], (outputs[embeddingIndex] as Array<FloatArray>)[0])
    }

    companion object {
        const val SAMPLES = 15_600
        val DOG = 69..75 // Dog, Bark, Yip, Howl, Bow-wow, Growling, Whimper (dog)
        const val LAWN_MOWER = 340
        val AIRCRAFT = 329..334 // Aircraft, Aircraft engine, Jet engine, Propeller, Helicopter, Fixed-wing aircraft
    }
}
