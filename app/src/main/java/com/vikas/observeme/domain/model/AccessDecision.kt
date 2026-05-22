package com.vikas.observeme.domain.model

sealed class AccessDecision {
    object Idle : AccessDecision()
    object Scanning : AccessDecision()          // BLE detected, checking face
    object LivenessChallenge : AccessDecision() // face found, running liveness
    object Granted : AccessDecision()
    data class Denied(val reason: String) : AccessDecision()
}