package com.example.reconocedordinero.ui.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reconocedordinero.AppContainer
import com.example.reconocedordinero.R
import com.example.reconocedordinero.domain.ScanResult
import com.example.reconocedordinero.domain.StabilityFilter
import com.example.reconocedordinero.ml.Detection
import com.example.reconocedordinero.ml.Dinero
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado visible de la pantalla de reconocimiento. */
data class ScanUiState(
    val detections: List<Detection> = emptyList(),
    val bitmapWidth: Int = 0,
    val bitmapHeight: Int = 0,
    val hasPieces: Boolean = false,
)

/**
 * Pipeline de reconocimiento: recibe frames de CameraX (siempre en el hilo único de análisis),
 * llama a [com.example.reconocedordinero.ml.ModelRunner.detect], alimenta el [StabilityFilter]
 * y publica el resultado cuando se estabiliza.
 */
class ScanViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(ScanUiState())
    val ui: StateFlow<ScanUiState> = _ui.asStateFlow()

    private val filter = StabilityFilter()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var onResult: (() -> Unit)? = null
    private var onExit: (() -> Unit)? = null

    private var lastProcessedAt = 0L
    private var lastBeepAt = 0L

    @Volatile
    private var finished = false

    val isFinished: Boolean get() = finished

    fun bind(onResult: () -> Unit, onExit: () -> Unit) {
        this.onResult = onResult
        this.onExit = onExit
    }

    fun reset() {
        filter.reset()
        lastProcessedAt = 0L
        lastBeepAt = 0L
        finished = false
        _ui.value = ScanUiState()
    }

    /** Procesa un frame. Debe ejecutarse en el hilo de análisis; cierra siempre el [ImageProxy]. */
    fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (finished) return
            // ~5 fps: se descartan los frames que llegan antes de 200 ms.
            if (now - lastProcessedAt < FRAME_INTERVAL_MS) return
            lastProcessedAt = now

            val model = container.modelRunner ?: return
            val bitmap = image.toRotatedBitmap() ?: return
            val width = bitmap.width
            val height = bitmap.height

            val detections = try {
                model.detect(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Fallo en la inferencia", e)
                emptyList()
            }
            bitmap.recycle()

            val (counts, _) = Dinero.resumen(detections, model.labels)
            filter.push(counts)

            _ui.value = ScanUiState(
                detections = detections.filter { model.labels.getOrNull(it.cls) in Dinero.CENTIMOS },
                bitmapWidth = width,
                bitmapHeight = height,
                hasPieces = counts.isNotEmpty(),
            )

            if (counts.isNotEmpty() && now - lastBeepAt >= BEEP_INTERVAL_MS) {
                lastBeepAt = now
                container.beepPlayer.beep()
            }

            filter.getStable()?.let { estable -> finishWith(estable) }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo procesar el frame", e)
        } finally {
            image.close()
        }
    }

    private fun finishWith(counts: Map<String, Int>) {
        if (finished) return
        finished = true
        val total = counts.entries.sumOf { (Dinero.CENTIMOS[it.key] ?: 0) * it.value }
        container.publishResult(ScanResult(counts = counts, totalCents = total))
        container.hapticManager.success()
        mainHandler.post { onResult?.invoke() }
    }

    /** Se agotaron los 20 s sin resultado estable. */
    fun onTimeout() {
        if (finished) return
        finished = true
        container.hapticManager.error()
        container.speechManager.speak(container.context.getString(R.string.scan_timeout))
        mainHandler.post { onExit?.invoke() }
    }

    companion object {
        private const val TAG = "ScanViewModel"
        private const val FRAME_INTERVAL_MS = 200L
        private const val BEEP_INTERVAL_MS = 1500L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ScanViewModel(container) }
        }
    }
}

/**
 * Convierte el frame RGBA_8888 de CameraX (con posible padding de fila) en un [Bitmap] ya
 * rotado con la orientación correcta. El modelo recibe el bitmap rotado y devuelve cajas en
 * coordenadas de ese mismo bitmap.
 */
private fun ImageProxy.toRotatedBitmap(): Bitmap? {
    if (planes.isEmpty()) return null
    val plane = planes[0]
    val pixelStride = plane.pixelStride
    if (pixelStride <= 0) return null

    val buffer = plane.buffer
    buffer.rewind()
    val padding = plane.rowStride - pixelStride * width
    val paddedWidth = width + padding / pixelStride

    val raw = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
    raw.copyPixelsFromBuffer(buffer)
    val cropped = if (paddedWidth == width) {
        raw
    } else {
        Bitmap.createBitmap(raw, 0, 0, width, height).also { raw.recycle() }
    }

    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return cropped

    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return try {
        Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
    } finally {
        cropped.recycle()
    }
}
