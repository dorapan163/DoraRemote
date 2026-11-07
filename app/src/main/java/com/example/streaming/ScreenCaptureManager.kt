package com.example.streaming

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.example.network.EmbeddedWebSocketServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen Capture Manager.
 * Orchestrates MediaProjection + ImageReader virtual capture pipeline,
 * integrates with AdaptiveBitrateController for weak-network adaptation,
 * and broadcasts encrypted video frames over WebSocket.
 */
class ScreenCaptureManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val webSocketServer: EmbeddedWebSocketServer,
    private val bitrateController: AdaptiveBitrateController
) {
    companion object {
        private const val TAG = "ScreenCapture"
        private const val VIRTUAL_DISPLAY_NAME = "ScrcpyRemoteVirtualDisplay"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var simulationJob: Job? = null
    private var isStreaming = false

    // Active touch indicator for visual feedback on host
    var lastTouchX: Float? = null
    var lastTouchY: Float? = null
    var touchFeedbackExpiry: Long = 0

    fun startMediaProjectionCapture(projection: MediaProjection, screenWidth: Int = 1080, screenHeight: Int = 2400, densityDpi: Int = 400) {
        stop()
        mediaProjection = projection
        isStreaming = true

        backgroundThread = HandlerThread("ScrcpyCaptureThread").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)

        // Target capture resolution
        val targetWidth = (screenWidth * bitrateController.currentScale).toInt().coerceAtLeast(320)
        val targetHeight = (screenHeight * bitrateController.currentScale).toInt().coerceAtLeast(640)

        imageReader = ImageReader.newInstance(targetWidth, targetHeight, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (bitrateController.shouldSendFrame()) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val width = image.width
                    val height = image.height

                    val bitmap = Bitmap.createBitmap(
                        width + (rowStride - pixelStride * width) / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    // Crop to exact width
                    val cleanBitmap = if (bitmap.width != width) {
                        Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    } else {
                        bitmap
                    }

                    // Compress frame
                    val stream = ByteArrayOutputStream()
                    cleanBitmap.compress(Bitmap.CompressFormat.JPEG, bitrateController.currentJpegQuality, stream)
                    val frameBytes = stream.toByteArray()

                    webSocketServer.broadcastBinary(frameBytes)
                    bitrateController.onFrameSent(frameBytes.size)

                    if (cleanBitmap != bitmap) cleanBitmap.recycle()
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Frame processing error: ${e.message}")
            } finally {
                image.close()
            }
        }, backgroundHandler)

        virtualDisplay = projection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            targetWidth,
            targetHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )
    }

    /**
     * High-fidelity interactive screen simulation for testing, preview,
     * or when MediaProjection system dialog is pending.
     */
    fun startSimulatedStreaming() {
        stop()
        isStreaming = true
        simulationJob = scope.launch(Dispatchers.Default) {
            val baseWidth = 720
            val baseHeight = 1600
            val bitmap = Bitmap.createBitmap(baseWidth, baseHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            var frameIndex = 0L

            while (isActive && isStreaming) {
                if (bitrateController.shouldSendFrame()) {
                    // Render virtual interactive screen
                    drawSimulatedScreen(canvas, baseWidth, baseHeight, paint, frameIndex++)

                    // Draw touch cursor indicator if recently touched
                    val now = System.currentTimeMillis()
                    if (now < touchFeedbackExpiry && lastTouchX != null && lastTouchY != null) {
                        paint.color = Color.argb(180, 0, 229, 255)
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(lastTouchX!! * baseWidth, lastTouchY!! * baseHeight, 35f, paint)
                        paint.color = Color.WHITE
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 4f
                        canvas.drawCircle(lastTouchX!! * baseWidth, lastTouchY!! * baseHeight, 38f, paint)
                    }

                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, bitrateController.currentJpegQuality, stream)
                    val bytes = stream.toByteArray()

                    webSocketServer.broadcastBinary(bytes)
                    bitrateController.onFrameSent(bytes.size)
                }

                val sleepTime = (1000 / bitrateController.currentTargetFps.coerceAtLeast(10)).toLong()
                delay(sleepTime)
            }
            bitmap.recycle()
        }
    }

    private fun drawSimulatedScreen(canvas: Canvas, w: Int, h: Int, paint: Paint, frameIndex: Long) {
        // Background gradient
        canvas.drawColor(Color.parseColor("#0F172A"))

        // Top Status Bar
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRect(0f, 0f, w.toFloat(), 60f, paint)

        // Status bar clock
        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.textAlign = Paint.Align.LEFT
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        canvas.drawText(timeStr, 24f, 42f, paint)

        // Status bar battery & wifi
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("5G  100% 🔋", w - 24f, 42f, paint)

        // Screen Header Card
        paint.color = Color.parseColor("#1E293B")
        val headerRect = RectF(30f, 90f, w - 30f, 260f)
        canvas.drawRoundRect(headerRect, 24f, 24f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#00E5FF")
        paint.textSize = 34f
        paint.isFakeBoldText = true
        canvas.drawText("Scrcpy Remote 实时桌面", 60f, 150f, paint)

        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 24f
        paint.isFakeBoldText = false
        canvas.drawText("低延迟视频通道已就绪 · AES-256 加密", 60f, 190f, paint)
        canvas.drawText("帧序: #$frameIndex | 目标: ${bitrateController.currentTargetFps} FPS", 60f, 230f, paint)

        // Mock App Grid
        val apps = listOf(
            Triple("浏览器", "#3B82F6", "🌐"),
            Triple("相册", "#EC4899", "🖼️"),
            Triple("终端", "#10B981", "💻"),
            Triple("设置", "#8B5CF6", "⚙️"),
            Triple("视频", "#F59E0B", "▶️"),
            Triple("文件", "#06B6D4", "📁"),
            Triple("相机", "#EF4444", "📷"),
            Triple("安全中心", "#14B8A6", "🛡️")
        )

        val startY = 300f
        val cols = 4
        val colWidth = (w - 60) / cols
        for ((idx, app) in apps.withIndex()) {
            val col = idx % cols
            val row = idx / cols
            val cx = 30 + col * colWidth + colWidth / 2f
            val cy = startY + row * 160f + 60f

            paint.color = Color.parseColor(app.second)
            canvas.drawRoundRect(RectF(cx - 45f, cy - 45f, cx + 45f, cy + 45f), 20f, 20f, paint)

            paint.color = Color.WHITE
            paint.textSize = 36f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(app.third, cx, cy + 12f, paint)

            paint.color = Color.parseColor("#CBD5E1")
            paint.textSize = 22f
            canvas.drawText(app.first, cx, cy + 75f, paint)
        }

        // Live Dynamic Interactive Dashboard area
        val dashY = 660f
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(RectF(30f, dashY, w - 30f, h - 160f), 24f, 24f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#00E676")
        paint.textSize = 28f
        paint.isFakeBoldText = true
        canvas.drawText("⚡ 触控反馈与手势引擎监控", 60f, dashY + 60f, paint)

        paint.color = Color.parseColor("#E2E8F0")
        paint.textSize = 24f
        paint.isFakeBoldText = false
        val touchStr = if (lastTouchX != null && lastTouchY != null) {
            "最近触控坐标: X=${String.format(Locale.US, "%.3f", lastTouchX)} Y=${String.format(Locale.US, "%.3f", lastTouchY)}"
        } else {
            "等待控制端触控或多点手势输入..."
        }
        canvas.drawText(touchStr, 60f, dashY + 110f, paint)

        // Animated wave pattern indicating real-time render
        paint.color = Color.parseColor("#00E5FF")
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        val waveBaseY = dashY + 220f
        var prevX = 60f
        var prevY = waveBaseY
        for (x in 60..w - 60 step 15) {
            val angle = (x + frameIndex * 8) * 0.05
            val y = waveBaseY + (kotlin.math.sin(angle) * 35).toFloat()
            canvas.drawLine(prevX, prevY, x.toFloat(), y, paint)
            prevX = x.toFloat()
            prevY = y
        }
        paint.style = Paint.Style.FILL

        // Bottom Navigation Bar
        val navY = h - 110f
        paint.color = Color.parseColor("#0F172A")
        canvas.drawRect(0f, navY, w.toFloat(), h.toFloat(), paint)

        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 32f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("◁  ○  □", w / 2f, navY + 65f, paint)
    }

    fun onTouchReceived(normX: Float, normY: Float) {
        lastTouchX = normX
        lastTouchY = normY
        touchFeedbackExpiry = System.currentTimeMillis() + 800
    }

    fun stop() {
        isStreaming = false
        simulationJob?.cancel()
        simulationJob = null

        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            backgroundThread?.quitSafely()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            Log.d(TAG, "Error stopping capture: ${e.message}")
        }
    }
}
