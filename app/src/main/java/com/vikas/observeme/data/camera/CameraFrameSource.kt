package com.vikas.observeme.data.camera

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraMetadata
import android.media.Image
import android.media.ImageReader
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraFrameSource @Inject constructor() {

    // ImageReader sits between Camera2 and your code.
    // It owns a fixed-size buffer queue of Image objects.
    // maxImages = 2: double-buffering — one being processed,
    // one ready next. More = more memory. Less = dropped frames.
    private var imageReader: ImageReader? = null

    /**
     * Returns the ImageReader's Surface — added as a second
     * output target on the CaptureSession alongside the
     * preview SurfaceView.
     */
    fun getImageReaderSurface(size: Size): Surface {
        imageReader?.close() // close previous if any

        imageReader = ImageReader.newInstance(
            size.width,
            size.height,
            ImageFormat.YUV_420_888, // standard format for ML processing
            2                         // maxImages buffer count
        )
        return imageReader!!.surface
    }

    /**
     * Emits one Image per camera frame via callbackFlow.
     *
     * CRITICAL: every Image emitted MUST be closed by the
     * collector after use. If you forget image.close():
     *  - buffer queue fills up (maxImages = 2)
     *  - ImageReader stops delivering new frames
     *  - preview freezes
     *  - no error is thrown — silent failure
     *
     * This is one of the most common Camera2 bugs.
     */
    fun frameFlow(): Flow<Image> = callbackFlow {

        val reader = imageReader
            ?: error("Call getImageReaderSurface() before frameFlow()")

        val listener = ImageReader.OnImageAvailableListener { imgReader ->
            // acquireLatestImage: gets newest frame, discards older
            // ones still in queue. Use this (not acquireNextImage)
            // for face detection — you always want the freshest frame.
            val image = imgReader.acquireLatestImage() ?: return@OnImageAvailableListener

            // trySend is non-blocking — if collector is slow and
            // channel is full, the frame is dropped (not queued).
            // For real-time face detection this is correct behaviour.
            val result = trySend(image)

            // If send failed (collector busy), close immediately
            // to release the buffer back to the queue
            if (result.isFailure) {
                image.close()
            }
        }

        // null handler = callback on camera's internal thread
        // This is fine — trySend is thread-safe
        reader.setOnImageAvailableListener(listener, null)

        awaitClose {
            // Remove listener and close reader when flow is cancelled
            // (ViewModel cleared, Activity destroyed)
            reader.setOnImageAvailableListener(null, null)
            imageReader?.close()
            imageReader = null
        }
    }
}