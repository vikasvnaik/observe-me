package com.vikas.observeme.data.recognition

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import com.vikas.observeme.data.camera.CameraFrame
import com.vikas.observeme.domain.model.LabelResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageLabelRepository @Inject constructor(
    private val labelerSource: ImageLabelerSource
) {
    // Emits JPEG bytes of a captured frame for Gemini analysis.
    // DROP_OLDEST ensures a slow Gemini call never blocks the labeling flow.
    private val _capturedFrames = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val capturedFrames: Flow<ByteArray> = _capturedFrames.asSharedFlow()

    @Volatile private var captureRequested = false

    fun requestCapture() {
        captureRequested = true
    }

    fun labelFlow(frameFlow: Flow<CameraFrame>, rotationDegrees: Int = 90): Flow<List<LabelResult>> =
        frameFlow
            .map { frame ->
                Log.d("LabelRepo", "frame received ${frame.width}x${frame.height} rot=$rotationDegrees")
                try {
                    if (captureRequested) {
                        captureRequested = false
                        _capturedFrames.tryEmit(frame.toJpegBytes())
                    }
                    val results = labelerSource.label(frame, rotationDegrees)
                    Log.d("LabelRepo", "MLKit returned ${results.size} labels: ${results.map { "${it.text}=${it.confidence}" }}")
                    results
                } catch (e: Exception) {
                    Log.e("LabelRepo", "MLKit error", e)
                    emptyList()
                }
                // No image.close() needed — Image is closed inside CameraFrameSource callback
            }
            .catch { e ->
                Log.e("LabelRepo", "Fatal flow error", e)
                emit(emptyList())
            }
            .flowOn(Dispatchers.Default)

    // NV21 bytes are already extracted — JPEG conversion is a single call.
    private fun CameraFrame.toJpegBytes(): ByteArray {
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        return ByteArrayOutputStream().also {
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 85, it)
        }.toByteArray()
    }
}
