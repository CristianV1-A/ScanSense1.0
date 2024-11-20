package com.example.scansense10

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.io.IOException

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
class ObjectDetectionAnalyzer(
    private val activity: MainActivity
) : ImageAnalysis.Analyzer {

    companion object {
        const val TAG = "MainActivity"
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()
            activity.runObjectDetection(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante a análise de imagem: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }


    @Suppress("unused")
    private fun ImageProxy.toBitmap(): Bitmap? {
        if (format != ImageFormat.YUV_420_888) {
            Log.e(TAG, "Formato de imagem incompatível: $format")
            return null
        }
        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
            val imageBytes = out.toByteArray()
            out.close()

            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao converter ImageProxy para Bitmap: ${e.message}")
            null
        }
    }
}
