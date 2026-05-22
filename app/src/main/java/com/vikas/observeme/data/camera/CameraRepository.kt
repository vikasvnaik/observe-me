package com.vikas.observeme.data.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.media.Image
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Standard preview/analysis size — good balance of
// performance vs accuracy for face detection
private val ANALYSIS_SIZE = Size(640, 480)

@Singleton
class CameraRepository @Inject constructor(
    private val cameraDeviceManager: CameraDeviceManager,
    private val cameraSessionManager: CameraSessionManager,
    private val cameraFrameSource: CameraFrameSource
) {
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Closed)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Closed)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    
    // We keep a reference to the surface so that if the camera is closed and 
    // reopened (e.g. on pause/resume), we can automatically restart the 
    // preview if the surface is still valid.
    private var previewSurface: Surface? = null

    private var cameraJob: Job? = null
    private var sessionJob: Job? = null

    fun open(scope: CoroutineScope) {
        cameraJob?.cancel()
        cameraJob = scope.launch {
            val cameraId = cameraDeviceManager.getFrontCameraId()
            cameraDeviceManager.openCamera(cameraId).collect { state ->
                _cameraState.value = state
                if (state is CameraState.Opened) {
                    cameraDevice = state.device
                    // Auto-start preview if we already have a valid surface
                    previewSurface?.let { surface ->
                        if (surface.isValid) {
                            startPreviewInternal(scope, surface)
                        }
                    }
                }
            }
        }
    }

    fun startPreview(scope: CoroutineScope, surface: Surface) {
        previewSurface = surface
        if (cameraDevice != null && surface.isValid) {
            startPreviewInternal(scope, surface)
        }
    }

    private fun startPreviewInternal(scope: CoroutineScope, surface: Surface) {
        val device = cameraDevice ?: return
        sessionJob?.cancel()

        // Get the ImageReader surface — second output target
        val analysisSurface = cameraFrameSource.getImageReaderSurface(ANALYSIS_SIZE)

        sessionJob = scope.launch {
            cameraSessionManager.createSession(
                device = device,
                surfaces = listOf(surface, analysisSurface)
            ).collect { state ->
                _sessionState.value = state
                if (state is SessionState.Ready) {
                    captureSession = state.session
                }
            }
        }
    }

    /**
     * The frame flow — collected by the face detector in Phase 3.
     * Each Image MUST be closed by the collector after processing.
     */
    fun frameFlow(): Flow<Image> = cameraFrameSource.frameFlow()

    fun stopPreview() {
        sessionJob?.cancel()
        captureSession?.close()
        captureSession = null
        _sessionState.value = SessionState.Closed
        // We DO NOT clear previewSurface here because it might still be valid 
        // for the next camera session (e.g. after a resume).
    }

    /**
     * Called when the UI surface is actually destroyed.
     */
    fun releasePreviewSurface() {
        stopPreview()
        previewSurface = null
    }

    fun close() {
        stopPreview() // This keeps the surface ref but stops the session
        cameraJob?.cancel()
        cameraDevice?.close()
        cameraDevice = null
        _cameraState.value = CameraState.Closed
    }
}