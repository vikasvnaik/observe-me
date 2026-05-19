package com.vikas.facegate.presentation.viewmodel

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vikas.facegate.data.camera.CameraRepository
import com.vikas.facegate.data.camera.CameraState
import com.vikas.facegate.data.camera.SessionState
import com.vikas.facegate.domain.model.AccessDecision
import com.vikas.facegate.domain.model.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FaceGateViewModel @Inject constructor(
    private val cameraRepository: CameraRepository
): ViewModel() {

    private val _accessDecision = MutableStateFlow<AccessDecision>(AccessDecision.Idle)
    val accessDecision: StateFlow<AccessDecision> = _accessDecision.asStateFlow()

    private val _permissionState = MutableStateFlow<PermissionState?>(null)
    val permissionState: StateFlow<PermissionState?> = _permissionState.asStateFlow()
    private val _debugLog = MutableStateFlow("Waiting...")
    val debugLog: StateFlow<String> = _debugLog.asStateFlow()

    val cameraState: StateFlow<CameraState> = cameraRepository.cameraState
    val sessionState: StateFlow<SessionState> = cameraRepository.sessionState

    private var frameCountJob: Job? = null

    fun onPermissionState(state: PermissionState) {
        _permissionState.value = state
        when(state) {
            is PermissionState.Granted  -> {
                _debugLog.value = "All permissions granted"
                openCamera()
            }
            is PermissionState.Denied   -> _debugLog.value = "Denied: ${state.permissions.joinToString()}"
            is PermissionState.Rationale -> _debugLog.value = "Please grant camera & BLE permissions"
        }
    }

    fun openCamera() {
        cameraRepository.open(viewModelScope)

        viewModelScope.launch {
            cameraRepository.cameraState.collect { state ->
                _debugLog.value = when(state) {
                    is CameraState.Opening -> "Opening camera..."
                    is CameraState.Opened  -> "Camera opened successfully"
                    is CameraState.Error   -> "Camera error: ${state.message}"
                    is CameraState.Disconnected -> "Camera disconnected"
                    is CameraState.Closed -> "Camera closed"

                }
            }
        }
    }

    fun startFrameCounter() {
        frameCountJob?.cancel()
        frameCountJob = viewModelScope.launch {
            var count = 0
            cameraRepository.frameFlow()
                .collect { image ->
                    count++
                    // ALWAYS close the image — even in this test
                    image.close()
                    if (count % 30 == 0) { // log every 30 frames (~1 sec)
                        _debugLog.value = "Frames received: $count (~${count/30} sec)"
                    }
                }
        }
    }

    fun startPreview(previewSurface: Surface) {
        cameraRepository.startPreview(viewModelScope, previewSurface)
        // Small delay to let session configure before collecting frames
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            startFrameCounter()
        }
    }

    fun stopPreview() {
        cameraRepository.stopPreview()
    }

    fun onSurfaceDestroyed() {
        cameraRepository.releasePreviewSurface()
    }

    fun closeCamera() = cameraRepository.close()

    override fun onCleared() {
        super.onCleared()
        cameraRepository.close()
    }
    fun log(msg: String) {
        _debugLog.value = msg
    }
}