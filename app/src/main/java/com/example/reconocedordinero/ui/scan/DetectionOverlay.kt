package com.example.reconocedordinero.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun DetectionOverlay(boxes: List<androidx.compose.ui.geometry.Rect>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 3f)
        for (b in boxes) {
            drawRoundRect(color = Color(0xFFFFD84D), topLeft = b.topLeft, size = b.size, style = stroke, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f,8f))
        }
    }
}
