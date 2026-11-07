package com.example.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets

data class DiscoveredHost(
    val name: String,
    val ip: String,
    val port: Int,
    val pairCode: String,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * UDP Multicast/Broadcast service for instant LAN auto-discovery.
 */
class LanDiscovery(
    private val scope: CoroutineScope
) {
    companion object {
        const val DISCOVERY_PORT = 8766
        private const val TAG = "LanDiscovery"
        private const val MAGIC_HEADER = "SCRCPY_REMOTE_DISCOVER_V1"
    }

    private var broadcastJob: Job? = null
    private var listenerJob: Job? = null

    private val _discoveredHosts = MutableStateFlow<Map<String, DiscoveredHost>>(emptyMap())
    val discoveredHosts: StateFlow<Map<String, DiscoveredHost>> = _discoveredHosts.asStateFlow()

    fun startBroadcasting(hostName: String, port: Int, pairCode: String) {
        stopBroadcasting()
        broadcastJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true

                while (isActive) {
                    try {
                        val payload = JSONObject().apply {
                            put("magic", MAGIC_HEADER)
                            put("name", hostName)
                            put("port", port)
                            put("pairCode", pairCode)
                        }.toString().toByteArray(StandardCharsets.UTF_8)

                        val broadcastAddr = InetAddress.getByName("255.255.255.255")
                        val packet = DatagramPacket(payload, payload.size, broadcastAddr, DISCOVERY_PORT)
                        socket.send(packet)
                    } catch (e: Exception) {
                        Log.d(TAG, "Broadcast error: ${e.message}")
                    }
                    delay(2000)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create broadcast socket: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    fun stopBroadcasting() {
        broadcastJob?.cancel()
        broadcastJob = null
    }

    fun startListening() {
        stopListening()
        listenerJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(DISCOVERY_PORT)
                socket.broadcast = true
                val buffer = ByteArray(2048)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val jsonStr = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                    try {
                        val json = JSONObject(jsonStr)
                        if (json.optString("magic") == MAGIC_HEADER) {
                            val hostIp = packet.address.hostAddress ?: ""
                            val name = json.optString("name", "Unknown Host")
                            val port = json.optInt("port", 8765)
                            val pairCode = json.optString("pairCode", "")

                            val host = DiscoveredHost(
                                name = name,
                                ip = hostIp,
                                port = port,
                                pairCode = pairCode,
                                lastSeen = System.currentTimeMillis()
                            )

                            val current = _discoveredHosts.value.toMutableMap()
                            current[hostIp] = host

                            // Clean old hosts older than 10 seconds
                            val now = System.currentTimeMillis()
                            val filtered = current.filterValues { now - it.lastSeen < 10000 }
                            _discoveredHosts.value = filtered
                        }
                    } catch (e: Exception) {
                        // ignore malformed packets
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Listener stopped or socket error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    fun stopListening() {
        listenerJob?.cancel()
        listenerJob = null
    }
}
