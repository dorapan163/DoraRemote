package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.ConnectionState
import com.example.ui.components.FloatingControlBar
import com.example.ui.components.QualitySettingsSheet
import com.example.ui.components.RemoteControlCanvas
import com.example.ui.components.TelemetryHud
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ScrcpyRemoteViewModel

@Composable
fun ControllerScreen(
    viewModel: ScrcpyRemoteViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clientState by viewModel.clientState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val discoveredHosts by viewModel.discoveredHosts.collectAsState()
    val activePreset by viewModel.selectedPreset.collectAsState()

    var inputPairCode by remember { mutableStateOf("") }
    var inputIp by remember { mutableStateOf("127.0.0.1") }
    var inputPort by remember { mutableStateOf("8765") }

    var showQualitySheet by remember { mutableStateOf(false) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var showQrInputDialog by remember { mutableStateOf(false) }
    var typedText by remember { mutableStateOf("") }
    var qrContentInput by remember { mutableStateOf("") }

    if (clientState.connectionState == ConnectionState.CONNECTED) {
        // Connected Remote Session Canvas View
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            RemoteControlCanvas(
                bitmap = clientState.latestFrame,
                showRipple = clientState.showTouchRipple,
                onTap = { x, y -> viewModel.sendGesture("TAP", x, y) },
                onDoubleTap = { x, y -> viewModel.sendGesture("DOUBLE_TAP", x, y) },
                onLongPress = { x, y -> viewModel.sendGesture("LONG_PRESS", x, y, durationMs = 600) },
                onSwipe = { sx, sy, ex, ey, dur -> viewModel.sendGesture("SWIPE", sx, sy, ex, ey, durationMs = dur) },
                onPinch = { cx, cy, zoom -> viewModel.sendGesture("PINCH", cx, cy, scale = zoom) },
                onTouchDown = { id, x, y -> viewModel.sendTouchEvent("DOWN", id, x, y) },
                onTouchMove = { id, x, y -> viewModel.sendTouchEvent("MOVE", id, x, y) },
                onTouchUp = { id, x, y -> viewModel.sendTouchEvent("UP", id, x, y) }
            )

            // Top Telemetry HUD
            TelemetryHud(
                telemetry = telemetry,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )

            // Bottom Navigation & System Action Bar
            FloatingControlBar(
                isFullscreen = clientState.isFullscreen,
                onBackClick = { viewModel.sendKey("BACK") },
                onHomeClick = { viewModel.sendKey("HOME") },
                onRecentsClick = { viewModel.sendKey("RECENTS") },
                onKeyboardClick = { showTextInputDialog = true },
                onLockClick = { viewModel.sendKey("POWER") },
                onQualityClick = { showQualitySheet = true },
                onToggleFullscreen = { viewModel.toggleFullscreen(!clientState.isFullscreen) },
                onDisconnect = { viewModel.disconnectController() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }

        if (showQualitySheet) {
            QualitySettingsSheet(
                activePreset = activePreset,
                onPresetSelect = { viewModel.setQualityPreset(it) },
                onDismiss = { showQualitySheet = false }
            )
        }

        if (showTextInputDialog) {
            AlertDialog(
                onDismissRequest = { showTextInputDialog = false },
                containerColor = DarkSurface,
                title = { Text("远程文字输入", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("输入需要同步发送到被控端设备的文本内容：", color = TextSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = typedText,
                            onValueChange = { typedText = it },
                            placeholder = { Text("输入内容...", color = TextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = DarkSurfaceBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("remote_text_field")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (typedText.isNotEmpty()) {
                                viewModel.sendText(typedText)
                                typedText = ""
                                showTextInputDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = DarkSurface),
                        modifier = Modifier.testTag("send_remote_text_button")
                    ) {
                        Text("发送到设备", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTextInputDialog = false }) {
                        Text("取消", color = TextSecondary)
                    }
                }
            )
        }

    } else {
        // Disconnected Connection Management View
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Cyber Header Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkSurface)
                        .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(CyberCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CastConnected,
                                        contentDescription = "Scrcpy",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Scrcpy 控制终端",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = "WebSocket 低延迟传输 · AES-256 E2EE",
                                        color = CyberEmerald,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceElevated)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "AES-256",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("GCM 加密", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Connection in progress indicator
            if (clientState.connectionState == ConnectionState.CONNECTING) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, CyberCyan, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = CyberCyan,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "正在连接并执行 AES-256 握手协商...",
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Quick 6-Digit PIN Pairing Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "1. 配对码安全互联",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "输入被控端屏幕显示的 6 位数字密钥快速配对：",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputPairCode,
                        onValueChange = { if (it.length <= 6) inputPairCode = it },
                        placeholder = { Text("例如 849203", color = TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CyberCyan,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = DarkSurfaceBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pair_code_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (inputPairCode.length == 6) {
                                    // Try connecting to discovered host with matching pair code or local IP
                                    val match = discoveredHosts.values.find { it.pairCode == inputPairCode }
                                    val targetIp = match?.ip ?: inputIp
                                    val targetPort = match?.port ?: (inputPort.toIntOrNull() ?: 8765)
                                    viewModel.connectAsController(targetIp, targetPort, inputPairCode)
                                } else {
                                    Toast.makeText(context, "请输入完整的 6 位配对码", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan,
                                contentColor = DarkSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("connect_by_code_button")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("配对并连接", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showQrInputDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkSurfaceElevated,
                                contentColor = CyberCyan
                            ),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("scan_qr_button")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("扫码配对")
                        }
                    }
                }
            }

            // LAN Discovered Devices Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = CyberEmerald, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "局域网广播设备 (${discoveredHosts.size})",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "自动发现中...",
                            color = CyberEmerald,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (discoveredHosts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "同一 Wi-Fi 下尚未发现被控端广播，请先在被控端开启服务",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        discoveredHosts.values.forEach { host ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(1.dp, CyberEmerald.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Devices, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(text = host.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(text = "${host.ip}:${host.port}", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }

                                Button(
                                    onClick = {
                                        inputIp = host.ip
                                        inputPort = host.port.toString()
                                        inputPairCode = host.pairCode
                                        viewModel.connectAsController(host.ip, host.port, host.pairCode)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = DarkSurface),
                                    modifier = Modifier.testTag("connect_discovered_${host.ip}")
                                ) {
                                    Text("一键直连", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Direct IP & Port Input Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "2. 手动指定 IP 与端口",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "适用于跨网段、内网穿透或远程端口映射连接",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = inputIp,
                            onValueChange = { inputIp = it },
                            label = { Text("主机 IP 地址") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = DarkSurfaceBorder
                            ),
                            modifier = Modifier
                                .weight(2f)
                                .testTag("direct_ip_input")
                        )

                        OutlinedTextField(
                            value = inputPort,
                            onValueChange = { inputPort = it },
                            label = { Text("端口") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = DarkSurfaceBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("direct_port_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val port = inputPort.toIntOrNull() ?: 8765
                            val code = if (inputPairCode.isNotEmpty()) inputPairCode else "849203"
                            viewModel.connectAsController(inputIp, port, code)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurfaceElevated,
                            contentColor = CyberCyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("manual_ip_connect_button")
                    ) {
                        Icon(Icons.Default.SettingsEthernet, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("通过 IP 发起连接", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    if (showQrInputDialog) {
        AlertDialog(
            onDismissRequest = { showQrInputDialog = false },
            containerColor = DarkSurface,
            title = { Text("扫码与配对信息解析", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("可将摄像头扫描到的二维码文本或链接粘贴在此处快速解析：", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = qrContentInput,
                        onValueChange = { qrContentInput = it },
                        placeholder = { Text("例如 {\"ip\":\"192.168.1.10\",\"port\":8765,\"code\":\"849203\"}", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = DarkSurfaceBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qr_raw_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            if (qrContentInput.contains("{")) {
                                val json = org.json.JSONObject(qrContentInput)
                                val ip = json.optString("ip", "127.0.0.1")
                                val port = json.optInt("port", 8765)
                                val code = json.optString("code", "849203")
                                viewModel.connectAsController(ip, port, code)
                            } else {
                                val parts = qrContentInput.split(":")
                                if (parts.size >= 2) {
                                    viewModel.connectAsController(parts[0], parts[1].toIntOrNull() ?: 8765, inputPairCode)
                                }
                            }
                            showQrInputDialog = false
                        } catch (e: Exception) {
                            Toast.makeText(context, "二维码内容解析失败", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = DarkSurface)
                ) {
                    Text("立即解析连接", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrInputDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }
}
