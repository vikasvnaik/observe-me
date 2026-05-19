package com.vikas.facegate.presentation.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Wraps a SurfaceView in Compose via AndroidView.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onSurfaceReady: (SurfaceHolder) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                // Ensure the SurfaceView is displayed on top of the window background
                // but below the UI elements drawn by Compose.
                setZOrderMediaOverlay(true)
                
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        onSurfaceReady(holder)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int, width: Int, height: Int
                    ) { }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        onSurfaceDestroyed()
                    }
                })
            }
        }
    )
}