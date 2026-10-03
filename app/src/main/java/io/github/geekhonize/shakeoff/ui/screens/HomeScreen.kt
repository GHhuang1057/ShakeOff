package io.github.geekhonize.shakeoff.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.geekhonize.shakeoff.data.AppInfo
import io.github.geekhonize.shakeoff.strategy.ControlMode
import io.github.geekhonize.shakeoff.ui.HomeUiState
import io.github.geekhonize.shakeoff.util.shizukuStatusColor
import io.github.geekhonize.shakeoff.util.shizukuStatusText

/**
 * 首页：应用列表 + 搜索 + 系统应用开关 + 下拉刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onToggleSystemApps: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onToggleSensor: (AppInfo, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestShizuku: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ShakeOff", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = shizukuStatusText(state.shizukuStatus),
                            style = MaterialTheme.typography.bodySmall,
                            color = shizukuStatusColor(state.shizukuStatus, MaterialTheme.colorScheme)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRequestShizuku) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "Shizuku 授权"
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 模式 Chip 行：显示当前生效模式与广告数量
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 当前模式
                AssistChip(
                    onClick = onOpenSettings,
                    label = {
                        Text(
                            text = state.activeMode.label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )

                // 降级提示
                if (state.activeMode != state.requestedMode) {
                    Text(
                        text = "已从「${state.requestedMode.label}」降级",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 广告应用计数
                if (state.adwareCount > 0) {
                    Text(
                        text = "广告 ${state.adwareCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 搜索框
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("搜索应用名称或包名") }
            )

            // 系统应用开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("显示系统应用", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = state.showSystemApps,
                    onCheckedChange = onToggleSystemApps
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (state.isLoading && state.apps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val pullState = rememberPullToRefreshState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullToRefresh(
                            isRefreshing = state.isLoading,
                            onRefresh = onRefresh,
                            state = pullState
                        )
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = state.visibleApps,
                            key = { it.packageName }
                        ) { app ->
                            AppRow(
                                app = app,
                                // 无障碍模式无法直接改权限，开关置灰
                                enabled = state.activeMode != ControlMode.ACCESSIBILITY,
                                onToggle = { onToggleSensor(app, it) }
                            )
                            HorizontalDivider()
                        }

                        if (state.visibleApps.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "没有匹配的应用",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 下拉刷新指示器
                    PullToRefreshDefaults.Indicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = state.isLoading,
                        state = pullState
                    )
                }
            }
        }
    }
}

/**
 * 单个应用条目：图标、名称、包名、传感器状态与开关。
 */
@Composable
private fun AppRow(
    app: AppInfo,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle(!app.sensorBlocked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 应用图标
        val bitmap = remember(app.packageName) {
            runCatching {
                app.icon?.toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX)?.asImageBitmap()
            }.getOrNull()
        }
        if (bitmap != null) {
            Icon(
                painter = BitmapPainter(bitmap),
                contentDescription = app.label,
                modifier = Modifier.size(40.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = app.label,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // 红色「广告」标签
                if (app.isAdware) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "广告",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = if (app.isAdware && app.adCategory.isNotEmpty()) {
                    "${app.packageName} · ${app.adCategory}"
                } else {
                    app.packageName
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (app.sensorBlocked) "摇一摇：已屏蔽" else "摇一摇：正常",
                style = MaterialTheme.typography.labelMedium,
                color = if (app.sensorBlocked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Switch(
            checked = app.sensorBlocked,
            onCheckedChange = onToggle,
            enabled = enabled
        )
    }
}

private const val ICON_SIZE_PX = 128
