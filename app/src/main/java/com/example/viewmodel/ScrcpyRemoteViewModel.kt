package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.projection.MediaProjection
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.AesGcmCrypto
import com.example.model.AuditLog
import com.example.model.ControlMessage
import com.example.model.GestureMessage
import com.example.model.HandshakeRequest
import com.example.model.HandshakeResponse
import com.example.model.HostPermissions
import com.example.model.KeyMessage
import com.example.model.LogLevel
import com.example.model.PermissionUpdateMessage
import com.example.model.PingMessage
import com.example.model.PongMessage
import com.example.model.QualityChangeMessage
import com.example.model.QualityPreset
import com.example.model.TelemetryData
import com.example.model.TouchMessage
import com.example.network.ConnectionState
import com.example.network.DiscoveredHost
import com.example.network.EmbeddedWebSocketServer
import com.example.network.LanDiscovery
import com.example.network.NetworkUtils
import com.example.network.WebSocketClientManager
import com.example.service.HostStreamingService
import com.example.service.RemoteAccessibilityService
import com.example.streaming.AdaptiveBitrateController
import com.example.streaming.ScreenCaptureManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

enum class AppRole {
    CONTROLLER, HOST
}

data class HostState(
    val isRunning: Boolean = false,
    val localIp: String = "127.0.0.1",
    val port: Int = 8765,
    val pairCode: String = "849203",
    val clientCount: Int = 0,
    val permissions: HostPermissions = HostPermissions(),
    val isSimulatedMode: Boolean = false,
    val connectedClientIp: String? = null,
    val pendingApprovalClient: Pair<EmbeddedWebSocketServer.ClientSession, HandshakeRequest>? = null
)

data class ClientUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val hostIp: String = "",
    val pairCode: String = "",
    val hostDeviceName: String = "",
    val grantedPermissions: HostPermissions = HostPermissions(),
    val latestFrame: Bitmap? = null,
    val isFullscreen: Boolean = false,
    val showTouchRipple: Boolean = true
)

class ScrcpyRemoteViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ScrcpyRemoteVM"
    }

    private val _currentRole = MutableStateFlow(AppRole.CONTROLLER)
    val currentRole: StateFlow<AppRole> = _currentRole.asStateFlow()

    private val _hostState = MutableStateFlow(HostState(
        localIp = NetworkUtils.getLocalIpAddress(),
        pairCode = AesGcmCrypto.generatePairingCode()
    ))
    val hostState: StateFlow<HostState> = _hostState.asStateFlow()

    private val _clientState = MutableStateFlow(ClientUiState())
    val clientState: StateFlow<ClientUiState> = _clientState.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditLog>> = _auditLogs.asStateFlow()

    private val _selectedPreset = MutableStateFlow(QualityPreset.ADAPTIVE)
    val selectedPreset: StateFlow<QualityPreset> = _selectedPreset.asStateFlow()

    val bitrateController = AdaptiveBitrateController()
    val telemetry: StateFlow<TelemetryData> = bitrateController.telemetry

    val lanDiscovery = LanDiscovery(viewModelScope)
    val discoveredHosts: StateFlow<Map<String, DiscoveredHost>> = lanDiscovery.discoveredHosts

    val webSocketClient = WebSocketClientManager(viewModelScope) { addLog(it) }
    val clientConnectionState: StateFlow<ConnectionState> = webSocketClient.connectionState
    val clientRtt: StateFlow<Long> = webSocketClient.rttMs

    private var webSocketServer: EmbeddedWebSocketServer? = null
    private var screenCaptureManager: ScreenCaptureManager? = null

    init {
        // Start discovering LAN hosts automatically
        lanDiscovery.startListening()

        // Handle incoming client frames
        viewModelScope.launch(Dispatchers.Default) {
            webSocketClient.incomingFrames.collect { frameBytes ->
                try {
                    val bitmap = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)
                    if (bitmap != null) {
                        _clientState.value = _clientState.value.copy(latestFrame = bitmap)
                        bitrateController.onFrameSent(frameBytes.size)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Frame decode error: ${e.message}")
                }
            }
        }

        // Handle incoming client text/JSON messages (HandshakeResponse, Pong, Permissions)
        viewModelScope.launch(Dispatchers.Default) {
            webSocketClient.incomingTextMessages.collect { msgStr ->
                val msg = ControlMessage.fromJson(msgStr) ?: return@collect
                when (msg) {
                    is HandshakeResponse -> {
                        if (msg.accepted) {
                            _clientState.value = _clientState.value.copy(
                                hostDeviceName = msg.hostDeviceName,
                                grantedPermissions = msg.permissions
                            )
                            addLog(AuditLog(
                                level = LogLevel.SUCCESS,
                                tag = "CLIENT",
                                message = "主机已授权接入: ${msg.hostDeviceName}",
                                details = "分辨率: ${msg.screenWidth}x${msg.screenHeight}, 触控: ${msg.permissions.allowTouch}"
                            ))
                        } else {
                            addLog(AuditLog(
                                level = LogLevel.ERROR,
                                tag = "CLIENT",
                                message = "主机拒绝连接: ${msg.reason}"
                            ))
                            webSocketClient.disconnect()
                        }
                    }
                    is PongMessage -> {
                        webSocketClient.handlePong(msg.serverTimestamp, msg.clientTimestamp)
                        bitrateController.onRttUpdated(webSocketClient.rttMs.value)
                    }
                    is PermissionUpdateMessage -> {
                        _clientState.value = _clientState.value.copy(grantedPermissions = msg.permissions)
                    }
                    else -> {}
                }
            }
        }

        // RTT updates trigger adaptive bitrate
        viewModelScope.launch {
            webSocketClient.rttMs.collect { rtt ->
                bitrateController.onRttUpdated(rtt)
            }
        }
    }

    fun setRole(role: AppRole) {
        _currentRole.value = role
        if (role == AppRole.HOST) {
            _hostState.value = _hostState.value.copy(localIp = NetworkUtils.getLocalIpAddress())
        }
    }

    fun regeneratePairCode() {
        val newCode = AesGcmCrypto.generatePairingCode()
        _hostState.value = _hostState.value.copy(pairCode = newCode)
        if (_hostState.value.isRunning) {
            webSocketServer?.crypto = AesGcmCrypto(AesGcmCrypto.deriveKey(newCode))
            lanDiscovery.startBroadcasting("Android Host", _hostState.value.port, newCode)
        }
        addLog(AuditLog(
            level = LogLevel.SECURITY,
            tag = "HOST",
            message = "已生成新安全配对码: $newCode",
            details = "AES-256 密钥已同步重置更新"
        ))
    }

    fun updatePermissions(permissions: HostPermissions) {
        _hostState.value = _hostState.value.copy(permissions = permissions)
        addLog(AuditLog(
            level = LogLevel.SECURITY,
            tag = "HOST",
            message = "被控端权限已更新",
            details = "触控:${permissions.allowTouch}, 按键:${permissions.allowKeys}, 剪贴板:${permissions.allowClipboard}"
        ))
    }

    fun startHost(mediaProjection: MediaProjection? = null, isSimulated: Boolean = false) {
        val pairCode = _hostState.value.pairCode
        val port = _hostState.value.port

        webSocketServer = EmbeddedWebSocketServer(viewModelScope, port) { addLog(it) }
        webSocketServer?.crypto = AesGcmCrypto(AesGcmCrypto.deriveKey(pairCode))
        webSocketServer?.start()

        screenCaptureManager = ScreenCaptureManager(
            getApplication(),
            viewModelScope,
            webSocketServer!!,
            bitrateController
        )

        if (mediaProjection != null && !isSimulated) {
            HostStreamingService.start(getApplication())
            screenCaptureManager?.startMediaProjectionCapture(mediaProjection)
            _hostState.value = _hostState.value.copy(isRunning = true, isSimulatedMode = false)
            addLog(AuditLog(
                level = LogLevel.SUCCESS,
                tag = "HOST",
                message = "MediaProjection 屏幕采集已启动",
                details = "端口: $port, 加密协议: AES-256-GCM"
            ))
        } else {
            screenCaptureManager?.startSimulatedStreaming()
            _hostState.value = _hostState.value.copy(isRunning = true, isSimulatedMode = true)
            addLog(AuditLog(
                level = LogLevel.INFO,
                tag = "HOST",
                message = "高清交互屏幕投屏已启动 (演示与测试模式)",
                details = "端口: $port, 实时渲染 60/30 FPS"
            ))
        }

        // Start UDP LAN broadcasting
        lanDiscovery.startBroadcasting("Scrcpy Remote Host", port, pairCode)

        // Listen for incoming host commands
        observeHostMessages()
    }

    private fun observeHostMessages() {
        val server = webSocketServer ?: return

        viewModelScope.launch(Dispatchers.Default) {
            server.incomingMessages.collect { (session, rawMsg) ->
                handleIncomingHostMessage(session, rawMsg)
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            server.incomingBinaryMessages.collect { (session, bytes) ->
                val str = String(bytes, StandardCharsets.UTF_8)
                handleIncomingHostMessage(session, str)
            }
        }

        viewModelScope.launch {
            server.connectedClientsCount.collect { count ->
                _hostState.value = _hostState.value.copy(clientCount = count)
            }
        }
    }

    private fun handleIncomingHostMessage(session: EmbeddedWebSocketServer.ClientSession, rawMsg: String) {
        val msg = ControlMessage.fromJson(rawMsg) ?: return
        when (msg) {
            is HandshakeRequest -> {
                if (msg.pairCode != _hostState.value.pairCode) {
                    val reject = HandshakeResponse(
                        accepted = false,
                        hostDeviceName = "Android Host",
                        screenWidth = 1080,
                        screenHeight = 2400,
                        permissions = _hostState.value.permissions,
                        reason = "配对码错误，无法建立 AES-256 会话"
                    )
                    webSocketServer?.sendMessage(session, reject.toJson())
                    addLog(AuditLog(
                        level = LogLevel.SECURITY,
                        tag = "AUTH",
                        message = "拦截未授权访问",
                        details = "来自: ${session.clientIp}, 设备: ${msg.deviceName}"
                    ))
                    return
                }

                if (_hostState.value.permissions.requireHostApproval) {
                    _hostState.value = _hostState.value.copy(
                        pendingApprovalClient = session to msg
                    )
                    addLog(AuditLog(
                        level = LogLevel.SECURITY,
                        tag = "AUTH",
                        message = "控制端请求接入: ${msg.deviceName}",
                        details = "IP: ${session.clientIp}, 等待被控端确认"
                    ))
                } else {
                    approvePendingClient(session, msg)
                }
            }
            is TouchMessage -> {
                if (_hostState.value.permissions.allowTouch) {
                    screenCaptureManager?.onTouchReceived(msg.x, msg.y)
                    val accessibility = RemoteAccessibilityService.getInstance()
                    if (accessibility != null) {
                        val metrics = getApplication<Application>().resources.displayMetrics
                        val absX = msg.x * metrics.widthPixels
                        val absY = msg.y * metrics.heightPixels
                        when (msg.action) {
                            "DOWN", "UP" -> accessibility.injectTap(absX, absY)
                        }
                    }
                }
            }
            is GestureMessage -> {
                if (_hostState.value.permissions.allowTouch) {
                    val accessibility = RemoteAccessibilityService.getInstance()
                    if (accessibility != null) {
                        val metrics = getApplication<Application>().resources.displayMetrics
                        val sx = msg.startX * metrics.widthPixels
                        val sy = msg.startY * metrics.heightPixels
                        val ex = msg.endX * metrics.widthPixels
                        val ey = msg.endY * metrics.heightPixels
                        when (msg.gestureType) {
                            "TAP" -> accessibility.injectTap(sx, sy)
                            "DOUBLE_TAP" -> {
                                accessibility.injectTap(sx, sy) {
                                    accessibility.injectTap(sx, sy)
                                }
                            }
                            "LONG_PRESS" -> accessibility.injectLongPress(sx, sy, msg.durationMs)
                            "SWIPE" -> accessibility.injectSwipe(sx, sy, ex, ey, msg.durationMs)
                            "PINCH" -> accessibility.injectPinch(sx, sy, msg.scale)
                        }
                    }
                }
            }
            is KeyMessage -> {
                if (_hostState.value.permissions.allowKeys) {
                    val accessibility = RemoteAccessibilityService.getInstance()
                    accessibility?.performSystemKey(msg.keyCode)
                    addLog(AuditLog(
                        level = LogLevel.INFO,
                        tag = "KEY",
                        message = "执行系统按键: ${msg.keyCode}"
                    ))
                }
            }
            is QualityChangeMessage -> {
                if (_hostState.value.permissions.allowQualityControl) {
                    val preset = QualityPreset(
                        id = "custom",
                        name = "Custom",
                        width = msg.targetWidth,
                        height = msg.targetHeight,
                        targetFps = msg.fps,
                        targetBitrateKbps = msg.bitrateKbps,
                        isAdaptive = msg.adaptiveMode
                    )
                    bitrateController.setPreset(preset)
                }
            }
            is PingMessage -> {
                val pong = PongMessage(
                    pingId = msg.pingId,
                    clientTimestamp = msg.timestamp,
                    serverTimestamp = System.currentTimeMillis()
                )
                webSocketServer?.sendMessage(session, pong.toJson())
            }
            else -> {}
        }
    }

    fun approvePendingClient(session: EmbeddedWebSocketServer.ClientSession, request: HandshakeRequest) {
        session.isAuthorized = true
        session.clientDeviceName = request.deviceName
        val res = HandshakeResponse(
            accepted = true,
            hostDeviceName = android.os.Build.MODEL ?: "Android Device",
            screenWidth = 1080,
            screenHeight = 2400,
            permissions = _hostState.value.permissions
        )
        webSocketServer?.sendMessage(session, res.toJson())
        _hostState.value = _hostState.value.copy(
            pendingApprovalClient = null,
            connectedClientIp = session.clientIp
        )
        addLog(AuditLog(
            level = LogLevel.SUCCESS,
            tag = "AUTH",
            message = "已批准接入: ${request.deviceName}",
            details = "IP: ${session.clientIp}, 授权完成"
        ))
    }

    fun rejectPendingClient(session: EmbeddedWebSocketServer.ClientSession, reason: String = "被控端管理员拒绝了连接请求") {
        val res = HandshakeResponse(
            accepted = false,
            hostDeviceName = "Android Host",
            screenWidth = 1080,
            screenHeight = 2400,
            permissions = _hostState.value.permissions,
            reason = reason
        )
        webSocketServer?.sendMessage(session, res.toJson())
        _hostState.value = _hostState.value.copy(pendingApprovalClient = null)
        addLog(AuditLog(
            level = LogLevel.WARNING,
            tag = "AUTH",
            message = "已拒绝控制端接入",
            details = "IP: ${session.clientIp}"
        ))
    }

    fun stopHost() {
        HostStreamingService.stop(getApplication())
        screenCaptureManager?.stop()
        screenCaptureManager = null
        webSocketServer?.stop()
        webSocketServer = null
        lanDiscovery.stopBroadcasting()
        _hostState.value = _hostState.value.copy(
            isRunning = false,
            clientCount = 0,
            connectedClientIp = null,
            pendingApprovalClient = null
        )
        addLog(AuditLog(
            level = LogLevel.INFO,
            tag = "HOST",
            message = "被控端服务已停止"
        ))
    }

    // Controller actions
    fun connectAsController(ip: String, port: Int = 8765, pairCode: String) {
        _clientState.value = _clientState.value.copy(
            hostIp = ip,
            pairCode = pairCode
        )
        webSocketClient.connect(ip, port, pairCode)

        // Send handshake immediately
        viewModelScope.launch {
            // Wait for connected
            var attempts = 0
            while (webSocketClient.connectionState.value != ConnectionState.CONNECTED && attempts++ < 30) {
                kotlinx.coroutines.delay(100)
            }
            if (webSocketClient.connectionState.value == ConnectionState.CONNECTED) {
                val handshake = HandshakeRequest(
                    deviceName = android.os.Build.MODEL ?: "Controller Device",
                    pairCode = pairCode
                )
                webSocketClient.sendEncrypted(handshake.toJson())
            }
        }
    }

    fun disconnectController() {
        webSocketClient.disconnect()
        _clientState.value = _clientState.value.copy(
            latestFrame = null,
            connectionState = ConnectionState.DISCONNECTED
        )
    }

    fun sendTouchEvent(action: String, pointerId: Int, normX: Float, normY: Float, pressure: Float = 1f) {
        if (!_clientState.value.grantedPermissions.allowTouch) return
        val touch = TouchMessage(action, pointerId, normX, normY, pressure)
        webSocketClient.sendEncrypted(touch.toJson())
    }

    fun sendGesture(gestureType: String, startX: Float, startY: Float, endX: Float = startX, endY: Float = startY, durationMs: Long = 100, scale: Float = 1.0f) {
        if (!_clientState.value.grantedPermissions.allowTouch) return
        val gesture = GestureMessage(gestureType, startX, startY, endX, endY, durationMs, scale)
        webSocketClient.sendEncrypted(gesture.toJson())
    }

    fun sendKey(keyCode: String, text: String = "") {
        if (!_clientState.value.grantedPermissions.allowKeys) return
        val key = KeyMessage(keyCode, text)
        webSocketClient.sendEncrypted(key.toJson())
        addLog(AuditLog(
            level = LogLevel.INFO,
            tag = "KEY",
            message = "控制端发送按键: $keyCode"
        ))
    }

    fun sendText(text: String) {
        if (!_clientState.value.grantedPermissions.allowKeyboard) return
        val key = KeyMessage("TEXT", text)
        webSocketClient.sendEncrypted(key.toJson())
    }

    fun setQualityPreset(preset: QualityPreset) {
        _selectedPreset.value = preset
        bitrateController.setPreset(preset)
        val msg = QualityChangeMessage(
            targetWidth = preset.width,
            targetHeight = preset.height,
            fps = preset.targetFps,
            bitrateKbps = preset.targetBitrateKbps,
            adaptiveMode = preset.isAdaptive
        )
        webSocketClient.sendEncrypted(msg.toJson())
        addLog(AuditLog(
            level = LogLevel.INFO,
            tag = "QUALITY",
            message = "画质预设已切换: ${preset.name}"
        ))
    }

    fun toggleFullscreen(fullscreen: Boolean) {
        _clientState.value = _clientState.value.copy(isFullscreen = fullscreen)
    }

    fun toggleTouchRipple(show: Boolean) {
        _clientState.value = _clientState.value.copy(showTouchRipple = show)
    }

    fun addLog(log: AuditLog) {
        val current = _auditLogs.value.toMutableList()
        current.add(0, log)
        if (current.size > 200) current.removeAt(current.size - 1)
        _auditLogs.value = current
    }

    fun clearLogs() {
        _auditLogs.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        lanDiscovery.stopListening()
        stopHost()
        webSocketClient.disconnect()
    }
}
