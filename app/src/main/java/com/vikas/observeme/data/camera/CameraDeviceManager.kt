package com.vikas.observeme.data.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Represents the camera hardware state at any point in time */
sealed class CameraState {
    object Closed : CameraState()
    object Opening : CameraState()
    data class Opened(val device: CameraDevice) : CameraState()
    data class Error(val code: Int, val message: String) : CameraState()
    object Disconnected : CameraState()
}

@Singleton
class CameraDeviceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /**
     * Returns the ID of the front-facing camera.
     * Swiftlane uses front camera for face recognition.
     */
    fun getFrontCameraId(): String {
        return cameraManager.cameraIdList.first { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
        }
    }

    /**
     * Opens the camera and emits state transitions as a Flow.
     *
     * callbackFlow is mandatory here — CameraDevice.StateCallback
     * delivers results on a separate thread via OS callbacks.
     * We can't use regular flow { } because we can't emit
     * from inside a callback.
     */
    @SuppressLint("MissingPermission") // permission checked before calling
    fun openCamera(cameraId: String): Flow<CameraState> = callbackFlow {

        trySend(CameraState.Opening)

        val callback = object : CameraDevice.StateCallback() {

            // Called when camera hardware is ready
            override fun onOpened(camera: CameraDevice) {
                trySend(CameraState.Opened(camera))
                // Do NOT close the flow here — camera stays open
                // until the collector cancels or we get error/disconnect
            }

            // Called when camera is disconnected (another app took it,
            // or the device was physically unplugged)
            override fun onDisconnected(camera: CameraDevice) {
                camera.close() // always close the device handle
                trySend(CameraState.Disconnected)
                close()        // complete the flow
            }

            // Called on hardware error — code tells you what went wrong
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                val message = when (error) {
                    ERROR_CAMERA_IN_USE      -> "Camera already in use"
                    ERROR_MAX_CAMERAS_IN_USE -> "Too many cameras open"
                    ERROR_CAMERA_DISABLED    -> "Camera disabled by policy"
                    ERROR_CAMERA_DEVICE      -> "Fatal camera device error"
                    ERROR_CAMERA_SERVICE     -> "Fatal camera service error"
                    else                     -> "Unknown error: $error"
                }
                trySend(CameraState.Error(error, message))
                close()
            }
        }

        // Open the camera — result arrives via callback above
        cameraManager.openCamera(cameraId, callback, null)

        // awaitClose runs when:
        // 1. The collector's scope is cancelled (Activity destroyed)
        // 2. close() is called above (error/disconnect)
        // This is your hardware cleanup guarantee
        awaitClose {
            // The CameraDevice is closed by the collector
            // who holds the reference via CameraState.Opened
            // We just log here — actual close is in CameraRepository
        }
    }
}