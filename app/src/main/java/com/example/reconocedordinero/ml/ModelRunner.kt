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
import java.util.Locale
import kotlin.math.abs

/** Caja en coordenadas del bitmap original. */
data class Detection(
    val cls: Int,
    val score: Float,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    /**
     * Confianza de la segunda clase más probable en la misma caja. Sirve para detectar monedas
     * dudosas: si la ganadora y la segunda están muy cerca, es mejor no contar la pieza.
     */
    val secondScore: Float = 0f,
    /**
     * Probabilidades de todas las clases. Solo se usa internamente para el recorte ampliado y el
     * voto temporal; la UI y el conteo usan [cls], [score] y [secondScore].
     */
    val probs: FloatArray? = null,
) {
    /** Diferencia entre la clase ganadora y la segunda: cuanto menor, más duda hay. */
    val margin: Float get() = score - secondScore
}

/** Las dos clases más probables de una predicción. */
internal data class Top2(val cls: Int, val score: Float, val second: Float) {
    val margin: Float get() = score - second
}

/** Clase ganadora y segunda de un vector de probabilidades (incluye billetes y `rev`). */
internal fun top2(probs: FloatArray): Top2 {
    var best = 0
    var score = 0f
    var second = 0f
    for (c in probs.indices) {
        val s = probs[c]
        if (s > score) {
            second = score
            score = s
            best = c
        } else if (s > second) {
            second = s
        }
    }
    return Top2(best, score, second)
}

/**
 * Mezcla las probabilidades de la detección completa con las del recorte ampliado. El recorte
 * pesa más porque ve la moneda con muchos más píxeles.
 */
internal fun mezclarProbabilidades(
    completa: FloatArray,
    recorte: FloatArray,
    pesoRecorte: Float = 0.65f,
): FloatArray {
    require(completa.size == recorte.size) { "Los vectores de probabilidad deben tener el mismo tamaño" }
    val pesoCompleta = 1f - pesoRecorte
    return FloatArray(completa.size) { pesoCompleta * completa[it] + pesoRecorte * recorte[it] }
}

/** Valores en céntimos para evitar errores de coma flotante. Etiquetas fuera del mapa (ej. "rev") se ignoran. */
object Dinero {
    val CENTIMOS = mapOf(
        "10-soles" to 1000, "20-soles" to 2000, "50-soles" to 5000, "100-soles" to 10000,
        "m1" to 100, "m2" to 200, "m5" to 500,
        "m10" to 10, "m20" to 20, "m50" to 50,
    )

    /**
     * Margen mínimo entre la clase ganadora y la segunda para contar una moneda. Con menos de
     * este margen la detección se considera dudosa y no suma al total: es preferible avisar que
     * contar una denominación equivocada.
     */
    const val UNCERTAIN_MARGIN = 0.15f

    /** `true` si la etiqueta corresponde a un billete. */
    fun isBill(label: String): Boolean = label.endsWith("-soles")

    /** `true` si la etiqueta corresponde a una moneda de las que cuentan para el total. */
    fun isCoin(label: String): Boolean = label in CENTIMOS && !isBill(label)

    /**
     * `true` si la detección es una moneda cuya clase ganadora no se distingue con claridad de la
     * segunda (por ejemplo `m20` al 0,52 y `m50` al 0,44). Los billetes quedan exentos: ya
     * funcionan bien y el aviso es solo para monedas.
     */
    fun isUncertain(det: Detection, labels: List<String>): Boolean {
        val label = labels.getOrNull(det.cls) ?: return false
        return isCoin(label) && det.margin < UNCERTAIN_MARGIN
    }

    /** Cantidad de monedas dudosas de un frame (para avisar al usuario). */
    fun inciertas(dets: List<Detection>, labels: List<String>): Int =
        dets.count { isUncertain(it, labels) }

    /**
     * Devuelve (conteo por etiqueta, total en céntimos). Las monedas dudosas no se cuentan.
     */
    fun resumen(dets: List<Detection>, labels: List<String>): Pair<Map<String, Int>, Int> {
        val conteo = dets.filterNot { isUncertain(it, labels) }
            .mapNotNull { labels.getOrNull(it.cls) }
            .filter { it in CENTIMOS }
            .groupingBy { it }
            .eachCount()
        val total = conteo.entries.sumOf { CENTIMOS.getValue(it.key) * it.value }
        return conteo to total
    }
}

/**
 * Requiere en assets/: best.tflite y labels.txt (una clase por línea, en el orden de model.names).
 * Dependencia: implementation("com.google.ai.edge.litert:litert:<última versión>").
 * Ojo: el artefacto se llama com.google.ai.edge.litert:litert, pero la API Java sigue
 * viviendo en el paquete org.tensorflow.lite (ver README, sección de decisiones).
 * En build.gradle.kts: android { androidResources { noCompress += "tflite" } }
 *
 * Para mejorar la distinción entre monedas parecidas, después de la detección normal:
 *
 * 1. **Recorte ampliado:** cada moneda se recorta con margen y se vuelve a pasar por el modelo.
 *    Así ocupa casi todo el tensor de entrada y el modelo ve el número, el relieve y el canto.
 * 2. **Voto temporal:** cada moneda se sigue entre fotogramas (por solapamiento de cajas) y se
 *    acumulan sus probabilidades. Una predicción aislada y errónea pesa menos que la evidencia
 *    de varios fotogramas.
 *
 * Todo el estado es de un solo escaneo y de un solo hilo (ver [resetTracks]).
 */
class ModelRunner(context: Context, modelName: String = "best.tflite") {

    val labels: List<String> = context.assets.open("labels.txt")
        .bufferedReader().readLines().filter { it.isNotBlank() }

    private val interpreter: Interpreter
    private val outShape: IntArray        // [1, 4+nc, N] o [1, N, 4+nc]
    private val channelsFirst: Boolean
    private val inputChannelsFirst: Boolean   // true si la entrada es [1, 3, H, W]
    private val inputH: Int
    private val inputW: Int
    private val outN: Int
    private val outCh: Int

    // Buffers reutilizados: evita reservar ~5 MB por inferencia (importante al recortar monedas).
    private val entrada: ByteBuffer by lazy {
        ByteBuffer.allocateDirect(4 * inputW * inputH * 3).order(ByteOrder.nativeOrder())
    }
    private val pixeles: IntArray by lazy { IntArray(inputW * inputH) }
    private val salidaCruda: Array<Array<FloatArray>> by lazy {
        Array(1) {
            if (channelsFirst) Array(outCh) { FloatArray(outN) } else Array(outN) { FloatArray(outCh) }
        }
    }

    /** Seguimiento temporal de monedas entre fotogramas. */
    private val pistas = ArrayList<Pista>()
    private var fotograma = 0L

    init {
        val fd = context.assets.openFd(modelName)
        val model = FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        // TODO v2: delegado GPU (por ahora CPU con 4 hilos).
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
        outShape = interpreter.getOutputTensor(0).shape()
        channelsFirst = outShape[1] < outShape[2]
        outN = if (channelsFirst) outShape[2] else outShape[1]
        outCh = if (channelsFirst) outShape[1] else outShape[2]
        val inputShape = interpreter.getInputTensor(0).shape()
        inputChannelsFirst = inputShape[1] == 3
        inputH = if (inputChannelsFirst) inputShape[2] else inputShape[1]
        inputW = if (inputChannelsFirst) inputShape[3] else inputShape[2]
        Log.d(
            TAG,
            "in=${inputShape.toList()} out=${outShape.toList()} entrada=${inputW}x$inputH",
        )
    }

    /** Reinicia el seguimiento temporal al empezar un escaneo. */
    fun resetTracks() {
        pistas.clear()
        fotograma = 0L
    }

    /**
     * Detecta piezas en [bitmap].
     *
     * [confThr] es el umbral base; cada clase puede exigir más confianza (ver [thresholdFor]).
     * [iouThr] es el IoU del NMS, que es **entre clases**: una sola caja por objeto.
     */
    fun detect(
        bitmap: Bitmap,
        confThr: Float = 0.4f,
        iouThr: Float = 0.5f,
    ): List<Detection> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        fotograma++

        val candidatas = nonMaxSuppression(inferir(bitmap, confThr), iouThr)
        val monedas = candidatas.filter { esMoneda(it.cls) }
        val otras = candidatas.filterNot { esMoneda(it.cls) }
        val votadas = votarMonedas(bitmap, monedas)

        val salida = (otras + votadas).sortedByDescending { it.score }.take(50)
        if (Log.isLoggable(TAG, Log.DEBUG)) logDetecciones(salida)
        return salida
    }

    /**
     * Recorta cada moneda, la re-evalúa y acumula sus probabilidades en la pista temporal.
     */
    private fun votarMonedas(bitmap: Bitmap, monedas: List<Detection>): List<Detection> {
        expirarPistas()
        if (monedas.isEmpty()) return emptyList()

        val usadas = HashSet<Pista>()
        val salida = ArrayList<Detection>(monedas.size)
        var refinadas = 0

        for (d in monedas.sortedByDescending { it.score }) {
            val probs = d.probs ?: continue
            var pista = mejorPista(d, usadas)

            if (pista == null) {
                var suma = probs.copyOf()
                var peso = 1f
                var intento = false
                if (refinadas < MAX_REFINADAS) {
                    intento = true
                    val recorte = refinar(bitmap, d)
                    if (recorte != null) {
                        for (c in suma.indices) suma[c] += recorte[c] * PESO_RECORTE
                        peso += PESO_RECORTE
                        refinadas++
                    }
                }
                pista = Pista(
                    box = d,
                    suma = suma,
                    peso = peso,
                    ultimoFotograma = fotograma,
                    ultimoRefinado = if (intento) fotograma else 0L,
                    refinada = intento,
                )
                pistas += pista
                usadas += pista
            } else {
                usadas += pista
                pista.box = d
                pista.ultimoFotograma = fotograma
                sumar(pista, probs, 1f)

                val dudosa = desdePista(pista).margin < MARGEN_REVISAR
                val tocaRevisar = fotograma - pista.ultimoRefinado >= REVISAR_CADA
                val necesita = !pista.refinada || (dudosa && tocaRevisar)
                if (refinadas < MAX_REFINADAS && necesita) {
                    pista.ultimoRefinado = fotograma
                    pista.refinada = true
                    val recorte = refinar(bitmap, d)
                    if (recorte != null) {
                        sumar(pista, recorte, PESO_RECORTE)
                        refinadas++
                    }
                }
            }

            salida += desdePista(pista)
        }
        return salida
    }

    /** Mejor pista libre para una caja, comparando por solapamiento (IoU). */
    private fun mejorPista(d: Detection, usadas: Set<Pista>): Pista? {
        var mejor: Pista? = null
        var mejorIou = TRACK_IOU
        for (p in pistas) {
            if (p in usadas) continue
            val solape = iou(p.box, d)
            if (solape >= mejorIou) {
                mejorIou = solape
                mejor = p
            }
        }
        return mejor
    }

    private fun expirarPistas() {
        pistas.removeAll { fotograma - it.ultimoFotograma > MAX_EDAD }
        if (pistas.size > MAX_PISTAS) {
            pistas.sortByDescending { it.ultimoFotograma }
            while (pistas.size > MAX_PISTAS) pistas.removeAt(pistas.lastIndex)
        }
    }

    /** Acumula probabilidades con un factor de olvido para no arrastrar evidencia antigua. */
    private fun sumar(pista: Pista, probs: FloatArray, peso: Float) {
        if (pista.peso > PESO_MAX) {
            for (c in pista.suma.indices) pista.suma[c] *= 0.5f
            pista.peso *= 0.5f
        }
        for (c in probs.indices) pista.suma[c] += probs[c] * peso
        pista.peso += peso
    }

    /** Convierte una pista en una detección, mirando solo las clases de moneda. */
    private fun desdePista(pista: Pista): Detection {
        val probs = FloatArray(pista.suma.size) { pista.suma[it] / pista.peso }
        val mejor = top2Moneda(probs)
        return pista.box.copy(
            cls = mejor.cls,
            score = mejor.score,
            secondScore = mejor.second,
            probs = probs,
        )
    }

    /**
     * Recorta la moneda con un 20 % de margen, la vuelve a pasar por el modelo y devuelve la
     * mezcla de probabilidades. Devuelve `null` si el recorte no aporta una lectura clara.
     */
    private fun refinar(bitmap: Bitmap, d: Detection): FloatArray? {
        val lado = maxOf(d.x2 - d.x1, d.y2 - d.y1)
        if (lado < MIN_CROP_PX) return null

        val mitad = lado / 2f + lado * MARGEN_RECORTE
        val centroX = (d.x1 + d.x2) / 2f
        val centroY = (d.y1 + d.y2) / 2f
        val left = (centroX - mitad).toInt().coerceIn(0, bitmap.width - 2)
        val top = (centroY - mitad).toInt().coerceIn(0, bitmap.height - 2)
        val right = (centroX + mitad).toInt().coerceIn(left + 2, bitmap.width)
        val bottom = (centroY + mitad).toInt().coerceIn(top + 2, bitmap.height)
        val w = right - left
        val h = bottom - top
        if (w < MIN_CROP_PX || h < MIN_CROP_PX) return null
        if (w == bitmap.width && h == bitmap.height) return null

        val recorte = try {
            Bitmap.createBitmap(bitmap, left, top, w, h)
        } catch (e: IllegalArgumentException) {
            return null
        }

        return try {
            val dets = inferir(recorte, CROP_CONF, umbralPorClase = false, soloMonedas = true)
            if (dets.isEmpty()) return null

            // La moneda recortada debe quedar cerca del centro; si no, el recorte cogió otra pieza.
            val centroRecorteX = w / 2f
            val centroRecorteY = h / 2f
            val tolerancia = minOf(w, h) * 0.30f
            val centradas = dets.filter {
                abs((it.x1 + it.x2) / 2f - centroRecorteX) <= tolerancia &&
                    abs((it.y1 + it.y2) / 2f - centroRecorteY) <= tolerancia
            }
            val mejor = (centradas.ifEmpty { dets }).maxByOrNull { it.score } ?: return null
            if (mejor.score < CROP_MIN_SCORE) return null

            val probsRecorte = mejor.probs ?: return null
            val probsCompleta = d.probs ?: return null
            val mezcla = mezclarProbabilidades(probsCompleta, probsRecorte)
            if (top2Moneda(mezcla).cls < 0) return null

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                val antes = labels.getOrNull(top2Moneda(probsCompleta).cls)
                val despues = labels.getOrNull(top2Moneda(mezcla).cls)
                Log.d(
                    TAG,
                    "recorte ${w}x$h: $antes -> $despues (recorte=" +
                        String.format(Locale.US, "%.2f", mejor.score) + ")",
                )
            }
            mezcla
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo re-evaluar la moneda", e)
            null
        } finally {
            if (recorte !== bitmap) recorte.recycle()
        }
    }

    /**
     * Inferencia completa: letterbox al tamaño real del tensor, ejecución y decodificación.
     * No aplica NMS (lo hace [detect] después).
     */
    private fun inferir(
        bitmap: Bitmap,
        confThr: Float,
        umbralPorClase: Boolean = true,
        soloMonedas: Boolean = false,
    ): List<Detection> {
        // 1. Letterbox al tamaño real de entrada con relleno gris 114 (igual que Ultralytics)
        val escala = minOf(
            inputW / bitmap.width.toFloat(),
            inputH / bitmap.height.toFloat(),
        )
        val w = (bitmap.width * escala).toInt().coerceAtLeast(1)
        val h = (bitmap.height * escala).toInt().coerceAtLeast(1)
        val padX = (inputW - w) / 2f
        val padY = (inputH - h) / 2f
        val lienzo = Bitmap.createBitmap(inputW, inputH, Bitmap.Config.ARGB_8888)
        val escalada = Bitmap.createScaledBitmap(bitmap, w, h, true)
        Canvas(lienzo).apply {
            drawColor(Color.rgb(114, 114, 114))
            drawBitmap(escalada, padX, padY, null)
        }
        if (escalada !== bitmap) escalada.recycle()
        lienzo.getPixels(pixeles, 0, inputW, 0, 0, inputW, inputH)
        lienzo.recycle()

        // 2. RGB float32 normalizado 0..1, en el orden que pida el tensor de entrada
        entrada.clear()
        if (inputChannelsFirst) {            // [1, 3, H, W]: un plano por canal (R, G, B)
            for (shift in intArrayOf(16, 8, 0)) {
                for (p in pixeles) entrada.putFloat(((p shr shift) and 0xFF) / 255f)
            }
        } else {                             // [1, H, W, 3]: píxeles intercalados
            for (p in pixeles) {
                entrada.putFloat(((p shr 16) and 0xFF) / 255f)
                entrada.putFloat(((p shr 8) and 0xFF) / 255f)
                entrada.putFloat((p and 0xFF) / 255f)
            }
        }
        entrada.rewind()

        // 3. Inferencia
        interpreter.run(entrada, salidaCruda)
        fun v(c: Int, i: Int) =
            if (channelsFirst) salidaCruda[0][c][i] else salidaCruda[0][i][c]

        // 4. Decodificar. Si las coordenadas vienen normalizadas (0..1) se escalan al tensor.
        var maxW = 0f
        for (i in 0 until outN) maxW = maxOf(maxW, v(2, i))
        val normalizado = maxW <= 1.5f
        val kx = if (normalizado) inputW.toFloat() else 1f
        val ky = if (normalizado) inputH.toFloat() else 1f
        val nc = outCh - 4

        val salida = ArrayList<Detection>()
        val probs = FloatArray(nc)
        for (i in 0 until outN) {
            for (c in 0 until nc) probs[c] = v(4 + c, i)
            val mejor = if (soloMonedas) top2Moneda(probs) else top2(probs)
            if (mejor.cls < 0) continue
            val umbral = if (umbralPorClase) thresholdFor(mejor.cls, confThr) else confThr
            if (mejor.score < umbral) continue

            val cx = v(0, i) * kx
            val cy = v(1, i) * ky
            val bw = v(2, i) * kx
            val bh = v(3, i) * ky
            // Deshacer letterbox y volver a coordenadas del bitmap original
            salida += Detection(
                cls = mejor.cls,
                score = mejor.score,
                x1 = ((cx - bw / 2) - padX) / escala,
                y1 = ((cy - bh / 2) - padY) / escala,
                x2 = ((cx + bw / 2) - padX) / escala,
                y2 = ((cy + bh / 2) - padY) / escala,
                secondScore = mejor.second,
                probs = probs.copyOf(),
            )
        }
        return salida
    }

    /** Clase de moneda ganadora y segunda, ignorando billetes y `rev`. */
    private fun top2Moneda(probs: FloatArray): Top2 {
        var best = -1
        var score = 0f
        var second = 0f
        for (c in probs.indices) {
            if (!esMoneda(c)) continue
            val s = probs[c]
            if (s > score) {
                second = score
                score = s
                best = c
            } else if (s > second) {
                second = s
            }
        }
        return Top2(best, score, second)
    }

    private fun esMoneda(cls: Int): Boolean = Dinero.isCoin(labels.getOrNull(cls) ?: "")

    /**
     * Umbral de confianza por clase. Los billetes mantienen el umbral base (funcionan bien); las
     * monedas pequeñas (10/20/50 céntimos) exigen más confianza porque son las que más se
     * confunden entre sí.
     */
    private fun thresholdFor(cls: Int, base: Float): Float {
        val porClase = when (labels.getOrNull(cls)) {
            "m10", "m20", "m50" -> 0.50f
            "m1", "m2", "m5" -> 0.45f
            else -> base
        }
        return maxOf(base, porClase)
    }

    private fun logDetecciones(dets: List<Detection>) {
        for (d in dets) {
            val label = labels.getOrNull(d.cls) ?: d.cls.toString()
            val dudosa = if (Dinero.isUncertain(d, labels)) " DUDOSA" else ""
            Log.d(
                TAG,
                String.format(
                    Locale.US,
                    "%s=%.2f segunda=%.2f margen=%.2f%s caja=[%.0f,%.0f,%.0f,%.0f]",
                    label, d.score, d.secondScore, d.margin, dudosa, d.x1, d.y1, d.x2, d.y2,
                ),
            )
        }
    }

    fun close() = interpreter.close()

    /** Moneda seguida entre fotogramas, con sus probabilidades acumuladas. */
    private class Pista(
        var box: Detection,
        val suma: FloatArray,
        var peso: Float,
        var ultimoFotograma: Long,
        var ultimoRefinado: Long,
        var refinada: Boolean,
    )

    private companion object {
        const val TAG = "ModelRunner"

        /** Máximo de recortes por fotograma: acota el coste en escenas con muchas monedas. */
        const val MAX_REFINADAS = 3

        /** Cuánto pesa el recorte frente a un fotograma completo en el voto temporal. */
        const val PESO_RECORTE = 2f

        /** Umbral del recorte: solo se usa si la moneda se ve con claridad. */
        const val CROP_CONF = 0.20f
        const val CROP_MIN_SCORE = 0.45f

        /** Recortes menores que esto no aportan resolución. */
        const val MIN_CROP_PX = 32f

        /** Margen alrededor de la caja de la moneda al recortar. */
        const val MARGEN_RECORTE = 0.20f

        /** Si el margen acumulado baja de esto, se vuelve a recortar la moneda. */
        const val MARGEN_REVISAR = 0.20f
        const val REVISAR_CADA = 12L

        /** Seguimiento: solapamiento mínimo para considerar que es la misma moneda. */
        const val TRACK_IOU = 0.40f
        const val MAX_EDAD = 12L
        const val MAX_PISTAS = 60

        /** Factor de olvido: a partir de aquí la evidencia antigua se reduce a la mitad. */
        const val PESO_MAX = 20f
    }
}

/**
 * NMS **entre clases**: deja una sola caja por objeto y se queda con la clase de mayor
 * confianza. Antes solo se comparaba con cajas de la misma clase, así que una misma moneda podía
 * contarse dos veces como dos denominaciones distintas (por ejemplo 20 céntimos y 50 céntimos).
 *
 * El IoU se mantiene en 0,5: dos monedas separadas no se solapan tanto y una moneda encima de un
 * billete tiene un IoU pequeño, así que no se fusionan piezas distintas.
 */
internal fun nonMaxSuppression(dets: List<Detection>, iouThr: Float): List<Detection> {
    val keep = mutableListOf<Detection>()
    for (d in dets.sortedByDescending { it.score }) {
        if (keep.none { iou(it, d) > iouThr }) keep += d
    }
    return keep
}

internal fun iou(a: Detection, b: Detection): Float {
    val ix = maxOf(0f, minOf(a.x2, b.x2) - maxOf(a.x1, b.x1))
    val iy = maxOf(0f, minOf(a.y2, b.y2) - maxOf(a.y1, b.y1))
    val inter = ix * iy
    val union = (a.x2 - a.x1) * (a.y2 - a.y1) + (b.x2 - b.x1) * (b.y2 - b.y1) - inter
    return if (union <= 0f) 0f else inter / union
}
