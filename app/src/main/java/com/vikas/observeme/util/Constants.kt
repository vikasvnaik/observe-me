package com.vikas.observeme.util

object Constants {
    const val BLE_RSSI_THRESHOLD = -70        // dBm — closer than this = "near"
    const val FACE_CONFIDENCE_THRESHOLD = 0.85f
    const val LIVENESS_BLINK_THRESHOLD = 0.4f // eye open prob below = blink
    const val UNLOCK_DECISION_TIMEOUT_MS = 5_000L
    const val BLE_SCAN_PERIOD_MS = 10_000L
}