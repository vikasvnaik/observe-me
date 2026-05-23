package com.vikas.observeme.presentation.viewmodel

import android.util.Log
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vikas.observeme.data.camera.CameraRepository
import com.vikas.observeme.data.camera.CameraState
import com.vikas.observeme.data.camera.SessionState
import com.vikas.observeme.data.recognition.GeminiAnalyzer
import com.vikas.observeme.data.recognition.ImageLabelRepository
import com.vikas.observeme.domain.model.PermissionState
import com.vikas.observeme.domain.model.RecognitionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ObserveMeViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val imageLabelRepository: ImageLabelRepository,
    private val geminiAnalyzer: GeminiAnalyzer
) : ViewModel() {

    private val _permissionState = MutableStateFlow<PermissionState?>(null)
    val permissionState: StateFlow<PermissionState?> = _permissionState.asStateFlow()

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Scanning)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    val cameraState: StateFlow<CameraState> = cameraRepository.cameraState

    private var previewWatchJob: Job? = null
    private var scanningJob: Job? = null
    private var lastAnalysisTime = 0L
    private var pendingTriggerLabel = ""
    private val ANALYSIS_COOLDOWN_MS = 60_000L

    fun onPermissionState(state: PermissionState) {
        _permissionState.value = state
        if (state is PermissionState.Granted) openCamera()
    }

    fun openCamera() = cameraRepository.open(viewModelScope)

    fun startPreview(previewSurface: Surface) {
        cameraRepository.startPreview(viewModelScope, previewSurface)

        previewWatchJob?.cancel()
        previewWatchJob = viewModelScope.launch {
            cameraRepository.sessionState.collect { state ->
                Log.d("ViewModel", "sessionState → $state")
                if (state is SessionState.Ready) startScanning()
            }
        }
    }

    private fun startScanning() {
        Log.d("ViewModel", "startScanning called")
        scanningJob?.cancel()
        scanningJob = viewModelScope.launch {

            launch {
                imageLabelRepository.capturedFrames.collect { jpegBytes ->
                    runGeminiAnalysis(jpegBytes)
                }
            }

            while (isActive) {
                Log.d("ViewModel", "labelFlow loop — starting new collection")
                imageLabelRepository
                    .labelFlow(cameraRepository.frameFlow())
                    .collect { labels ->
                        Log.d("ViewModel", "labels received: ${labels.size} — ${labels.map { it.text }}")
                        if (labels.isEmpty()) return@collect

                        val current = _recognitionState.value
                        if (current is RecognitionState.Analyzing ||
                            current is RecognitionState.Result) return@collect

                        _recognitionState.value = RecognitionState.LiveLabels(labels.take(5))

                        val now = System.currentTimeMillis()
                        if (now - lastAnalysisTime < ANALYSIS_COOLDOWN_MS) return@collect

                        val trigger = labels.firstOrNull {
                            it.text in geminiAnalyzer.interestingLabels && it.confidence >= 0.7f
                        } ?: return@collect

                        lastAnalysisTime = now
                        pendingTriggerLabel = trigger.text
                        imageLabelRepository.requestCapture()
                    }

                Log.d("ViewModel", "labelFlow completed — retrying in 300ms")
                if (isActive) delay(300)
            }
        }
    }

    private suspend fun runGeminiAnalysis(jpegBytes: ByteArray) {
        _recognitionState.value = RecognitionState.Analyzing
        val result = geminiAnalyzer.analyze(jpegBytes, pendingTriggerLabel)
        _recognitionState.value = RecognitionState.Result(result, pendingTriggerLabel)
        delay(8_000L)
        _recognitionState.value = RecognitionState.Scanning
    }

    fun stopPreview() = cameraRepository.stopPreview()

    fun onSurfaceDestroyed() {
        previewWatchJob?.cancel()
        cameraRepository.releasePreviewSurface()
    }

    fun closeCamera() = cameraRepository.close()

    override fun onCleared() {
        super.onCleared()
        cameraRepository.close()
    }
}
