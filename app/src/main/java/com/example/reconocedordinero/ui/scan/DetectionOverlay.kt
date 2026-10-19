package com.example.reconocedordinero.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reconocedordinero.domain.MoneyFormatter
import com.example.reconocedordinero.ml.Detection
import com.example.reconocedordinero.ui.theme.Amarillo
import com.example.reconocedordinero.ui.theme.TextoOscuro

/**
 * Overlay decorativo sobre la vista de cámara: recuadro amarillo con esquinas redondeadas y
 * etiqueta pegada a la esquina superior izquierda. Es invisible para TalkBack.
 */
@Composable
fun DetectionOverlay(
    detections: List<Detection>,
    bitmapWidth: Int,
    bitmapHeight: Int,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = remember {
        TextStyle(color = TextoOscuro, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }

    Canvas(modifier = modifier.fillMaxSize().clearAndSetSemantics { }) {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return@Canvas

        // Equivale a FIT_CENTER: misma escala en ambos ejes y centrado.
        val scale = minOf(size.width / bitmapWidth, size.height / bitmapHeight)
        val offsetX = (size.width - bitmapWidth * scale) / 2f
        val offsetY = (size.height - bitmapHeight * scale) / 2f

        val strokeWidth = 3.dp.toPx()
        val corner = CornerRadius(12f, 12f)
        val pad = 4.dp.toPx()

        for (detection in detections) {
            val label = labels.getOrNull(detection.cls) ?: continue
            if (!MoneyFormatter.isMoney(label)) continue

            val left = detection.x1 * scale + offsetX
            val top = detection.y1 * scale + offsetY
            val width = (detection.x2 - detection.x1) * scale
            val height = (detection.y2 - detection.y1) * scale
            if (width <= 1f || height <= 1f) continue

            drawRoundRect(
                color = Amarillo,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = corner,
                style = Stroke(width = strokeWidth),
            )

            val layout = textMeasurer.measure(
                text = AnnotatedString(MoneyFormatter.overlayLabel(label)),
                style = textStyle,
            )
            drawRect(
                color = Amarillo,
                topLeft = Offset(left, top),
                size = Size(layout.size.width + pad * 2, layout.size.height + pad),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(left + pad, top + pad / 2f),
            )
        }
    }
}
