package io.github.geekhonize.shakeoff.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.geekhonize.shakeoff.BuildConfig
import io.github.geekhonize.shakeoff.monitor.EventEntry
import io.github.geekhonize.shakeoff.strategy.ControlMode
import io.github.geekhonize.shakeoff.strategy.ModeAvailability

/**
 * 设置页：模式选择器、监控设置、拦截记录入口、开源许可与关于。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    activeMode: ControlMode,
    requestedMode: ControlMode,
    availability: List<ModeAvailability>,
    monitorEnabled: Boolean,
    autoBlockEnabled: Boolean,
    daemonEnabled: Boolean,
    daemonRunning: Boolean,
    onBack: () -> Unit,
    onSelectMode: (ControlMode) -> Unit,
    onToggleMonitor: (Boolean) -> Unit,
    onToggleAutoBlock: (Boolean) -> Unit,
    onToggleDaemon: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenEventLog: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ===== 模式选择器 =====
            SectionTitle("控制模式")
            Text(
                text = "优先级：Shizuku > Device Owner > 无障碍。所选模式不可用时会自动降级到下一个可用模式。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ControlMode.byPriority.forEach { mode ->
                        ModeOption(
                            mode = mode,
                            isActive = mode == activeMode,
                            isRequested = mode == requestedMode,
                            availability = availability.firstOrNull { it.mode == mode },
                            onSelect = { onSelectMode(mode) }
                        )
                    }
                }
            }

            // 当前生效状态
            if (activeMode != requestedMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "「${requestedMode.label}」当前不可用，已自动降级为「${activeMode.label}」。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Device Owner 配置引导
            if (availability.firstOrNull { it.mode == ControlMode.DEVICE_OWNER }
                    ?.available == false
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "配置 Device Owner 模式",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "用 ADB 执行以下命令：",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = DPM_COMMAND,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "注意：手机不能有已登录的账户，必要时需恢复出厂设置后执行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 无障碍开启引导
            if (availability.firstOrNull { it.mode == ControlMode.ACCESSIBILITY }
                    ?.available == false
            ) {
                TextButton(onClick = onOpenAccessibility) {
                    Text("开启无障碍服务（保底模式）")
                }
            }

            HorizontalDivider()

            // ===== 监控设置 =====
            SectionTitle("监控设置")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    SwitchRow(
                        title = "实时监控广告应用",
                        subtitle = "新应用安装时与特征库比对，命中则发通知提醒",
                        checked = monitorEnabled,
                        onCheckedChange = onToggleMonitor
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SwitchRow(
                        title = "自动拦截",
                        subtitle = "检测到广告应用后自动禁用其传感器权限",
                        checked = autoBlockEnabled,
                        enabled = monitorEnabled,
                        onCheckedChange = onToggleAutoBlock
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SwitchRow(
                        title = "Shell 守护进程",
                        subtitle = if (activeMode == ControlMode.SHIZUKU) {
                            "以 shell 权限后台监控安装事件与广告日志"
                        } else {
                            "仅 Shizuku 模式可用"
                        },
                        checked = daemonEnabled && daemonRunning,
                        enabled = activeMode == ControlMode.SHIZUKU,
                        trailing = if (daemonRunning) "运行中" else null,
                        onCheckedChange = onToggleDaemon
                    )
                }
            }

            HorizontalDivider()

            // ===== 其他入口 =====
            SectionTitle("其他")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ClickableRow("拦截记录", "查看已拦截的广告与传感器操作") {
                        onOpenEventLog()
                    }
                    HorizontalDivider()
                    ClickableRow("开源许可", "查看本应用使用的开源库") {
                        onOpenAbout()
                    }
                    HorizontalDivider()
                    ClickableRow(
                        "关于",
                        "${BuildConfig.VERSION_NAME} · Geekhonize Software"
                    ) {
                        onOpenAbout()
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 拦截记录页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventLogScreen(
    entries: List<EventEntry>,
    onBack: () -> Unit,
    onClear: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拦截记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onClear) { Text("清空") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (entries.isEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "暂无拦截记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                entries.forEach { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (entry.success) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = entry.target,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (entry.success) "成功" else "失败",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (entry.success) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                            Text(
                                text = entry.action,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = entry.formattedTime(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 关于页：版本信息、三模式能力边界与隐私声明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ShakeOff",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "摇一摇广告拦截器",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            InfoRow("版本", BuildConfig.VERSION_NAME)
            InfoRow("版本号", BuildConfig.VERSION_CODE.toString())
            InfoRow("组织", "Geekhonize Software")
            InfoRow("作者", "huang1057")
            InfoRow("包名", BuildConfig.APPLICATION_ID)
            InfoRow("开源协议", "GPL-3.0")

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("三种模式的能力边界")
            ModeBoundaryCard(
                mode = ControlMode.SHIZUKU,
                boundary = "可真正禁用/恢复传感器权限，功能最完整。需安装并授权 Shizuku。"
            )
            ModeBoundaryCard(
                mode = ControlMode.DEVICE_OWNER,
                boundary = "可管理传感器权限授予状态，但无法执行 appops 以外的 shell 操作。需 ADB 设为设备所有者，且手机无已登录账户。"
            )
            ModeBoundaryCard(
                mode = ControlMode.ACCESSIBILITY,
                boundary = "无法禁用传感器权限，只能在广告弹窗出现后自动点击「跳过」。属保底方案。"
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("隐私声明")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "· 无障碍模式仅在本机读取界面节点用于判断按钮，不收集、不存储、不上传任何屏幕内容。\n" +
                                "· Shizuku 与 Device Owner 模式通过系统 API 管理应用权限，不涉及屏幕内容。\n" +
                                "· 广告特征库为本地静态数据（参考 AdwareZoo / Exodus Privacy 公开条目），匹配完全在本机完成。\n" +
                                "· 本应用不收集任何用户数据，无网络请求，无统计上报。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("免责声明")
            Text(
                text = "本应用按「现状」提供，不对任何因使用本应用导致的直接或间接损失承担责任。" +
                        "用户应自行确保操作符合当地法律法规，并自行承担全部后果。" +
                        "本项目开源，仅供学习交流使用。",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ===== 内部组件 =====

/**
 * 区块标题。
 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

/**
 * 模式单选项。
 */
@Composable
private fun ModeOption(
    mode: ControlMode,
    isActive: Boolean,
    isRequested: Boolean,
    availability: ModeAvailability?,
    onSelect: () -> Unit
) {
    val available = availability?.available ?: false

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isRequested, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isRequested,
            onClick = onSelect,
            enabled = true
        )
        Spacer(modifier = Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.titleMedium
                )
                if (isActive) {
                    Text(
                        text = "生效中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (available) "可用" else "不可用",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (available) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            Text(
                text = mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!available && !availability?.reason.isNullOrBlank()) {
                Text(
                    text = availability!!.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 开关行。
 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    trailing: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (trailing != null) {
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

/**
 * 可点击行。
 */
@Composable
private fun ClickableRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 关于页的键值行。
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp)
    }
}

/**
 * 模式能力边界说明卡片。
 */
@Composable
private fun ModeBoundaryCard(mode: ControlMode, boundary: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = mode.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = boundary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Device Owner 配置命令。
 */
private const val DPM_COMMAND =
    "adb shell dpm set-device-owner io.github.geekhonize.shakeoff/.DeviceAdminReceiver"
