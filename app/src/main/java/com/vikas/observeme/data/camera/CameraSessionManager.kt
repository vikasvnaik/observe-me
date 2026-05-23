package com.vikas.observeme.data.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log
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

    fun createSession(
        device: CameraDevice,
        surfaces: List<Surface>
    ): Flow<SessionState> = callbackFlow {

        Log.d("CameraSession", "createSession called with ${surfaces.size} surfaces")
        trySend(SessionState.Configuring)

        val callback = object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) {
                Log.d("CameraSession", "onConfigured — building repeating request")
                try {
                    val requestBuilder = device.createCaptureRequest(
                        CameraDevice.TEMPLATE_PREVIEW
                    ).apply {
                        surfaces.forEach { addTarget(it) }
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    }

                    session.setRepeatingRequest(requestBuilder.build(), null, null)
                    Log.d("CameraSession", "setRepeatingRequest OK → emitting Ready")
                    trySend(SessionState.Ready(session))

                } catch (e: Exception) {
                    Log.e("CameraSession", "setRepeatingRequest FAILED", e)
                    trySend(SessionState.Failed)
                    close(e)
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e("CameraSession", "onConfigureFailed")
                trySend(SessionState.Failed)
                close()
            }

            override fun onClosed(session: CameraCaptureSession) {
                Log.d("CameraSession", "onClosed")
                trySend(SessionState.Closed)
                close()
            }
        }

        @Suppress("DEPRECATION")
        device.createCaptureSession(surfaces, callback, null)

        awaitClose {
            Log.d("CameraSession", "awaitClose — flow cancelled")
        }
    }
}
