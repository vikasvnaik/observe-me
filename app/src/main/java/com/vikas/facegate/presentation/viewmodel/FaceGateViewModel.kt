package com.vikas.facegate.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.vikas.facegate.domain.model.AccessDecision
import com.vikas.facegate.domain.model.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class FaceGateViewModel @Inject constructor(): ViewModel() {

    private val _accessDecision = MutableStateFlow<AccessDecision>(AccessDecision.Idle)
    val accessDecision: StateFlow<AccessDecision> = _accessDecision.asStateFlow()

    private val _permissionState = MutableStateFlow<PermissionState?>(null)
    val permissionState: StateFlow<PermissionState?> = _permissionState.asStateFlow()
    private val _debugLog = MutableStateFlow("Waiting...")
    val debugLog: StateFlow<String> = _debugLog.asStateFlow()

    fun onPermissionState(state: PermissionState) {
        _permissionState.value = state
        _debugLog.value = when(state) {
            is PermissionState.Granted  -> "All permissions granted"
            is PermissionState.Denied   -> "Denied: ${state.permissions.joinToString()}"
            is PermissionState.Rationale -> "Please grant camera & BLE permissions"
        }
    }

    fun log(msg: String) {
        _debugLog.value = msg
    }
}