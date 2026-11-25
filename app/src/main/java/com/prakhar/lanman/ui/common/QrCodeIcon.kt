package com.prakhar.lanman.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A small QR glyph. The app only ships the core Material icon set, which has no QR icon, and a
 * person icon next to "Scan QR" made it unclear that the button scans a code.
 */
@Composable
fun QrCodeIcon(
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(24.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val cell = size.minDimension / 11f
        val stroke = Stroke(width = cell)
        fun finder(x: Float, y: Float) {
            drawRect(
                color = tint,
                topLeft = Offset(x * cell, y * cell),
                size = Size(cell * 3f, cell * 3f),
                style = stroke
            )
            drawRect(
                color = tint,
                topLeft = Offset((x + 1f) * cell, (y + 1f) * cell),
                size = Size(cell, cell)
            )
        }
        finder(0f, 0f)
        finder(8f, 0f)
        finder(0f, 8f)
        // A few data modules so it reads as a QR code rather than three boxes.
        drawRect(tint, Offset(4f * cell, 0f), Size(cell, cell))
        drawRect(tint, Offset(5f * cell, 2f * cell), Size(cell, cell))
        drawRect(tint, Offset(4f * cell, 4f * cell), Size(cell * 3f, cell * 3f))
        drawRect(tint, Offset(0f, 4f * cell), Size(cell, cell))
        drawRect(tint, Offset(2f * cell, 5f * cell), Size(cell, cell))
        drawRect(tint, Offset(8f * cell, 4f * cell), Size(cell, cell))
        drawRect(tint, Offset(10f * cell, 5f * cell), Size(cell, cell))
        drawRect(tint, Offset(9f * cell, 6f * cell), Size(cell, cell))
        drawRect(tint, Offset(4f * cell, 8f * cell), Size(cell, cell))
        drawRect(tint, Offset(5f * cell, 10f * cell), Size(cell, cell))
        drawRect(tint, Offset(7f * cell, 8f * cell), Size(cell * 2f, cell))
        drawRect(tint, Offset(8f * cell, 10f * cell), Size(cell * 3f, cell))
        drawRect(tint, Offset(1f * cell, 10f * cell), Size(cell * 2f, cell))
    }
}
