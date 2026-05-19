package com.vikas.facegate.presentation.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Wraps a SurfaceView in Compose via AndroidView.
 * SurfaceView is not a Composable — it's a raw Android
 * view that owns a hardware-composited Surface buffer.
 * Camera2 requires this kind of Surface for preview.
 *
 * onSurfaceReady: called when the Surface is created
 *   and ready to receive camera frames.
 * onSurfaceDestroyed: called when Surface is torn down
 *   — must stop the camera session here.
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
                holder.addCallback(object : SurfaceHolder.Callback {

                    // Surface created — safe to start camera session
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        onSurfaceReady(holder)
                    }

                    // Surface size changed (rotation, resize)
                    // In a production app you'd reconfigure the
                    // session here with the new dimensions
                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int, width: Int, height: Int
                    ) { /* handle rotation in a later step */ }

                    // Surface destroyed — must stop session
                    // before the Surface buffer is released
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        onSurfaceDestroyed()
                    }
                })
            }
        }
    )
}