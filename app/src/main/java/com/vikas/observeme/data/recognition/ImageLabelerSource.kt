package com.vikas.observeme.data.recognition

import android.graphics.ImageFormat
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.vikas.observeme.data.camera.CameraFrame
import com.vikas.observeme.domain.model.LabelResult
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class ImageLabelerSource @Inject constructor() {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
    )

    suspend fun label(frame: CameraFrame, rotationDegrees: Int = 90): List<LabelResult> =
        suspendCancellableCoroutine { cont ->
            try {
                val inputImage = InputImage.fromByteArray(
                    frame.nv21,
                    frame.width,
                    frame.height,
                    rotationDegrees,
                    ImageFormat.NV21
                )
                labeler.process(inputImage)
                    .addOnSuccessListener { labels ->
                        cont.resume(labels.map { LabelResult(it.text, it.confidence) })
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                    .addOnCanceledListener { cont.cancel() }
            } catch (e: Exception) {
                Log.e("ImageLabelerSource", "InputImage error", e)
                cont.resume(emptyList())
            }
        }

    fun close() = labeler.close()
}
