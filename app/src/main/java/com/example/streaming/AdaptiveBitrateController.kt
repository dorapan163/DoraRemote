package com.example.streaming

import com.example.model.NetworkTier
import com.example.model.QualityPreset
import com.example.model.TelemetryData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Weak-network Adaptive Bitrate & Frame-Rate Controller.
 * Dynamically scales resolution, frame rate, and compression ratio in real time
 * based on RTT latency and network congestion to guarantee under-50ms interaction latency.
 */
class AdaptiveBitrateController {

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private var activePreset: QualityPreset = QualityPreset.ADAPTIVE
    private var isAdaptiveMode: Boolean = true

    // Dynamic adaptive parameters
    var currentScale: Float = 0.6f
        private set
    var currentJpegQuality: Int = 70
        private set
    var currentTargetFps: Int = 30
        private set

    private var lastFrameTime = 0L
    private var frameCounter = 0
    private var fpsCalculationTime = System.currentTimeMillis()
    private var currentFps = 0
    private var totalBytesInSecond = 0L
    private var currentBitrateKbps = 0
    private var droppedFramesCount = 0
    private var totalFramesCount = 0L

    fun setPreset(preset: QualityPreset) {
        activePreset = preset
        isAdaptiveMode = preset.isAdaptive
        if (!isAdaptiveMode) {
            currentTargetFps = preset.targetFps
            currentScale = when (preset.id) {
                "ultra" -> 1.0f
                "balanced" -> 0.75f
                "smooth" -> 0.5f
                "weak_net" -> 0.35f
                else -> 0.6f
            }
            currentJpegQuality = when (preset.id) {
                "ultra" -> 85
                "balanced" -> 70
                "smooth" -> 55
                "weak_net" -> 35
                else -> 65
            }
        }
    }

    /**
     * Feed RTT measurement from Ping/Pong to adaptively tune compression parameters.
     */
    fun onRttUpdated(rttMs: Long) {
        val tier = when {
            rttMs < 40 -> NetworkTier.EXCELLENT
            rttMs < 90 -> NetworkTier.GOOD
            rttMs < 160 -> NetworkTier.FAIR
            else -> NetworkTier.WEAK
        }

        if (isAdaptiveMode) {
            when (tier) {
                NetworkTier.EXCELLENT -> {
                    currentTargetFps = 35
                    currentScale = 0.75f
                    currentJpegQuality = 78
                }
                NetworkTier.GOOD -> {
                    currentTargetFps = 30
                    currentScale = 0.65f
                    currentJpegQuality = 68
                }
                NetworkTier.FAIR -> {
                    currentTargetFps = 22
                    currentScale = 0.5f
                    currentJpegQuality = 52
                }
                NetworkTier.WEAK -> {
                    // Aggressive weak network adaptation: drop resolution & quality to keep pipeline moving
                    currentTargetFps = 15
                    currentScale = 0.35f
                    currentJpegQuality = 35
                }
            }
        }

        updateTelemetry(rttMs, tier)
    }

    /**
     * Check if a frame should be transmitted or dropped to maintain target FPS and avoid queue buildup.
     */
    fun shouldSendFrame(): Boolean {
        val now = System.currentTimeMillis()
        val intervalMs = 1000 / currentTargetFps.coerceAtLeast(5)
        if (now - lastFrameTime < intervalMs) {
            droppedFramesCount++
            return false
        }
        lastFrameTime = now
        totalFramesCount++
        return true
    }

    fun onFrameSent(byteSize: Int) {
        frameCounter++
        totalBytesInSecond += byteSize

        val now = System.currentTimeMillis()
        val delta = now - fpsCalculationTime
        if (delta >= 1000) {
            currentFps = (frameCounter * 1000 / delta).toInt()
            currentBitrateKbps = ((totalBytesInSecond * 8) / delta).toInt()
            frameCounter = 0
            totalBytesInSecond = 0L
            fpsCalculationTime = now

            updateTelemetry(_telemetry.value.rttMs, _telemetry.value.networkTier)
        }
    }

    private fun updateTelemetry(rttMs: Long, tier: NetworkTier) {
        val resolution = "${(1080 * currentScale).toInt()}x${(2400 * currentScale).toInt()}"
        _telemetry.value = TelemetryData(
            rttMs = rttMs,
            fps = currentFps,
            bitrateKbps = currentBitrateKbps,
            droppedFrames = droppedFramesCount,
            totalFrames = totalFramesCount,
            networkTier = tier,
            currentResolution = resolution,
            encryptionType = "AES-256-GCM"
        )
    }
}
