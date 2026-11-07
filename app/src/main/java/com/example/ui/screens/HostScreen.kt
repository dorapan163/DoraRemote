package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.RemoteAccessibilityService
import com.example.ui.components.QrCodeView
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ScrcpyRemoteViewModel
import org.json.JSONObject

@Composable
fun HostScreen(
    viewModel: ScrcpyRemoteViewModel,
    onRequestMediaProjection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hostState by viewModel.hostState.collectAsState()
    val isAccessibilityReady by RemoteAccessibilityService.isServiceConnected.collectAsState()

    val qrContent = remember(hostState.localIp, hostState.port, hostState.pairCode) {
        JSONObject().apply {
            put("ip", hostState.localIp)
            put("port", hostState.port)
            put("code", hostState.pairCode)
            put("device", android.os.Build.MODEL ?: "Android Host")
        }.toString()
    }

    // Pending incoming connection approval dialog
    hostState.pendingApprovalClient?.let { (session, request) ->
        AlertDialog(
            onDismissRequest = { /* force action */ },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CyberCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("控制端接入请求", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("检测到外部设备正在请求建立控制连接：", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceElevated)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("设备名称: ${request.deviceName}", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("来源 IP: ${session.clientIp}", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            Text("协议状态: 端到端 AES-256 握手成功", color = CyberEmerald, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.approvePendingClient(session, request) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = DarkSurface),
                    modifier = Modifier.testTag("approve_client_button")
                ) {
                    Text("同意接入", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.rejectPendingClient(session) },
                    modifier = Modifier.testTag("reject_client_button")
                ) {
                    Text("拒绝", color = CyberRed)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Host Status & Control Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurface)
                    .border(
                        1.dp,
                        if (hostState.isRunning) CyberEmerald else DarkSurfaceBorder,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(18.dp)
                    .testTag("host_status_card")
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (hostState.isRunning) StatusOnline else TextMuted)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hostState.isRunning) "被控端服务: 运行中" else "被控端服务: 已停止",
                                color = if (hostState.isRunning) StatusOnline else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        if (hostState.isRunning) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyberEmerald.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${hostState.clientCount} 个控制端在线",
                                    color = CyberEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "本机服务地址: ${hostState.localIp}:${hostState.port}",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!hostState.isRunning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onRequestMediaProjection,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberEmerald,
                                    contentColor = DarkSurface
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("start_real_projection_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("启动真机投屏", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.startHost(isSimulated = true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkSurfaceElevated,
                                    contentColor = CyberCyan
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("start_simulated_projection_button")
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("启动交互测试")
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.stopHost() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("stop_host_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("停止服务与屏幕共享", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 6-Digit Pairing PIN Display Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("pairing_code_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "安全配对码 (AES-256 密钥源)",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新生成",
                            tint = CyberCyan,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.regeneratePairCode() }
                                .padding(4.dp)
                                .testTag("regenerate_code_button")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制配对码",
                            tint = CyberCyan,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("PairCode", hostState.pairCode))
                                    Toast.makeText(context, "配对码已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                }
                                .padding(4.dp)
                                .testTag("copy_code_button")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${hostState.pairCode.take(3)}  ${hostState.pairCode.takeLast(3)}",
                        color = CyberCyan,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "控制端只需输入上述 6 位数字即可自动派生 256 位 AES 密钥，保护屏幕流与指令安全。",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // QR Code Display Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "扫码快速连接",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Text(
                    text = "控制端使用相机扫码即可秒连，无需手动输入 IP 与配对码",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(14.dp))

                QrCodeView(content = qrContent, size = 190.dp)

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Accessibility Service Status
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurface)
                    .border(
                        1.dp,
                        if (isAccessibilityReady) CyberEmerald else DarkSurfaceBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Accessibility,
                        contentDescription = null,
                        tint = if (isAccessibilityReady) CyberEmerald else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isAccessibilityReady) "手势无障碍服务已激活" else "手势无障碍服务未启用",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isAccessibilityReady) "支持多点触控、滑动及系统键模拟" else "点击前往系统设置开启无障碍权限",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                if (!isAccessibilityReady) {
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = CyberCyan),
                        modifier = Modifier.testTag("open_accessibility_settings")
                    ) {
                        Text("去开启", fontSize = 12.sp)
                    }
                } else {
                    Icon(Icons.Default.Check, contentDescription = "Active", tint = CyberEmerald, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Fine-grained Permission Management Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("permissions_management_card")
            ) {
                Text(
                    text = "精细权限安全控制",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "实时控制外部控制端允许执行的操作范围",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionToggleRow(
                    icon = Icons.Default.TouchApp,
                    title = "允许触控与多点手势",
                    subtitle = "允许控制端点击、长按、滑动和双指缩放",
                    checked = hostState.permissions.allowTouch,
                    onCheckedChange = {
                        viewModel.updatePermissions(hostState.permissions.copy(allowTouch = it))
                    }
                )

                PermissionToggleRow(
                    icon = Icons.Default.Key,
                    title = "允许系统导航按键",
                    subtitle = "返回键、Home键、最近任务、锁屏等全局控制",
                    checked = hostState.permissions.allowKeys,
                    onCheckedChange = {
                        viewModel.updatePermissions(hostState.permissions.copy(allowKeys = it))
                    }
                )

                PermissionToggleRow(
                    icon = Icons.Default.Keyboard,
                    title = "允许远程键盘输入",
                    subtitle = "允许控制端向当前输入框打字注入文本",
                    checked = hostState.permissions.allowKeyboard,
                    onCheckedChange = {
                        viewModel.updatePermissions(hostState.permissions.copy(allowKeyboard = it))
                    }
                )

                PermissionToggleRow(
                    icon = Icons.Default.ContentCopy,
                    title = "允许剪贴板双向同步",
                    subtitle = "自动同步双方设备剪贴板文本",
                    checked = hostState.permissions.allowClipboard,
                    onCheckedChange = {
                        viewModel.updatePermissions(hostState.permissions.copy(allowClipboard = it))
                    }
                )

                PermissionToggleRow(
                    icon = Icons.Default.Security,
                    title = "连接接入需弹窗确认",
                    subtitle = "控制端接入时需在被控端点击同意方可连接",
                    checked = hostState.permissions.requireHostApproval,
                    onCheckedChange = {
                        viewModel.updatePermissions(hostState.permissions.copy(requireHostApproval = it))
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun PermissionToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) CyberCyan else TextMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = TextSecondary, fontSize = 11.sp)
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberCyan,
                checkedTrackColor = CyberCyan.copy(alpha = 0.3f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = DarkSurfaceElevated
            )
        )
    }
}
