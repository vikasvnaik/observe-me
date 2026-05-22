package com.vikas.observeme.presentation.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.vikas.observeme.domain.model.PermissionState
import com.vikas.observeme.presentation.ui.theme.ObserveMeTheme
import com.vikas.observeme.presentation.viewmodel.ObserveMeViewModel
import com.vikas.observeme.util.AppPermissions
import com.vikas.observeme.util.permissionFlow
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ObserveMeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initial check for permissions
        lifecycleScope.launch {
            permissionFlow(AppPermissions.all).collect { state ->
                viewModel.onPermissionState(state)
            }
        }

        setContent {
            ObserveMeTheme {
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
 * Main Content layout.
 * Shows CameraPreview if granted, otherwise shows status/request UI.
 * Avoids opaque Surface backgrounds in the Granted state to ensure 
 * the CameraPreview (SurfaceView) is visible.
 */
@Composable
fun MainContent(
    log: String,
    permissionState: PermissionState?,
    viewModel: ObserveMeViewModel?,
    onRequestPermissions: () -> Unit
) {
    // Root container: No opaque Surface here when permissions are granted
    Box(modifier = Modifier.fillMaxSize()) {
        when (permissionState) {
            is PermissionState.Granted -> {
                // 1. Camera Preview filling the background layer
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onSurfaceReady = { holder ->
                        viewModel?.startPreview(holder.surface)
                    },
                    onSurfaceDestroyed = {
                        // Notify VM that the surface is gone to release hardware
                        viewModel?.onSurfaceDestroyed()
                    }
                )

                // 2. UI Overlay (Debug Text) sits on top of the camera frames
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                ) {
                    Text(
                        text = log,
                        modifier = Modifier.align(Alignment.TopCenter),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            is PermissionState.Denied,
            is PermissionState.Rationale -> {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onRequestPermissions) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }

            else -> {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    ObserveMeTheme {
        MainContent(
            log = "Waiting for permissions...",
            permissionState = PermissionState.Rationale,
            viewModel = null,
            onRequestPermissions = {}
        )
    }
}