package io.github.geekhonize.shakeoff.data

import android.graphics.drawable.Drawable

/**
 * 已安装应用的信息。
 *
 * @param packageName 应用包名
 * @param label 应用名称
 * @param icon 应用图标，获取失败时为 null
 * @param isSystem 是否为系统应用
 * @param sensorBlocked 当前是否已屏蔽摇一摇
 * @param isAdware 是否命中广告特征库
 * @param adCategory 广告分类（isAdware 为 true 时有值）
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val sensorBlocked: Boolean,
    val isAdware: Boolean = false,
    val adCategory: String = ""
)
