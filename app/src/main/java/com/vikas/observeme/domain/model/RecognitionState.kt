package com.vikas.observeme.domain.model

sealed class RecognitionState {
    object Scanning : RecognitionState()
    data class LiveLabels(val labels: List<LabelResult>) : RecognitionState()
    object Analyzing : RecognitionState()
    data class Result(val description: String, val trigger: String) : RecognitionState()
}
