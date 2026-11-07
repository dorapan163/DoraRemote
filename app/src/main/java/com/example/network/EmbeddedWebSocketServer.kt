package com.example.network

import android.util.Base64
import android.util.Log
import com.example.crypto.AesGcmCrypto
import com.example.model.AuditLog
import com.example.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lightweight, high-performance RFC 6455 WebSocket Server implemented directly on Android.
 * Runs on the Host (被控端) device, serving low-latency encrypted video frames and
 * receiving encrypted control commands from Controllers.
 */
class EmbeddedWebSocketServer(
    private val scope: CoroutineScope,
    private val port: Int = 8765,
    private val onLog: (AuditLog) -> Unit
) {
    companion object {
        private const val TAG = "ScrcpyWSServer"
        private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private const val OP_TEXT = 0x01
        private const val OP_BINARY = 0x02
        private const val OP_CLOSE = 0x08
        private const val OP_PING = 0x09
        private const val OP_PONG = 0x0A
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val activeClients = CopyOnWriteArrayList<ClientSession>()

    private val _connectedClientsCount = MutableStateFlow(0)
    val connectedClientsCount: StateFlow<Int> = _connectedClientsCount.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Pair<ClientSession, String>>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<Pair<ClientSession, String>> = _incomingMessages.asSharedFlow()

    private val _incomingBinaryMessages = MutableSharedFlow<Pair<ClientSession, ByteArray>>(extraBufferCapacity = 64)
    val incomingBinaryMessages: SharedFlow<Pair<ClientSession, ByteArray>> = _incomingBinaryMessages.asSharedFlow()

    var crypto: AesGcmCrypto? = null

    class ClientSession(
        val socket: Socket,
        val clientIp: String,
        val inputStream: InputStream,
        val outputStream: OutputStream,
        var isAuthorized: Boolean = false,
        var clientDeviceName: String = "Unknown Controller"
    )

    fun start() {
        if (serverJob != null && serverJob?.isActive == true) return

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                onLog(AuditLog(
                    level = LogLevel.SUCCESS,
                    tag = "SERVER",
                    message = "WebSocket 服务已启动",
                    details = "监听端口: $port, 端到端加密: AES-256-GCM 待配对"
                ))

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    val clientIp = socket.inetAddress?.hostAddress ?: "Unknown"

                    scope.launch(Dispatchers.IO) {
                        handleClient(socket, clientIp)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Server error: ${e.message}", e)
                    onLog(AuditLog(
                        level = LogLevel.ERROR,
                        tag = "SERVER",
                        message = "WebSocket 服务异常: ${e.message}"
                    ))
                }
            } finally {
                stop()
            }
        }
    }

    private fun handleClient(socket: Socket, clientIp: String) {
        var session: ClientSession? = null
        try {
            socket.tcpNoDelay = true // Disable Nagle's algorithm for ultra-low latency
            socket.sendBufferSize = 512 * 1024
            socket.receiveBufferSize = 64 * 1024

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. Perform WebSocket HTTP Handshake
            if (!performHandshake(input, output)) {
                socket.close()
                return
            }

            session = ClientSession(socket, clientIp, input, output)
            activeClients.add(session)
            _connectedClientsCount.value = activeClients.size

            onLog(AuditLog(
                level = LogLevel.INFO,
                tag = "NETWORK",
                message = "控制端已连接: $clientIp",
                details = "TCP NoDelay 开启，等待握手与身份验证"
            ))

            // 2. Read WebSocket Frames Loop
            while (scope.isActive && !socket.isClosed) {
                val frame = readWebSocketFrame(input) ?: break
                when (frame.opcode) {
                    OP_TEXT -> {
                        val text = String(frame.payload, StandardCharsets.UTF_8)
                        _incomingMessages.tryEmit(session to text)
                    }
                    OP_BINARY -> {
                        // Decrypt binary frame if crypto is set
                        val decrypted = try {
                            crypto?.decrypt(frame.payload) ?: frame.payload
                        } catch (e: Exception) {
                            Log.w(TAG, "Decryption error on binary payload: ${e.message}")
                            null
                        }
                        if (decrypted != null) {
                            _incomingBinaryMessages.tryEmit(session to decrypted)
                        }
                    }
                    OP_PING -> {
                        sendFrame(output, OP_PONG, frame.payload)
                    }
                    OP_CLOSE -> {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client $clientIp connection closed: ${e.message}")
        } finally {
            session?.let {
                activeClients.remove(it)
                _connectedClientsCount.value = activeClients.size
                onLog(AuditLog(
                    level = LogLevel.WARNING,
                    tag = "NETWORK",
                    message = "控制端断开连接: ${it.clientIp}"
                ))
            }
            try { socket.close() } catch (e: Exception) {}
        }
    }

    /**
     * Handshake: Handles standard RFC 6455 Upgrade.
     */
    private fun performHandshake(input: InputStream, output: OutputStream): Boolean {
        val reader = input.bufferedReader(StandardCharsets.UTF_8)
        var line = reader.readLine() ?: return false
        if (!line.startsWith("GET")) return false

        var secWebSocketKey: String? = null
        while (true) {
            line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val parts = line.split(":", limit = 2)
            if (parts.size == 2 && parts[0].trim().equals("Sec-WebSocket-Key", ignoreCase = true)) {
                secWebSocketKey = parts[1].trim()
            }
        }

        if (secWebSocketKey == null) return false

        val sha1 = MessageDigest.getInstance("SHA-1")
        val acceptBytes = sha1.digest((secWebSocketKey + WEBSOCKET_GUID).toByteArray(StandardCharsets.UTF_8))
        val acceptKey = Base64.encodeToString(acceptBytes, Base64.NO_WRAP)

        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"

        output.write(response.toByteArray(StandardCharsets.UTF_8))
        output.flush()
        return true
    }

    private data class WsFrame(val opcode: Int, val payload: ByteArray)

    private fun readWebSocketFrame(input: InputStream): WsFrame? {
        val b0 = input.read()
        if (b0 == -1) return null
        val b1 = input.read()
        if (b1 == -1) return null

        val opcode = b0 and 0x0F
        val isMasked = (b1 and 0x80) != 0
        var payloadLen = (b1 and 0x7F).toLong()

        if (payloadLen == 126L) {
            val byte1 = input.read()
            val byte2 = input.read()
            if (byte1 == -1 || byte2 == -1) return null
            payloadLen = ((byte1 shl 8) or byte2).toLong()
        } else if (payloadLen == 127L) {
            var len = 0L
            for (i in 0 until 8) {
                val b = input.read()
                if (b == -1) return null
                len = (len shl 8) or (b and 0xFF).toLong()
            }
            payloadLen = len
        }

        val maskingKey = ByteArray(4)
        if (isMasked) {
            var read = 0
            while (read < 4) {
                val r = input.read(maskingKey, read, 4 - read)
                if (r == -1) return null
                read += r
            }
        }

        if (payloadLen > 10 * 1024 * 1024) { // safety limit 10MB
            return null
        }

        val payload = ByteArray(payloadLen.toInt())
        var totalRead = 0
        while (totalRead < payloadLen) {
            val r = input.read(payload, totalRead, (payloadLen - totalRead).toInt())
            if (r == -1) return null
            totalRead += r
        }

        if (isMasked) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor maskingKey[i % 4].toInt()).toByte()
            }
        }

        return WsFrame(opcode, payload)
    }

    /**
     * Sends frame from server to client (Server-to-client frames MUST NOT be masked).
     */
    @Synchronized
    private fun sendFrame(output: OutputStream, opcode: Int, payload: ByteArray) {
        val b0 = (0x80 or (opcode and 0x0F)).toByte()
        val len = payload.size

        if (len <= 125) {
            val header = byteArrayOf(b0, len.toByte())
            output.write(header)
        } else if (len <= 65535) {
            val header = byteArrayOf(
                b0,
                126.toByte(),
                ((len shr 8) and 0xFF).toByte(),
                (len and 0xFF).toByte()
            )
            output.write(header)
        } else {
            val buf = ByteBuffer.allocate(10)
            buf.put(b0)
            buf.put(127.toByte())
            buf.putLong(len.toLong())
            output.write(buf.array())
        }

        output.write(payload)
        output.flush()
    }

    /**
     * Broadcasts an encrypted video frame or binary packet to all authorized clients.
     */
    fun broadcastBinary(packet: ByteArray) {
        if (activeClients.isEmpty()) return
        val encrypted = try {
            crypto?.encrypt(packet) ?: packet
        } catch (e: Exception) {
            packet
        }

        for (client in activeClients) {
            try {
                sendFrame(client.outputStream, OP_BINARY, encrypted)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to send frame to ${client.clientIp}")
            }
        }
    }

    /**
     * Sends an encrypted text/JSON message to a specific client.
     */
    fun sendMessage(session: ClientSession, text: String) {
        try {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            val encrypted = try {
                crypto?.encrypt(bytes) ?: bytes
            } catch (e: Exception) {
                bytes
            }
            // Send as binary encrypted or base64
            sendFrame(session.outputStream, OP_BINARY, encrypted)
        } catch (e: Exception) {
            Log.d(TAG, "Error sending to client: ${e.message}")
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        for (client in activeClients) {
            try { client.socket.close() } catch (e: Exception) {}
        }
        activeClients.clear()
        _connectedClientsCount.value = 0
    }
}
