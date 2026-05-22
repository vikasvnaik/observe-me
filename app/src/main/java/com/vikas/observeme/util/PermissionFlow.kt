package com.vikas.observeme.util

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.vikas.observeme.domain.model.PermissionState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/**
 * Emits current permission state immediately, then launches
 * the system dialog if needed, and emits the result.
 *
 * callbackFlow is used because the result arrives via a
 * callback (ActivityResultLauncher) — not inline.
 */
fun ComponentActivity.permissionFlow(
    permissions: List<String>
): Flow<PermissionState> = callbackFlow {

    // 1. Check if already granted — emit and close immediately
    val alreadyGranted = permissions.all { perm ->
        ContextCompat.checkSelfPermission(this@permissionFlow, perm) ==
                PackageManager.PERMISSION_GRANTED
    }
    if (alreadyGranted) {
        trySend(PermissionState.Granted)
        close() // flow completes — no dialog needed
        return@callbackFlow
    }

    // 2. Check if rationale should be shown
    val needsRationale = permissions.any { perm ->
        shouldShowRequestPermissionRationale(perm)
    }
    if (needsRationale) {
        trySend(PermissionState.Rationale)
        // Note: The logic below will still trigger the system dialog.
        // If you want to wait for user to click "Grant" in your rationale UI,
        // you might want to return early here or use a different trigger.
    }

    // 3. Register the launcher using the registry directly.
    // This avoids the "must call register before they are STARTED" check
    // which occurs when using registerForActivityResult(contract, callback).
    val key = "PermissionFlow_${UUID.randomUUID()}"
    val launcher = activityResultRegistry.register(
        key,
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys.toList()
        if (denied.isEmpty()) {
            trySend(PermissionState.Granted)
        } else {
            trySend(PermissionState.Denied(denied))
        }
        close() // flow completes after result
    }

    launcher.launch(permissions.toTypedArray())

    // 4. awaitClose — must manually unregister since we didn't provide a LifecycleOwner
    awaitClose {
        launcher.unregister()
    }
}