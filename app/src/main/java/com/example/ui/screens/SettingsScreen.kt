package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ScrcpyRemoteViewModel

@Composable
fun SettingsScreen(
    viewModel: ScrcpyRemoteViewModel,
    modifier: Modifier = Modifier
) {
    val clientState by viewModel.clientState.collectAsState()
    var weakNetAggressiveMode by remember { mutableStateOf(true) }
    var autoLanDiscovery by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "系统与传输参数配置", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = "自适应码率、触控反馈与加密安全策略", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        // Weak Network Adaptation Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = CyberEmerald, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "弱网自适应码率策略", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "激进弱网降级机制", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "当 RTT 超过 150ms 或检测到丢包时，自动快速降至 360P/15FPS 保持极低延迟交互", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = weakNetAggressiveMode,
                        onCheckedChange = { weakNetAggressiveMode = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberEmerald, checkedTrackColor = CyberEmerald.copy(alpha = 0.3f))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "丢弃积压过期帧 (Drop Lagged Frames)", color = TextPrimary, fontSize = 13.sp)
                        Text(text = "网络队列阻塞时立即丢弃中间帧，避免堆积导致操作延迟越来越大", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = true,
                        onCheckedChange = {},
                        enabled = false,
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberEmerald, checkedTrackColor = CyberEmerald.copy(alpha = 0.3f))
                    )
                }
            }
        }

        // Touch Feedback & Visualization
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "触控与手势交互", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "显示触控波纹光标 (Touch Ripple)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "在控制端屏幕上即时高亮点击与手势落点", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = clientState.showTouchRipple,
                        onCheckedChange = { viewModel.toggleTouchRipple(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyberCyan.copy(alpha = 0.3f))
                    )
                }
            }
        }

        // Encryption Spec Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "端到端加密技术指标", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "加密套件: AES-256-GCM (NoPadding)", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Text(text = "密钥派生: PBKDF2WithHmacSHA256 (10,000 次迭代)", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Text(text = "随机向量: 96-bit 每次发包高熵随机生成 IV", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Text(text = "数据完整性: 128-bit GCM 认证标签校验防篡改", color = CyberEmerald, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
