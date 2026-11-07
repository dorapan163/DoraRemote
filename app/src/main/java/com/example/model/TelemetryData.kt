package com.example.model

/**
 * Real-time network and streaming telemetry for latency, throughput, and weak network adaptation.
 */
data class TelemetryData(
    val rttMs: Long = 0,
    val fps: Int = 0,
    val bitrateKbps: Int = 0,
    val droppedFrames: Int = 0,
    val totalFrames: Long = 0,
    val networkTier: NetworkTier = NetworkTier.EXCELLENT,
    val currentResolution: String = "1080x2400",
    val encryptionType: String = "AES-256-GCM",
    val packetLossPercentage: Float = 0.0f
)

enum class NetworkTier(val label: String, val colorHex: Long) {
    EXCELLENT("极佳 (低延迟)", 0xFF10B981),
    GOOD("良好", 0xFF06B6D4),
    FAIR("一般 (网络抖动)", 0xFFF59E0B),
    WEAK("弱网 (自适应降码率)", 0xFFEF4444)
}

data class QualityPreset(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val targetFps: Int,
    val targetBitrateKbps: Int,
    val isAdaptive: Boolean = false
) {
    companion object {
        val ULTRA = QualityPreset("ultra", "超清 1080P 60FPS", 1080, 2400, 60, 5000)
        val BALANCED = QualityPreset("balanced", "均衡 720P 30FPS", 720, 1600, 30, 2000)
        val SMOOTH = QualityPreset("smooth", "流畅 540P 24FPS", 540, 1200, 24, 1000)
        val WEAK_NET = QualityPreset("weak_net", "弱网极速 360P 15FPS", 360, 800, 15, 450)
        val ADAPTIVE = QualityPreset("adaptive", "智能自适应 (动态码率)", 720, 1600, 30, 2000, isAdaptive = true)

        val ALL = listOf(ADAPTIVE, ULTRA, BALANCED, SMOOTH, WEAK_NET)
    }
}
