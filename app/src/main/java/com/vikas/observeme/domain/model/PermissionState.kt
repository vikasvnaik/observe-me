package com.vikas.observeme.domain.model

sealed class PermissionState {
    object Granted : PermissionState()
    data class Denied(val permissions: List<String>) : PermissionState()
    object Rationale : PermissionState() // user denied once, show explanation
}