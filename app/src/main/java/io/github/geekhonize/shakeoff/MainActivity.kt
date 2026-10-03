package io.github.geekhonize.shakeoff

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.geekhonize.shakeoff.monitor.AdwareMonitorReceiver
import io.github.geekhonize.shakeoff.monitor.EventLog
import io.github.geekhonize.shakeoff.strategy.ControlMode
import io.github.geekhonize.shakeoff.ui.MainViewModel
import io.github.geekhonize.shakeoff.ui.screens.AboutScreen
import io.github.geekhonize.shakeoff.ui.screens.EventLogScreen
import io.github.geekhonize.shakeoff.ui.screens.HomeScreen
import io.github.geekhonize.shakeoff.ui.screens.SettingsScreen
import io.github.geekhonize.shakeoff.ui.theme.ShakeOffTheme
import io.github.geekhonize.shakeoff.util.ShizukuManager

/**
 * 单 Activity 入口。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // UserService 绑定需要应用上下文
        ShizukuManager.init(applicationContext)

        // 注册广告安装监控（动态注册，无需 Manifest 声明）
        AdwareMonitorReceiver.register(applicationContext)

        setContent {
            ShakeOffTheme {
                ShakeOffApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ShizukuManager.registerPermissionListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuManager.unregisterPermissionListener()
    }
}

/**
 * 页面状态。
 */
private enum class Screen {
    HOME, SETTINGS, ABOUT, EVENT_LOG
}

/**
 * 应用根 Composable：负责导航与各类回调。
 */
@Composable
private fun ShakeOffApp() {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    var screen by remember { mutableStateOf(Screen.HOME) }

    // 拦截记录列表，读取后自动刷新
    var eventEntries by remember { mutableStateOf(EventLog.getInstance().getAll()) }

    // Shizuku 授权结果回调
    LaunchedEffect(Unit) {
        ShizukuManager.onPermissionResult = { granted ->
            if (granted) {
                viewModel.refresh()
            } else {
                Toast.makeText(context, "Shizuku 授权被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 一次性消息转 Toast
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeMessage()
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onToggleSystemApps = viewModel::onToggleSystemApps,
            onRefresh = viewModel::refresh,
            onToggleSensor = { app, block ->
                viewModel.onToggleSensor(app.packageName, block)
                eventEntries = EventLog.getInstance().getAll()

                // 非 Shizuku 且非 Device Owner 模式时，提示用户手动操作
                if (state.activeMode == ControlMode.ACCESSIBILITY) {
                    ShizukuManager.openAppDetails(context, app.packageName)
                    Toast.makeText(
                        context,
                        "无障碍模式无法关闭权限，已跳转到系统设置",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onOpenSettings = { screen = Screen.SETTINGS },
            onRequestShizuku = viewModel::requestShizukuPermission
        )

        Screen.SETTINGS -> SettingsScreen(
            activeMode = state.activeMode,
            requestedMode = state.requestedMode,
            availability = state.availability,
            monitorEnabled = state.monitorEnabled,
            autoBlockEnabled = state.autoBlockEnabled,
            daemonEnabled = state.daemonEnabled,
            daemonRunning = state.daemonRunning,
            onBack = { screen = Screen.HOME },
            onSelectMode = { mode ->
                viewModel.onSelectMode(mode)
                Toast.makeText(
                    context,
                    if (mode == state.activeMode) {
                        "已切换到 ${mode.label}"
                    } else {
                        "${mode.label} 不可用，已降级为 ${state.activeMode.label}"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            },
            onToggleMonitor = viewModel::setMonitorEnabled,
            onToggleAutoBlock = viewModel::setAutoBlockEnabled,
            onToggleDaemon = viewModel::setDaemonEnabled,
            onOpenAccessibility = viewModel::openAccessibilitySettings,
            onOpenEventLog = {
                eventEntries = EventLog.getInstance().getAll()
                screen = Screen.EVENT_LOG
            },
            onOpenAbout = { screen = Screen.ABOUT }
        )

        Screen.EVENT_LOG -> EventLogScreen(
            entries = eventEntries,
            onBack = { screen = Screen.SETTINGS },
            onClear = {
                EventLog.getInstance().clear()
                eventEntries = emptyList()
            }
        )

        Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.SETTINGS })
    }
}
