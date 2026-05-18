package com.vikas.facegate.domain.model

data class FaceResult(
    val boundingBox: android.graphics.RectF,
    val leftEyeOpenProbability: Float?,   // for liveness (Phase 5)
    val rightEyeOpenProbability: Float?,
    val headEulerAngleY: Float?,          // for head-turn challenge
    val score: Float                       // confidence 0..1
)