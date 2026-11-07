package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TouchRipple(
    val id: Long,
    val offset: Offset,
    val alpha: Float = 1.0f
)

/**
 * Interactive Remote Desktop Canvas.
 * Renders incoming decrypted video stream frames and handles multi-touch,
 * tap, double tap, long press, drag, swipe, and pinch gestures.
 */
@Composable
fun RemoteControlCanvas(
    bitmap: Bitmap?,
    showRipple: Boolean = true,
    onTap: (normX: Float, normY: Float) -> Unit,
    onDoubleTap: (normX: Float, normY: Float) -> Unit,
    onLongPress: (normX: Float, normY: Float) -> Unit,
    onSwipe: (startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long) -> Unit,
    onPinch: (centerX: Float, centerY: Float, scale: Float) -> Unit,
    onTouchDown: (pointerId: Int, normX: Float, normY: Float) -> Unit,
    onTouchMove: (pointerId: Int, normX: Float, normY: Float) -> Unit,
    onTouchUp: (pointerId: Int, normX: Float, normY: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val ripples = remember { mutableStateListOf<TouchRipple>() }

    fun addRipple(offset: Offset) {
        if (!showRipple) return
        val ripple = TouchRipple(System.currentTimeMillis(), offset)
        ripples.add(ripple)
        coroutineScope.launch {
            delay(400)
            ripples.remove(ripple)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("remote_desktop_canvas"),
        contentAlignment = Alignment.Center
    ) {
        val aspect = if (bitmap != null && bitmap.height > 0) {
            bitmap.width.toFloat() / bitmap.height.toFloat()
        } else {
            9f / 19.5f
        }

        Box(
            modifier = Modifier
                .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            var dragStartOffset by remember { mutableStateOf<Offset?>(null) }
            var dragStartTime by remember { mutableStateOf(0L) }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            if (zoom != 1.0f) {
                                val normCx = (centroid.x / size.width).coerceIn(0f, 1f)
                                val normCy = (centroid.y / size.height).coerceIn(0f, 1f)
                                onPinch(normCx, normCy, zoom)
                            }
                        }
                    }
                    .pointerInput(bitmap) {
                        detectTapGestures(
                            onTap = { offset ->
                                val normX = (offset.x / size.width).coerceIn(0f, 1f)
                                val normY = (offset.y / size.height).coerceIn(0f, 1f)
                                addRipple(offset)
                                onTap(normX, normY)
                            },
                            onDoubleTap = { offset ->
                                val normX = (offset.x / size.width).coerceIn(0f, 1f)
                                val normY = (offset.y / size.height).coerceIn(0f, 1f)
                                addRipple(offset)
                                onDoubleTap(normX, normY)
                            },
                            onLongPress = { offset ->
                                val normX = (offset.x / size.width).coerceIn(0f, 1f)
                                val normY = (offset.y / size.height).coerceIn(0f, 1f)
                                addRipple(offset)
                                onLongPress(normX, normY)
                            }
                        )
                    }
                    .pointerInput(bitmap) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStartOffset = offset
                                dragStartTime = System.currentTimeMillis()
                                val normX = (offset.x / size.width).coerceIn(0f, 1f)
                                val normY = (offset.y / size.height).coerceIn(0f, 1f)
                                onTouchDown(0, normX, normY)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val normX = (change.position.x / size.width).coerceIn(0f, 1f)
                                val normY = (change.position.y / size.height).coerceIn(0f, 1f)
                                onTouchMove(0, normX, normY)
                            },
                            onDragEnd = {
                                val start = dragStartOffset
                                if (start != null) {
                                    val duration = System.currentTimeMillis() - dragStartTime
                                    // Swipe completed
                                    val normSx = (start.x / size.width).coerceIn(0f, 1f)
                                    val normSy = (start.y / size.height).coerceIn(0f, 1f)
                                    onSwipe(normSx, normSy, normSx, normSy, duration)
                                }
                                onTouchUp(0, 0.5f, 0.5f)
                                dragStartOffset = null
                            },
                            onDragCancel = {
                                onTouchUp(0, 0.5f, 0.5f)
                                dragStartOffset = null
                            }
                        )
                    }
            ) {
                if (bitmap != null && !bitmap.isRecycled) {
                    val imageBitmap = bitmap.asImageBitmap()
                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                }

                // Render touch ripples for user feedback
                for (ripple in ripples) {
                    drawCircle(
                        color = CyberCyan.copy(alpha = 0.45f),
                        radius = 45f,
                        center = ripple.offset
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.7f),
                        radius = 20f,
                        center = ripple.offset
                    )
                }
            }

            if (bitmap == null) {
                Text(
                    text = "等待接收远程视频流...",
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
