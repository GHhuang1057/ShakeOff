package io.github.geekhonize.shakeoff.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.geekhonize.shakeoff.data.AppInfo
import io.github.geekhonize.shakeoff.data.AppOpsRepository
import io.github.geekhonize.shakeoff.monitor.EventLog
import io.github.geekhonize.shakeoff.monitor.MonitorPrefs
import io.github.geekhonize.shakeoff.monitor.ShellDaemonManager
import io.github.geekhonize.shakeoff.strategy.ControlMode
import io.github.geekhonize.shakeoff.strategy.ModeAvailability
import io.github.geekhonize.shakeoff.strategy.ModeManager
import io.github.geekhonize.shakeoff.util.ShizukuManager
import io.github.geekhonize.shakeoff.util.ShizukuStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页 UI 状态。
 */
data class HomeUiState(
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,

    /** 当前实际生效的模式 */
    val activeMode: ControlMode = ControlMode.SHIZUKU,

    /** 用户期望的模式 */
    val requestedMode: ControlMode = ControlMode.SHIZUKU,

    /** 各模式可用性 */
    val availability: List<ModeAvailability> = emptyList(),

    val apps: List<AppInfo> = emptyList(),
    val query: String = "",
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null,

    /** 监控相关开关 */
    val monitorEnabled: Boolean = false,
    val autoBlockEnabled: Boolean = false,
    val daemonEnabled: Boolean = false,
    val daemonRunning: Boolean = false
) {
    /**
     * 按搜索关键字过滤后的应用列表。
     */
    val visibleApps: List<AppInfo>
        get() = if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }

    /**
     * 设备上疑似广告应用的数量。
     */
    val adwareCount: Int
        get() = apps.count { it.isAdware }
}

/**
 * 单 Activity + MVVM：首页列表、模式切换、广告监控与传感器权限开关。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppOpsRepository(application)
    private val modeManager = ModeManager(application)
    private val daemonManager = ShellDaemonManager(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // 应用启动时解析模式（含自动降级）
        resolveMode(initial = true)
        loadApps()
        syncMonitorPrefs()
    }

    /**
     * 解析当前模式。
     *
     * @param initial 是否为首次解析（首次不提示降级 Toast）
     */
    fun resolveMode(initial: Boolean = false) {
        viewModelScope.launch {
            val result = modeManager.resolve()
            val availability = modeManager.checkAvailability()

            var message: String? = null
            if (result.degraded && !initial) {
                message = "当前模式不可用，已降级为 ${result.activeMode.label}"
            }

            _uiState.value = _uiState.value.copy(
                activeMode = result.activeMode,
                requestedMode = result.requestedMode,
                availability = availability,
                shizukuStatus = ShizukuManager.status(getApplication()),
                message = message
            )

            // 无障碍模式下不可启用守护进程
            if (result.activeMode != ControlMode.SHIZUKU && _uiState.value.daemonEnabled) {
                setDaemonEnabled(false)
            }
        }
    }

    /**
     * 加载已安装应用，并通过当前生效策略查询传感器状态。
     */
    fun loadApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val includeSystem = _uiState.value.showSystemApps
            val mode = _uiState.value.activeMode
            val strategy = modeManager.strategyFor(mode)

            // 先取全量包名（用于状态查询）
            val all = withContext(Dispatchers.IO) {
                repository.loadInstalledApps(includeSystem = true)
            }
            val packageNames = all.map { it.packageName }

            // 通过当前策略批量查询状态
            val sensorState = HashMap<String, Boolean>(packageNames.size)
            for (pkg in packageNames) {
                sensorState[pkg] = try {
                    strategy.isBlocked(pkg)
                } catch (e: Exception) {
                    false
                }
            }

            val apps = repository.loadInstalledApps(
                includeSystem = includeSystem,
                sensorState = sensorState
            )

            _uiState.value = _uiState.value.copy(
                apps = apps,
                isLoading = false
            )
        }
    }

    /**
     * 下拉刷新：同时刷新模式与应用列表。
     */
    fun refresh() {
        resolveMode(initial = true)
        loadApps()
        syncMonitorPrefs()
    }

    /**
     * 更新搜索关键字。
     */
    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    /**
     * 切换是否显示系统应用。
     */
    fun onToggleSystemApps(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showSystemApps = enabled)
        loadApps()
    }

    /**
     * 切换某应用的传感器权限开关。
     *
     * 无障碍模式无法直接改权限，改为提示已记录。
     */
    fun onToggleSensor(packageName: String, block: Boolean) {
        val mode = _uiState.value.activeMode

        // 无障碍模式下开关无实际意义，提示用户
        if (mode == ControlMode.ACCESSIBILITY) {
            _uiState.value = _uiState.value.copy(
                message = "无障碍模式无法直接关闭传感器权限，仅能在广告弹出时自动点击跳过"
            )
            EventLog.getInstance().add(
                packageName,
                "无障碍模式无法关闭权限",
                false
            )
            return
        }

        viewModelScope.launch {
            val strategy = modeManager.strategyFor(mode)
            val ok = try {
                strategy.setBlocked(packageName, block)
            } catch (e: Exception) {
                false
            }

            if (ok) {
                _uiState.value = _uiState.value.copy(
                    apps = _uiState.value.apps.map {
                        if (it.packageName == packageName) {
                            it.copy(sensorBlocked = block)
                        } else {
                            it
                        }
                    },
                    message = if (block) "已屏蔽摇一摇" else "已恢复传感器权限"
                )
                EventLog.getInstance().add(
                    packageName,
                    if (block) "禁用传感器权限" else "恢复传感器权限",
                    true
                )
            } else {
                _uiState.value = _uiState.value.copy(message = "操作失败，请重试")
                EventLog.getInstance().add(packageName, "操作失败", false)
            }
        }
    }

    /**
     * 用户手动选择模式。
     *
     * 若所选模式不可用，[ModeManager] 会自动降级并在此提示。
     */
    fun onSelectMode(mode: ControlMode) {
        viewModelScope.launch {
            val result = modeManager.selectMode(mode)
            val availability = modeManager.checkAvailability()

            val message = if (result.degraded) {
                "当前模式不可用，已降级为 ${result.activeMode.label}"
            } else {
                "已切换到 ${result.activeMode.label}"
            }

            _uiState.value = _uiState.value.copy(
                activeMode = result.activeMode,
                requestedMode = result.requestedMode,
                availability = availability,
                message = message
            )

            // 模式变化后重新加载状态
            loadApps()
        }
    }

    /**
     * 请求 Shizuku 授权。
     */
    fun requestShizukuPermission() {
        when (ShizukuManager.status(getApplication())) {
            ShizukuStatus.NOT_INSTALLED -> {
                _uiState.value = _uiState.value.copy(message = "请先安装 Shizuku")
                ShizukuManager.openShizukuDownload(getApplication())
            }

            ShizukuStatus.NOT_RUNNING -> {
                _uiState.value = _uiState.value.copy(message = "请先启动 Shizuku")
                ShizukuManager.openShizukuApp(getApplication())
            }

            ShizukuStatus.NOT_GRANTED -> {
                val requested = ShizukuManager.requestPermission()
                _uiState.value = _uiState.value.copy(
                    message = if (requested) "已发起授权请求" else "授权请求失败"
                )
            }

            ShizukuStatus.GRANTED -> {
                _uiState.value = _uiState.value.copy(message = "Shizuku 已授权")
            }
        }
    }

    /**
     * 打开无障碍设置页。
     */
    fun openAccessibilitySettings() {
        modeManager.openAccessibilitySettings()
    }

    /**
     * 同步监控相关开关状态。
     */
    fun syncMonitorPrefs() {
        val context = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            monitorEnabled = MonitorPrefs.isMonitorEnabled(context),
            autoBlockEnabled = MonitorPrefs.isAutoBlockEnabled(context),
            daemonEnabled = MonitorPrefs.isDaemonEnabled(context)
        )
    }

    /**
     * 切换实时广告监控。
     */
    fun setMonitorEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        MonitorPrefs.setMonitorEnabled(context, enabled)
        _uiState.value = _uiState.value.copy(monitorEnabled = enabled)

        viewModelScope.launch {
            io.github.geekhonize.shakeoff.monitor.AdwareScanWorker.schedule(context)
            _uiState.value = _uiState.value.copy(
                message = if (enabled) "已开启实时广告监控" else "已关闭实时广告监控"
            )
        }
    }

    /**
     * 切换自动拦截。
     */
    fun setAutoBlockEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        MonitorPrefs.setAutoBlockEnabled(context, enabled)
        _uiState.value = _uiState.value.copy(autoBlockEnabled = enabled)
    }

    /**
     * 切换 Shell 守护进程。仅 Shizuku 模式可用。
     */
    fun setDaemonEnabled(enabled: Boolean) {
        val context = getApplication<Application>()

        // 非 Shizuku 模式直接拒绝
        if (enabled && _uiState.value.activeMode != ControlMode.SHIZUKU) {
            _uiState.value = _uiState.value.copy(
                message = "守护进程仅在 Shizuku 模式下可用"
            )
            return
        }

        MonitorPrefs.setDaemonEnabled(context, enabled)
        _uiState.value = _uiState.value.copy(daemonEnabled = enabled)

        viewModelScope.launch {
            val ok = if (enabled) daemonManager.start() else daemonManager.stop()
            val running = daemonManager.isRunning()
            _uiState.value = _uiState.value.copy(
                daemonRunning = running,
                message = when {
                    !ok && enabled -> "守护进程启动失败"
                    enabled -> "守护进程已启动"
                    else -> "守护进程已停止"
                }
            )
        }
    }

    /**
     * 消费一次性消息。
     */
    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
