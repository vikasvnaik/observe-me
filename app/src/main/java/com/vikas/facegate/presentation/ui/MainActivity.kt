package com.vikas.facegate.presentation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.vikas.facegate.domain.model.PermissionState
import com.vikas.facegate.presentation.ui.theme.FaceGateTheme
import com.vikas.facegate.presentation.viewmodel.FaceGateViewModel
import com.vikas.facegate.util.AppPermissions
import com.vikas.facegate.util.permissionFlow
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: FaceGateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initial check for permissions
        lifecycleScope.launch {
            permissionFlow(AppPermissions.all).collect { state ->
                viewModel.onPermissionState(state)
            }
        }

        setContent {
            FaceGateTheme {
                val log by viewModel.debugLog.collectAsState()
                val permissionState by viewModel.permissionState.collectAsState()
                
                MainContent(
                    log = log, 
                    permissionState = permissionState,
                    viewModel = viewModel,
                    onRequestPermissions = {
                        // Launch permission flow from the Activity context
                        lifecycleScope.launch {
                            permissionFlow(AppPermissions.all).collect { state ->
                                viewModel.onPermissionState(state)
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val state = viewModel.permissionState.value
        if(state is PermissionState.Granted) {
            viewModel.openCamera()
        }
    }

    override fun onPause() {
        super.onPause()

        viewModel.closeCamera()
    }
}

/**
 * Refactored MainContent to use State Hoisting.
 * The 'onRequestPermissions' callback allows the Activity to handle the permission logic,
 * which is necessary because 'permissionFlow' is an extension function of ComponentActivity.
 */
@Composable
fun MainContent(
    log: String,
    permissionState: PermissionState?,
    viewModel: FaceGateViewModel?,
    onRequestPermissions: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (permissionState) {
                is PermissionState.Granted -> {
                    // Show camera preview full screen
                    Box(modifier = Modifier.fillMaxSize()) {

                        CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            onSurfaceReady = { holder ->
                                viewModel?.startPreview(holder.surface)
                            },
                            onSurfaceDestroyed = {
                                viewModel?.stopPreview()
                            }
                        )

                        // Debug overlay — top of screen
                        Text(
                            text = log,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is PermissionState.Denied,
                is PermissionState.Rationale -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(log)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRequestPermissions) {
                            Text("Grant Permissions")
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    FaceGateTheme {
        // Passing mock data for Preview
        MainContent(
            log = "Waiting for permissions...",
            permissionState = PermissionState.Rationale,
            viewModel = null,
            onRequestPermissions = {}
        )
    }
}