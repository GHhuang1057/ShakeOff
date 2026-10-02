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
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.geekhonize.shakeoff.ui.MainViewModel
import io.github.geekhonize.shakeoff.ui.screens.AboutScreen
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
 * 简单的页面状态：首页 / 设置 / 关于。
 */
private enum class Screen {
    HOME, SETTINGS, ABOUT
}

/**
 * 应用根 Composable：负责导航与 Shizuku 授权回调。
 */
@Composable
private fun ShakeOffApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: MainViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    var screen by remember { mutableStateOf(Screen.HOME) }

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
                val needSettings = viewModel.onToggleSensor(app.packageName, block)
                if (needSettings) {
                    // 未授权：跳转应用详情页引导手动关闭传感器权限
                    ShizukuManager.openAppDetails(context, app.packageName)
                    Toast.makeText(
                        context,
                        "请在系统设置中手动关闭该应用的传感器权限",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onOpenSettings = { screen = Screen.SETTINGS },
            onRequestShizuku = viewModel::requestShizukuPermission
        )

        Screen.SETTINGS -> SettingsScreen(
            onBack = { screen = Screen.HOME },
            onOpenAbout = { screen = Screen.ABOUT },
            onOpenLicenses = { screen = Screen.ABOUT }
        )

        Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.SETTINGS })
    }
}
