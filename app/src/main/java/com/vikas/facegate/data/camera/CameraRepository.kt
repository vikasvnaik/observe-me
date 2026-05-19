package com.vikas.facegate.data.camera

import android.hardware.camera2.CameraDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraRepository @Inject constructor(
    private val cameraDeviceManager: CameraDeviceManager
) {
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Closed)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    // Holds the active CameraDevice so we can close it cleanly
    private var cameraDevice: CameraDevice? = null
    private var cameraJob: Job? = null

    /**
     * Opens the front camera and tracks its state.
     * scope should be the ViewModel's viewModelScope —
     * cancelled automatically when ViewModel is cleared.
     */
    fun open(scope: CoroutineScope) {
        // Cancel any existing open attempt first
        cameraJob?.cancel()

        cameraJob = scope.launch {
            val cameraId = cameraDeviceManager.getFrontCameraId()
            cameraDeviceManager.openCamera(cameraId).collect { state ->
                _cameraState.value = state

                // Track the device reference so we can close it
                if (state is CameraState.Opened) {
                    cameraDevice = state.device
                }
            }
        }
    }

    /**
     * Closes the camera hardware and resets state.
     * Always call this in onPause or when done — Camera2
     * holds an exclusive lock on the hardware.
     */
    fun close() {
        cameraJob?.cancel()
        cameraDevice?.close()
        cameraDevice = null
        _cameraState.value = CameraState.Closed
    }
}