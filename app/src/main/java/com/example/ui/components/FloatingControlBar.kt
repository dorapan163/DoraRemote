package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberRed
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextPrimary

/**
 * Floating Quick Navigation & System Control Bar for Controller screen.
 */
@Composable
fun FloatingControlBar(
    isFullscreen: Boolean,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onRecentsClick: () -> Unit,
    onKeyboardClick: () -> Unit,
    onLockClick: () -> Unit,
    onQualityClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurface.copy(alpha = 0.92f))
                .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("floating_control_bar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ControlBarButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDesc = "返回键 (Back)",
                testTag = "key_back_button",
                onClick = onBackClick
            )

            ControlBarButton(
                icon = Icons.Default.Home,
                contentDesc = "主屏幕 (Home)",
                testTag = "key_home_button",
                onClick = onHomeClick
            )

            ControlBarButton(
                icon = Icons.Default.ViewCarousel,
                contentDesc = "最近任务 (Recents)",
                testTag = "key_recents_button",
                onClick = onRecentsClick
            )

            ControlBarButton(
                icon = Icons.Default.Keyboard,
                contentDesc = "虚拟键盘输入",
                testTag = "keyboard_input_button",
                onClick = onKeyboardClick
            )

            ControlBarButton(
                icon = Icons.Default.Tune,
                contentDesc = "画质与码率",
                testTag = "quality_settings_button",
                onClick = onQualityClick
            )

            ControlBarButton(
                icon = Icons.Default.Lock,
                contentDesc = "锁屏 (Power)",
                testTag = "key_power_button",
                onClick = onLockClick
            )

            ControlBarButton(
                icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDesc = "全屏切换",
                testTag = "fullscreen_toggle_button",
                onClick = onToggleFullscreen
            )

            ControlBarButton(
                icon = Icons.Default.Close,
                contentDesc = "断开连接",
                testTag = "disconnect_session_button",
                tint = CyberRed,
                onClick = onDisconnect
            )
        }
    }
}

@Composable
private fun ControlBarButton(
    icon: ImageVector,
    contentDesc: String,
    testTag: String,
    tint: Color = TextPrimary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
