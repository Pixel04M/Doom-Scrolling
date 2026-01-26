package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

fun ImageProxy.toBitmap(): Bitmap {
    val image = image ?: throw IllegalStateException("ImageProxy has no image")
    if (format != ImageFormat.YUV_420_888) {
        throw IllegalStateException("Unsupported ImageProxy format: $format")
    }

    val nv21 = yuv420888ToNv21(this)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)

    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
    val jpegBytes = out.toByteArray()
    var bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        ?: throw IllegalStateException("Failed to decode bitmap")

    val rotationDegrees = imageInfo.rotationDegrees
    if (rotationDegrees != 0) {
        val m = Matrix()
        m.postRotate(rotationDegrees.toFloat())
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    return bitmap
}

private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val width = image.width
    val height = image.height

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    yBuffer.rewind()
    uBuffer.rewind()
    vBuffer.rewind()

    val nv21 = ByteArray(width * height * 3 / 2)

    // Copy Y plane row by row to handle rowStride
    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride
    var outIndex = 0
    for (row in 0 until height) {
        val yRowStart = row * yRowStride
        for (col in 0 until width) {
            nv21[outIndex++] = yBuffer.get(yRowStart + col * yPixelStride)
        }
    }

    // Interleave VU for NV21
    val uvHeight = height / 2
    val uvWidth = width / 2
    val uRowStride = uPlane.rowStride
    val uPixelStride = uPlane.pixelStride
    val vRowStride = vPlane.rowStride
    val vPixelStride = vPlane.pixelStride

    for (row in 0 until uvHeight) {
        val uRowStart = row * uRowStride
        val vRowStart = row * vRowStride
        for (col in 0 until uvWidth) {
            val uIndex = uRowStart + col * uPixelStride
            val vIndex = vRowStart + col * vPixelStride
            nv21[outIndex++] = vBuffer.get(vIndex)
            nv21[outIndex++] = uBuffer.get(uIndex)
        }
    }

    return nv21
}
