package com.vikas.facegate.data.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class SessionState {
    object Configuring : SessionState()
    data class Ready(val session: CameraCaptureSession) : SessionState()
    object Failed : SessionState()
    object Closed : SessionState()
}

@Singleton
class CameraSessionManager @Inject constructor() {

    /**
     * Creates a CaptureSession for the given surfaces and emits
     * its state as a Flow. The session is configured with a
     * repeating preview request — this is what makes frames
     * continuously flow to the Surface (and your screen).
     *
     * surfaces: list of output targets.
     *   Phase 2: [previewSurface]
     *   Phase 3: [previewSurface, imageReaderSurface]
     */
    fun createSession(
        device: CameraDevice,
        surfaces: List<Surface>
    ): Flow<SessionState> = callbackFlow {

        trySend(SessionState.Configuring)

        val callback = object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) {
                try {
                    // Build a repeating preview request
                    // TEMPLATE_PREVIEW sets sensible defaults:
                    // auto-exposure, auto-focus, auto-white-balance
                    val requestBuilder = device.createCaptureRequest(
                        CameraDevice.TEMPLATE_PREVIEW
                    ).apply {
                        // Add every surface as an output target
                        surfaces.forEach { addTarget(it) }
                        // Enable continuous auto-focus — important
                        // for face detection accuracy
                        set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        )
                        set(
                            CaptureRequest.CONTROL_MODE,
                            CameraMetadata.CONTROL_MODE_AUTO
                        )
                    }

                    // setRepeatingRequest sends this request to the
                    // camera hardware on every frame — typically 30fps
                    session.setRepeatingRequest(
                        requestBuilder.build(),
                        null, // capture callback — we use ImageReader later
                        null  // handler — null = current thread
                    )

                    trySend(SessionState.Ready(session))

                } catch (e: Exception) {
                    trySend(SessionState.Failed)
                    close(e)
                }
            }

            // Configuration failed — usually means an invalid
            // Surface size or format was requested
            override fun onConfigureFailed(session: CameraCaptureSession) {
                trySend(SessionState.Failed)
                close()
            }

            // Session closed — clean up
            override fun onClosed(session: CameraCaptureSession) {
                trySend(SessionState.Closed)
                close()
            }
        }

        @Suppress("DEPRECATION")
        device.createCaptureSession(surfaces, callback, null)

        awaitClose {
            // Closed by CameraRepository when camera is released
        }
    }
}