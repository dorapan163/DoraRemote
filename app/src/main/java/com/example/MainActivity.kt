package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.network.ConnectionState
import com.example.ui.screens.ControllerScreen
import com.example.ui.screens.HostScreen
import com.example.ui.screens.SecurityLogsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.AppRole
import com.example.viewmodel.ScrcpyRemoteViewModel

enum class MainTab {
    CONTROLLER, HOST, LOGS, SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: ScrcpyRemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: ScrcpyRemoteViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(MainTab.CONTROLLER) }
    val clientState by viewModel.clientState.collectAsState()

    val isConnected = clientState.connectionState == ConnectionState.CONNECTED
    val hideNavigation = isConnected && clientState.isFullscreen

    // Launcher for real Android MediaProjection screen capture
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(result.resultCode, result.data!!)
            viewModel.startHost(mediaProjection = projection, isSimulated = false)
        } else {
            Toast.makeText(context, "屏幕录制权限未授予，已切换为高清交互测试流", Toast.LENGTH_SHORT).show()
            viewModel.startHost(mediaProjection = null, isSimulated = true)
        }
    }

    BackHandler(enabled = true) {
        if (isConnected) {
            if (clientState.isFullscreen) {
                viewModel.toggleFullscreen(false)
            } else {
                viewModel.disconnectController()
            }
        } else if (currentTab != MainTab.CONTROLLER) {
            currentTab = MainTab.CONTROLLER
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(visible = !hideNavigation) {
                NavigationBar(
                    containerColor = DarkSurface,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == MainTab.CONTROLLER,
                        onClick = {
                            currentTab = MainTab.CONTROLLER
                            viewModel.setRole(AppRole.CONTROLLER)
                        },
                        icon = {
                            Icon(Icons.Default.CastConnected, contentDescription = stringResource(R.string.role_controller))
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.role_controller),
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == MainTab.CONTROLLER) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberCyan,
                            selectedTextColor = CyberCyan,
                            indicatorColor = DarkSurfaceElevated,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_controller_tab")
                    )

                    NavigationBarItem(
                        selected = currentTab == MainTab.HOST,
                        onClick = {
                            currentTab = MainTab.HOST
                            viewModel.setRole(AppRole.HOST)
                        },
                        icon = {
                            Icon(Icons.Default.Devices, contentDescription = stringResource(R.string.role_host))
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.role_host),
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == MainTab.HOST) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberCyan,
                            selectedTextColor = CyberCyan,
                            indicatorColor = DarkSurfaceElevated,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_host_tab")
                    )

                    NavigationBarItem(
                        selected = currentTab == MainTab.LOGS,
                        onClick = { currentTab = MainTab.LOGS },
                        icon = {
                            Icon(Icons.Default.Security, contentDescription = stringResource(R.string.tab_logs))
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.tab_logs),
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == MainTab.LOGS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberCyan,
                            selectedTextColor = CyberCyan,
                            indicatorColor = DarkSurfaceElevated,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_logs_tab")
                    )

                    NavigationBarItem(
                        selected = currentTab == MainTab.SETTINGS,
                        onClick = { currentTab = MainTab.SETTINGS },
                        icon = {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.tab_settings))
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.tab_settings),
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == MainTab.SETTINGS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberCyan,
                            selectedTextColor = CyberCyan,
                            indicatorColor = DarkSurfaceElevated,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_settings_tab")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(
                    if (!isConnected) Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    else Modifier
                )
        ) {
            when (currentTab) {
                MainTab.CONTROLLER -> {
                    ControllerScreen(viewModel = viewModel)
                }
                MainTab.HOST -> {
                    HostScreen(
                        viewModel = viewModel,
                        onRequestMediaProjection = {
                            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
                        }
                    )
                }
                MainTab.LOGS -> {
                    SecurityLogsScreen(viewModel = viewModel)
                }
                MainTab.SETTINGS -> {
                    SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
