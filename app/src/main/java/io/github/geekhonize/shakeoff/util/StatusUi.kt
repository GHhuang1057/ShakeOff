package io.github.geekhonize.shakeoff.util

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Shizuku 状态对应的展示文案。
 */
fun shizukuStatusText(status: ShizukuStatus): String = when (status) {
    ShizukuStatus.NOT_INSTALLED -> "Shizuku 未安装"
    ShizukuStatus.NOT_RUNNING -> "Shizuku 未运行"
    ShizukuStatus.NOT_GRANTED -> "Shizuku 未授权"
    ShizukuStatus.GRANTED -> "Shizuku 已授权"
}

/**
 * Shizuku 状态对应的指示色：已授权为 primary，其余为 error。
 */
fun shizukuStatusColor(status: ShizukuStatus, colorScheme: ColorScheme): Color =
    when (status) {
        ShizukuStatus.GRANTED -> colorScheme.primary
        else -> colorScheme.error
    }
