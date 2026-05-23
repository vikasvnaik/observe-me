package com.vikas.observeme.presentation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.vikas.observeme.domain.model.LabelResult
import com.vikas.observeme.domain.model.PermissionState
import com.vikas.observeme.domain.model.RecognitionState
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

        lifecycleScope.launch {
            permissionFlow(AppPermissions.all).collect { state ->
                viewModel.onPermissionState(state)
            }
        }

        setContent {
            ObserveMeTheme {
                val permissionState by viewModel.permissionState.collectAsState()
                val recognitionState by viewModel.recognitionState.collectAsState()

                MainContent(
                    permissionState = permissionState,
                    recognitionState = recognitionState,
                    viewModel = viewModel,
                    onRequestPermissions = {
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
        if (viewModel.permissionState.value is PermissionState.Granted) {
            viewModel.openCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.closeCamera()
    }
}

@Composable
fun MainContent(
    permissionState: PermissionState?,
    recognitionState: RecognitionState,
    viewModel: ObserveMeViewModel?,
    onRequestPermissions: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (permissionState) {
            is PermissionState.Granted -> {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onSurfaceReady = { holder -> viewModel?.startPreview(holder.surface) },
                    onSurfaceDestroyed = { viewModel?.onSurfaceDestroyed() }
                )

                // Scanning status — top-left
                val scanningText = when (recognitionState) {
                    is RecognitionState.Scanning   -> "Scanning..."
                    is RecognitionState.LiveLabels -> "Live"
                    else -> null
                }
                scanningText?.let {
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 48.dp, start = 12.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Live label chips — top-right corner
                if (recognitionState is RecognitionState.LiveLabels) {
                    LiveLabelsOverlay(
                        labels = recognitionState.labels,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp, end = 12.dp)
                    )
                }

                // Bottom card — shown during analysis and after result
                when (recognitionState) {
                    is RecognitionState.Analyzing -> AnalyzingCard(
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                    is RecognitionState.Result -> ResultCard(
                        trigger = recognitionState.trigger,
                        description = recognitionState.description,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                    else -> {}
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
                            text = "Camera permission is required to scan objects.",
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

@Composable
private fun LiveLabelsOverlay(labels: List<LabelResult>, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        labels.forEach { label ->
            Text(
                text = "${label.text}  ${(label.confidence * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AnalyzingCard(modifier: Modifier) {
    Card(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Analyzing with Gemini...",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ResultCard(trigger: String, description: String, modifier: Modifier) {
    Card(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = trigger.uppercase(),
                color = Color(0xFF90CAF9),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    ObserveMeTheme {
        MainContent(
            permissionState = PermissionState.Rationale,
            recognitionState = RecognitionState.Scanning,
            viewModel = null,
            onRequestPermissions = {}
        )
    }
}
