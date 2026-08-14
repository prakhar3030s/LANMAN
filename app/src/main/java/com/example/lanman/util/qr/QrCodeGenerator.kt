package com.example.lanman.util.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QrCodeGenerator {
    suspend fun generateQrCode(content: String, size: Int = 512): Bitmap? = withContext(Dispatchers.Default) {
        if (content.isBlank()) return@withContext null
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    suspend fun generateQrBooleanMatrix(content: String, size: Int = 33): Array<BooleanArray>? = withContext(Dispatchers.Default) {
        if (content.isBlank()) return@withContext null
        try {
            val writer = QRCodeWriter()
            val hints = mapOf(com.google.zxing.EncodeHintType.MARGIN to 2)
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val matrix = Array(height) { BooleanArray(width) }
            for (x in 0 until width) {
                for (y in 0 until height) {
                    matrix[y][x] = bitMatrix.get(x, y)
                }
            }
            matrix
        } catch (e: Exception) {
            null
        }
    }
}
