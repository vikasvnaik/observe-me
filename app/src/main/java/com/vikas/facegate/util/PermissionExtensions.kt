package com.vikas.facegate.util

import android.Manifest
import android.os.Build

// All permissions this app needs, split by feature
object AppPermissions {

    val camera = listOf(Manifest.permission.CAMERA)

    val ble = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    val all = camera + ble
}