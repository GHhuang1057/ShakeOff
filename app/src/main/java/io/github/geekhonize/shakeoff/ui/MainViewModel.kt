package io.github.geekhonize.shakeoff.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.geekhonize.shakeoff.data.AppInfo
import io.github.geekhonize.shakeoff.data.AppOpsRepository
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
    val apps: List<AppInfo> = emptyList(),
    val query: String = "",
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null
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
}

/**
 * 单 Activity + MVVM：首页列表、搜索、系统应用开关与传感器权限开关。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppOpsRepository(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshShizukuStatus()
        loadApps()
    }

    /**
     * 刷新 Shizuku 状态，并在已授权时同步传感器权限状态。
     */
    fun refreshShizukuStatus() {
        val status = ShizukuManager.status(getApplication())
        _uiState.value = _uiState.value.copy(shizukuStatus = status)
        if (status == ShizukuStatus.GRANTED) {
            loadApps()
        }
    }

    /**
     * 加载已安装应用。若 Shizuku 已授权，同时查询各应用的传感器权限状态。
     */
    fun loadApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val status = ShizukuManager.status(getApplication())
            val includeSystem = _uiState.value.showSystemApps

            val packages = withContext(Dispatchers.IO) {
                val all = repository.loadInstalledApps(includeSystem = true)
                all.map { it.packageName }
            }

            val sensorState = if (status == ShizukuStatus.GRANTED) {
                repository.queryAll(packages)
            } else {
                emptyMap()
            }

            val apps = repository.loadInstalledApps(
                includeSystem = includeSystem,
                sensorState = sensorState
            )

            _uiState.value = _uiState.value.copy(
                shizukuStatus = status,
                apps = apps,
                isLoading = false
            )
        }
    }

    /**
     * 下拉刷新。
     */
    fun refresh() {
        refreshShizukuStatus()
        loadApps()
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
     * @return 若需要跳转到系统设置页引导手动关闭，返回 true
     */
    fun onToggleSensor(packageName: String, block: Boolean): Boolean {
        val status = _uiState.value.shizukuStatus
        if (status != ShizukuStatus.GRANTED) {
            _uiState.value = _uiState.value.copy(
                message = "请先授权 Shizuku，或手动关闭该应用的传感器权限"
            )
            return true
        }

        viewModelScope.launch {
            val ok = repository.setBlocked(packageName, block)
            if (ok) {
                // 局部更新该项，避免整表重载
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
            } else {
                _uiState.value = _uiState.value.copy(message = "操作失败，请重试")
            }
        }
        return false
    }

    /**
     * 请求 Shizuku 授权。
     */
    fun requestShizukuPermission() {
        val status = ShizukuManager.status(getApplication())
        when (status) {
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
     * 消费一次性消息。
     */
    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
