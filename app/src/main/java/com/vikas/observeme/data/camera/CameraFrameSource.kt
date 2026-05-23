package com.vikas.observeme.data.camera

import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

data class CameraFrame(val nv21: ByteArray, val width: Int, val height: Int)

@Singleton
class CameraFrameSource @Inject constructor() {

    private var imageReader: ImageReader? = null

    // Dedicated background thread so NV21 extraction never blocks the main thread
    private val callbackThread = HandlerThread("CameraFrameSource").also { it.start() }
    private val callbackHandler = Handler(callbackThread.looper)

    fun getImageReaderSurface(size: Size): Surface {
        imageReader?.close()
        imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
        Log.d("FrameSource", "ImageReader created ${size.width}x${size.height}")
        return imageReader!!.surface
    }

    fun frameFlow(): Flow<CameraFrame> = callbackFlow {
        val reader = imageReader
            ?: error("Call getImageReaderSurface() before frameFlow()")

        Log.d("FrameSource", "frameFlow started — attaching listener")

        val listener = ImageReader.OnImageAvailableListener { imgReader ->
            // Acquire, extract bytes, and close the Image immediately so the
            // ImageReader buffer is freed before the next frame arrives.
            val image = imgReader.acquireLatestImage() ?: return@OnImageAvailableListener
            try {
                val frame = image.toCameraFrame()
                val result = trySend(frame)
                if (result.isFailure) {
                    Log.w("FrameSource", "channel full — dropping frame")
                }
            } finally {
                image.close()
            }
        }

        reader.setOnImageAvailableListener(listener, callbackHandler)

        awaitClose {
            Log.d("FrameSource", "frameFlow awaitClose — removing listener")
            reader.setOnImageAvailableListener(null, callbackHandler)
            if (imageReader === reader) {
                reader.close()
                imageReader = null
            }
        }
    }

    // YUV_420_888 → NV21, handles row/pixel stride differences across devices.
    private fun Image.toCameraFrame(): CameraFrame {
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val nv21 = ByteArray(width * height * 3 / 2)
        var pos = 0

        for (row in 0 until height) {
            for (col in 0 until width) {
                nv21[pos++] = yBuf.get(row * yRowStride + col)
            }
        }
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                nv21[pos++] = vBuf.get(row * uvRowStride + col * uvPixelStride)
                nv21[pos++] = uBuf.get(row * uvRowStride + col * uvPixelStride)
            }
        }

        return CameraFrame(nv21, width, height)
    }
}
