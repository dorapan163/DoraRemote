package com.example.model

import org.json.JSONObject

/**
 * Control and protocol messages exchanged over the secure WebSocket channel.
 */
sealed class ControlMessage {
    abstract val type: String
    abstract fun toJson(): String

    companion object {
        fun fromJson(jsonStr: String): ControlMessage? {
            return try {
                val obj = JSONObject(jsonStr)
                when (obj.optString("type")) {
                    "handshake_req" -> HandshakeRequest(
                        deviceName = obj.optString("deviceName", "Unknown Controller"),
                        pairCode = obj.optString("pairCode", ""),
                        clientVersion = obj.optString("clientVersion", "1.0")
                    )
                    "handshake_res" -> HandshakeResponse(
                        accepted = obj.optBoolean("accepted", false),
                        hostDeviceName = obj.optString("hostDeviceName", "Android Host"),
                        screenWidth = obj.optInt("screenWidth", 1080),
                        screenHeight = obj.optInt("screenHeight", 2400),
                        permissions = HostPermissions(
                            allowTouch = obj.optBoolean("allowTouch", true),
                            allowKeys = obj.optBoolean("allowKeys", true),
                            allowKeyboard = obj.optBoolean("allowKeyboard", true),
                            allowClipboard = obj.optBoolean("allowClipboard", true),
                            allowAudio = obj.optBoolean("allowAudio", false),
                            allowQualityControl = obj.optBoolean("allowQualityControl", true)
                        ),
                        reason = obj.optString("reason", "")
                    )
                    "touch" -> TouchMessage(
                        action = obj.optString("action", "MOVE"),
                        pointerId = obj.optInt("pointerId", 0),
                        x = obj.optDouble("x", 0.0).toFloat(),
                        y = obj.optDouble("y", 0.0).toFloat(),
                        pressure = obj.optDouble("pressure", 1.0).toFloat()
                    )
                    "gesture" -> GestureMessage(
                        gestureType = obj.optString("gestureType", "TAP"),
                        startX = obj.optDouble("startX", 0.0).toFloat(),
                        startY = obj.optDouble("startY", 0.0).toFloat(),
                        endX = obj.optDouble("endX", 0.0).toFloat(),
                        endY = obj.optDouble("endY", 0.0).toFloat(),
                        durationMs = obj.optLong("durationMs", 100),
                        scale = obj.optDouble("scale", 1.0).toFloat()
                    )
                    "key" -> KeyMessage(
                        keyCode = obj.optString("keyCode", "BACK"),
                        text = obj.optString("text", "")
                    )
                    "clipboard" -> ClipboardMessage(
                        text = obj.optString("text", "")
                    )
                    "quality_change" -> QualityChangeMessage(
                        targetWidth = obj.optInt("targetWidth", 720),
                        targetHeight = obj.optInt("targetHeight", 1600),
                        fps = obj.optInt("fps", 30),
                        bitrateKbps = obj.optInt("bitrateKbps", 2000),
                        adaptiveMode = obj.optBoolean("adaptiveMode", true)
                    )
                    "ping" -> PingMessage(
                        pingId = obj.optLong("pingId", 0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                    "pong" -> PongMessage(
                        pingId = obj.optLong("pingId", 0),
                        clientTimestamp = obj.optLong("clientTimestamp", 0),
                        serverTimestamp = obj.optLong("serverTimestamp", System.currentTimeMillis())
                    )
                    "permission_update" -> PermissionUpdateMessage(
                        permissions = HostPermissions(
                            allowTouch = obj.optBoolean("allowTouch", true),
                            allowKeys = obj.optBoolean("allowKeys", true),
                            allowKeyboard = obj.optBoolean("allowKeyboard", true),
                            allowClipboard = obj.optBoolean("allowClipboard", true),
                            allowAudio = obj.optBoolean("allowAudio", false),
                            allowQualityControl = obj.optBoolean("allowQualityControl", true)
                        )
                    )
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class HandshakeRequest(
    val deviceName: String,
    val pairCode: String,
    val clientVersion: String = "1.0"
) : ControlMessage() {
    override val type: String = "handshake_req"
    override fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("deviceName", deviceName)
        put("pairCode", pairCode)
        put("clientVersion", clientVersion)
    }.toString()
}

data class HandshakeResponse(
    val accepted: Boolean,
    val hostDeviceName: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val permissions: HostPermissions,
    val reason: String = ""
) : ControlMessage() {
    override val type: String = "handshake_res"
    override fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("accepted", accepted)
        put("hostDeviceName", hostDeviceName)
        put("screenWidth", screenWidth)
        put("screenHeight", screenHeight)
        put("allowTouch", permissions.allowTouch)
        put("allowKeys", permissions.allowKeys)
        put("allowKeyboard", permissions.allowKeyboard)
        put("allowClipboard", permissions.allowClipboard)
        put("allowAudio", permissions.allowAudio)
        put("allowQualityControl", permissions.allowQualityControl)
        put("reason", reason)
    }.toString()
}

data class TouchMessage(
    val action: String, // "DOWN", "MOVE", "UP", "POINTER_DOWN", "POINTER_UP", "CANCEL"
    val pointerId: Int,
    val x: Float, // Normalized 0.0 .. 1.0
    val y: Float, // Normalized 0.0 .. 1.0
    val pressure: Float = 1.0f
) : ControlMessage() {
    override val type: String = "touch"
    override fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("action", action)
        put("pointerId", pointerId)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("pressure", pressure.toDouble())
    }.toString()
}

data class GestureMessage(
    val gestureType: String, // "TAP", "DOUBLE_TAP", "LONG_PRESS", "SWIPE", "PINCH", "SCROLL"
    val startX: Float,
    val startY: Float,
    val endX: Float = startX,
    val endY: Float = startY,
    val durationMs: Long = 100,
    val scale: Float = 1.0f
) : ControlMessage() {
    override val type: String = "gesture"
    override fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("gestureType", gestureType)
        put("startX", startX.toDouble())
        put("startY", startY.toDouble())
        put("endX", endX.toDouble())
        put("endY", endY.toDouble())
        put("durationMs", durationMs)
        put("scale", scale.toDouble())
    }.toString()
}

data class KeyMessage(
    val keyCode: String, // "BACK", "HOME", "RECENTS", "POWER", "VOLUME_UP", "VOLUME_DOWN", "TEXT"
    val text: String = ""
) : ControlMessage() {
    override val type: String = "key"
    override fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("keyCode", keyCode)
        put("text", text)
    }.toString()
}

data class ClipboardMessage(
    val text: String
) : ControlMessage() {
    override val type: String = "clipboard"
    override fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("text", text)
    }.toString()
}

data class QualityChangeMessage(
    val targetWidth: Int,
    val targetHeight: Int,
    val fps: Int,
    val bitrateKbps: Int,
    val adaptiveMode: Boolean
) : ControlMessage() {
    override val type: String = "quality_change"
    override fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("targetWidth", targetWidth)
        put("targetHeight", targetHeight)
        put("fps", fps)
        put("bitrateKbps", bitrateKbps)
        put("adaptiveMode", adaptiveMode)
    }.toString()
}

data class PingMessage(
    val pingId: Long,
    val timestamp: Long
) : ControlMessage() {
    override val type: String = "ping"
    override fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("pingId", pingId)
        put("timestamp", timestamp)
    }.toString()
}

data class PongMessage(
    val pingId: Long,
    val clientTimestamp: Long,
    val serverTimestamp: Long
) : ControlMessage() {
    override val type: String = "pong"
    override fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("pingId", pingId)
        put("clientTimestamp", clientTimestamp)
        put("serverTimestamp", serverTimestamp)
    }.toString()
}

data class PermissionUpdateMessage(
    val permissions: HostPermissions
) : ControlMessage() {
    override val type: String = "permission_update"
    override fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("allowTouch", permissions.allowTouch)
        put("allowKeys", permissions.allowKeys)
        put("allowKeyboard", permissions.allowKeyboard)
        put("allowClipboard", permissions.allowClipboard)
        put("allowAudio", permissions.allowAudio)
        put("allowQualityControl", permissions.allowQualityControl)
    }.toString()
}
