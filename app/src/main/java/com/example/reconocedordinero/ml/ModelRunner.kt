package com.example.reconocedordinero.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Caja en coordenadas del bitmap original. */
data class Detection(
    val cls: Int,
    val score: Float,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

/** Valores en céntimos para evitar errores de coma flotante. Etiquetas fuera del mapa (ej. "rev") se ignoran. */
object Dinero {
    val CENTIMOS = mapOf(
        "10-soles" to 1000, "20-soles" to 2000, "50-soles" to 5000, "100-soles" to 10000,
        "m1" to 100, "m2" to 200, "m5" to 500,
        "m10" to 10, "m20" to 20, "m50" to 50,
    )

    /** Devuelve (conteo por etiqueta, total en céntimos). */
    fun resumen(dets: List<Detection>, labels: List<String>): Pair<Map<String, Int>, Int> {
        val conteo = dets.map { labels[it.cls] }
            .filter { it in CENTIMOS }
            .groupingBy { it }
            .eachCount()
        val total = conteo.entries.sumOf { CENTIMOS.getValue(it.key) * it.value }
        return conteo to total
    }
}

/**
 * Requiere en assets/: best.tflite y labels.txt (una clase por línea, en el orden de model.names).
 * Dependencia: implementation("org.tensorflow:tensorflow-lite:2.16.1")
 * En build.gradle.kts: android { androidResources { noCompress += "tflite" } }
 */
class ModelRunner(context: Context, modelName: String = "best.tflite") {

    val labels: List<String> = context.assets.open("labels.txt")
        .bufferedReader().readLines().filter { it.isNotBlank() }

    private val size = 640
    private val interpreter: Interpreter
    private val outShape: IntArray        // [1, 4+nc, N] o [1, N, 4+nc]
    private val channelsFirst: Boolean
    private val inputChannelsFirst: Boolean   // true si la entrada es [1, 3, 640, 640]

    init {
        val fd = context.assets.openFd(modelName)
        val model = FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
        outShape = interpreter.getOutputTensor(0).shape()
        channelsFirst = outShape[1] < outShape[2]
        inputChannelsFirst = interpreter.getInputTensor(0).shape()[1] == 3
        Log.d(TAG, "in=${interpreter.getInputTensor(0).shape().toList()} out=${outShape.toList()}")
    }

    fun detect(bitmap: Bitmap, confThr: Float = 0.4f, iouThr: Float = 0.5f): List<Detection> {
        // 1. Letterbox a 640x640 con relleno gris 114 (igual que Ultralytics en inferencia)
        val scale = minOf(size / bitmap.width.toFloat(), size / bitmap.height.toFloat())
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        val padX = (size - w) / 2f
        val padY = (size - h) / 2f
        val boxed = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(boxed).apply {
            drawColor(Color.rgb(114, 114, 114))
            drawBitmap(Bitmap.createScaledBitmap(bitmap, w, h, true), padX, padY, null)
        }

        // 2. RGB float32 normalizado 0..1, en el orden que pida el tensor de entrada
        val input = ByteBuffer.allocateDirect(4 * size * size * 3).order(ByteOrder.nativeOrder())
        val px = IntArray(size * size)
        boxed.getPixels(px, 0, size, 0, 0, size, size)
        if (inputChannelsFirst) {            // [1, 3, 640, 640]: un plano por canal (R, G, B)
            for (shift in intArrayOf(16, 8, 0)) {
                for (p in px) input.putFloat(((p shr shift) and 0xFF) / 255f)
            }
        } else {                             // [1, 640, 640, 3]: píxeles intercalados
            for (p in px) {
                input.putFloat(((p shr 16) and 0xFF) / 255f)
                input.putFloat(((p shr 8) and 0xFF) / 255f)
                input.putFloat((p and 0xFF) / 255f)
            }
        }
        input.rewind()

        // 3. Inferencia
        val n = if (channelsFirst) outShape[2] else outShape[1]
        val ch = if (channelsFirst) outShape[1] else outShape[2]
        val raw = Array(1) {
            if (channelsFirst) Array(ch) { FloatArray(n) } else Array(n) { FloatArray(ch) }
        }
        interpreter.run(input, raw)
        fun v(c: Int, i: Int) = if (channelsFirst) raw[0][c][i] else raw[0][i][c]

        // 4. Decodificar. Si las coordenadas vienen normalizadas (0..1) se escalan a 640.
        var maxW = 0f
        for (i in 0 until n) maxW = maxOf(maxW, v(2, i))
        val k = if (maxW <= 1.5f) size.toFloat() else 1f
        val nc = ch - 4

        val cand = ArrayList<Detection>()
        for (i in 0 until n) {
            var best = 0
            var score = 0f
            for (c in 0 until nc) {
                val s = v(4 + c, i)
                if (s > score) { score = s; best = c }
            }
            if (score < confThr) continue
            val cx = v(0, i) * k
            val cy = v(1, i) * k
            val bw = v(2, i) * k
            val bh = v(3, i) * k
            // Deshacer letterbox y volver a coordenadas del bitmap original
            cand += Detection(
                best, score,
                ((cx - bw / 2) - padX) / scale,
                ((cy - bh / 2) - padY) / scale,
                ((cx + bw / 2) - padX) / scale,
                ((cy + bh / 2) - padY) / scale,
            )
        }
        return nms(cand, iouThr).take(50)
    }

    private fun nms(dets: List<Detection>, iouThr: Float): List<Detection> {
        val keep = mutableListOf<Detection>()
        for (d in dets.sortedByDescending { it.score }) {
            if (keep.none { it.cls == d.cls && iou(it, d) > iouThr }) keep += d
        }
        return keep
    }

    private fun iou(a: Detection, b: Detection): Float {
        val ix = maxOf(0f, minOf(a.x2, b.x2) - maxOf(a.x1, b.x1))
        val iy = maxOf(0f, minOf(a.y2, b.y2) - maxOf(a.y1, b.y1))
        val inter = ix * iy
        val union = (a.x2 - a.x1) * (a.y2 - a.y1) + (b.x2 - b.x1) * (b.y2 - b.y1) - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun close() = interpreter.close()

    private companion object {
        const val TAG = "ModelRunner"
    }
}
