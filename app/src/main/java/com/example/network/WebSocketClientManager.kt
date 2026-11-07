package com.example.network

import android.util.Log
import com.example.crypto.AesGcmCrypto
import com.example.model.AuditLog
import com.example.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
}

/**
 * Controller WebSocket Client Manager. Connects to the Host's WebSocket server,
 * negotiates AES-256 end-to-end encryption, calculates RTT latency,
 * sends touch/gesture/key commands, and receives video frames.
 */
class WebSocketClientManager(
    private val scope: CoroutineScope,
    private val onLog: (AuditLog) -> Unit
) {
    companion object {
        private const val TAG = "ScrcpyWSClient"
    }

    private var okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for websocket
        .writeTimeout(5, TimeUnit.SECONDS)
        .pingInterval(2, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _rttMs = MutableStateFlow(0L)
    val rttMs: StateFlow<Long> = _rttMs.asStateFlow()

    private val _incomingFrames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    val incomingFrames: SharedFlow<ByteArray> = _incomingFrames.asSharedFlow()

    private val _incomingTextMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingTextMessages: SharedFlow<String> = _incomingTextMessages.asSharedFlow()

    var crypto: AesGcmCrypto? = null

    fun connect(hostIp: String, port: Int = 8765, pairCode: String) {
        disconnect()
        _connectionState.value = ConnectionState.CONNECTING

        val wsUrl = "ws://$hostIp:$port/scrcpy-ws"
        onLog(AuditLog(
            level = LogLevel.INFO,
            tag = "CLIENT",
            message = "正在连接目标主机...",
            details = "地址: $wsUrl, 配对码: $pairCode"
        ))

        // Initialize AES-256 Crypto with the pairing code
        crypto = AesGcmCrypto(AesGcmCrypto.deriveKey(pairCode))

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
                onLog(AuditLog(
                    level = LogLevel.SUCCESS,
                    tag = "CLIENT",
                    message = "WebSocket 连接建立成功",
                    details = "端到端 AES-256-GCM 加密信道已激活"
                ))
                startPingLoop()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _incomingTextMessages.tryEmit(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val rawBytes = bytes.toByteArray()
                // Decrypt binary frame or encrypted message
                try {
                    val decrypted = crypto?.decrypt(rawBytes) ?: rawBytes
                    // Check if it's a JSON text or video frame (JPEG starts with 0xFF 0xD8 or video header)
                    if (decrypted.isNotEmpty() && (decrypted[0] == '{'.code.toByte())) {
                        val str = String(decrypted, StandardCharsets.UTF_8)
                        _incomingTextMessages.tryEmit(str)
                    } else {
                        _incomingFrames.tryEmit(decrypted)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Decryption error on client frame: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.ERROR
                onLog(AuditLog(
                    level = LogLevel.ERROR,
                    tag = "CLIENT",
                    message = "连接断开或错误: ${t.message}"
                ))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
                onLog(AuditLog(
                    level = LogLevel.WARNING,
                    tag = "CLIENT",
                    message = "与主机的连接已关闭 ($code: $reason)"
                ))
            }
        })
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                val pingTime = System.currentTimeMillis()
                // Send encrypted ping
                val pingPayload = """{"type":"ping","timestamp":$pingTime}"""
                sendEncrypted(pingPayload)
                delay(1500)
            }
        }
    }

    fun handlePong(serverTimestamp: Long, clientTimestamp: Long) {
        val now = System.currentTimeMillis()
        val rtt = (now - clientTimestamp).coerceAtLeast(1)
        _rttMs.value = rtt
    }

    fun sendEncrypted(jsonText: String) {
        val ws = webSocket ?: return
        try {
            val bytes = jsonText.toByteArray(StandardCharsets.UTF_8)
            val encrypted = crypto?.encrypt(bytes) ?: bytes
            ws.send(ByteString.of(*encrypted))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send encrypted message: ${e.message}")
        }
    }

    fun disconnect() {
        pingJob?.cancel()
        pingJob = null
        try {
            webSocket?.close(1000, "Client disconnect")
        } catch (e: Exception) {}
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
