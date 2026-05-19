package com.vikas.facegate.data.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.view.Surface
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
    private val cameraDeviceManager: CameraDeviceManager,
    private val cameraSessionManager: CameraSessionManager
) {
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Closed)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Closed)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    // Holds the active CameraDevice so we can close it cleanly
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    
    // Store the surface to handle the race condition where the surface is ready 
    // before the camera hardware is fully opened.
    private var previewSurface: Surface? = null

    private var cameraJob: Job? = null
    private var sessionJob: Job? = null

    /**
     * Opens the front camera and tracks its state.
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
                    // If startPreview was called while we were opening, 
                    // we can now proceed to create the session.
                    previewSurface?.let { surface ->
                        startPreviewInternal(scope, surface)
                    }
                }
            }
        }
    }

    /**
     * Starts a preview session on the given surface.
     */
    fun startPreview(scope: CoroutineScope, surface: Surface) {
        previewSurface = surface
        val device = cameraDevice
        if (device != null) {
            startPreviewInternal(scope, surface)
        }
        // If device is null, startPreviewInternal will be called automatically 
        // once the camera state becomes Opened in the open() flow.
    }

    private fun startPreviewInternal(scope: CoroutineScope, surface: Surface) {
        val device = cameraDevice ?: return
        sessionJob?.cancel()
        sessionJob = scope.launch {
            cameraSessionManager.createSession(
                device = device,
                surfaces = listOf(surface)
            ).collect { state ->
                _sessionState.value = state
                if (state is SessionState.Ready) {
                    captureSession = state.session
                }
            }
        }
    }

    /**
     * Stops the preview session and clears the surface, but keeps the 
     * camera device open. This is useful for configuration changes.
     */
    fun stopPreview() {
        sessionJob?.cancel()
        captureSession?.close()
        captureSession = null
        previewSurface = null
        _sessionState.value = SessionState.Closed
    }

    /**
     * Closes the camera hardware and resets state.
     */
    fun close() {
        stopPreview()
        cameraJob?.cancel()
        cameraDevice?.close()
        cameraDevice = null
        _cameraState.value = CameraState.Closed
    }
}