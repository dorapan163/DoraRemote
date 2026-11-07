package com.example.network

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    /**
     * Finds the local IPv4 address (preferring Wi-Fi wlan0, ap0, or eth0).
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            var candidateIp = "127.0.0.1"

            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val hostAddress = address.hostAddress ?: continue
                        val name = networkInterface.name.lowercase()
                        if (name.contains("wlan") || name.contains("ap") || name.contains("eth")) {
                            return hostAddress
                        }
                        candidateIp = hostAddress
                    }
                }
            }
            return candidateIp
        } catch (e: Exception) {
            return "127.0.0.1"
        }
    }
}
