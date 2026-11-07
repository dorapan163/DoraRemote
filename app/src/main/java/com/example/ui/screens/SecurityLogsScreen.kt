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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LogLevel
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
fun SecurityLogsScreen(
    viewModel: ScrcpyRemoteViewModel,
    modifier: Modifier = Modifier
) {
    val auditLogs by viewModel.auditLogs.collectAsState()
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }

    val filteredLogs = remember(auditLogs, selectedLevel) {
        if (selectedLevel == null) auditLogs else auditLogs.filter { it.level == selectedLevel }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("security_logs_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "安全与会话审计日志", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = "端到端 AES-256 加密与指令执行记录", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Button(
                onClick = { viewModel.clearLogs() },
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = TextSecondary),
                modifier = Modifier.testTag("clear_logs_button")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("清空", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Level Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedLevel == null,
                onClick = { selectedLevel = null },
                label = { Text("全部", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                    selectedLabelColor = CyberCyan,
                    containerColor = DarkSurface,
                    labelColor = TextSecondary
                )
            )

            FilterChip(
                selected = selectedLevel == LogLevel.SECURITY,
                onClick = { selectedLevel = if (selectedLevel == LogLevel.SECURITY) null else LogLevel.SECURITY },
                label = { Text("安全鉴权", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                    selectedLabelColor = CyberCyan,
                    containerColor = DarkSurface,
                    labelColor = TextSecondary
                )
            )

            FilterChip(
                selected = selectedLevel == LogLevel.SUCCESS,
                onClick = { selectedLevel = if (selectedLevel == LogLevel.SUCCESS) null else LogLevel.SUCCESS },
                label = { Text("成功", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyberEmerald.copy(alpha = 0.2f),
                    selectedLabelColor = CyberEmerald,
                    containerColor = DarkSurface,
                    labelColor = TextSecondary
                )
            )

            FilterChip(
                selected = selectedLevel == LogLevel.ERROR,
                onClick = { selectedLevel = if (selectedLevel == LogLevel.ERROR) null else LogLevel.ERROR },
                label = { Text("异常", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyberRed.copy(alpha = 0.2f),
                    selectedLabelColor = CyberRed,
                    containerColor = DarkSurface,
                    labelColor = TextSecondary
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无审计日志记录\n启动连接或传输后将实时记录安全事件",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    val badgeColor = when (log.level) {
                        LogLevel.SECURITY -> CyberCyan
                        LogLevel.SUCCESS -> CyberEmerald
                        LogLevel.WARNING -> Color(0xFFF59E0B)
                        LogLevel.ERROR -> CyberRed
                        LogLevel.INFO -> Color(0xFF3B82F6)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
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
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(badgeColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = log.tag,
                                            color = badgeColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = log.message,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Text(
                                    text = log.formattedTime,
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (log.details.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = log.details,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
