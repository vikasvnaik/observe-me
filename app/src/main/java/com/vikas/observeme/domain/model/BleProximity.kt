package com.vikas.observeme.domain.model

data class BleProximity(
    val deviceName: String?,
    val rssi: Int,           // e.g. -55 dBm. Closer = higher (less negative)
    val isNear: Boolean      // true when rssi > threshold
)