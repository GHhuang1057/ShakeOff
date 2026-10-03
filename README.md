# ShakeOff

**摇一摇广告拦截器** — 通过关闭目标应用的传感器权限，阻断摇一摇广告。

[![Android CI](https://github.com/GHhuang1057/ShakeOff/actions/workflows/android.yml/badge.svg)](https://github.com/GHhuang1057/ShakeOff/actions/workflows/android.yml)

## 功能

- 列出设备上所有可启动的应用（自动过滤无启动 Intent 的后台组件）
- 按应用名称或包名搜索
- 可选显示系统应用
- 下拉刷新
- 一键开关单个应用的传感器权限（`OP_MOTION_SENSORS`）
- **三种控制模式 + 优先级自动降级**：Shizuku > Device Owner > 无障碍
- **实时广告应用监控**：新应用安装时与本地特征库比对，命中即发通知提醒
- **可选 Shell 守护进程**（仅 Shizuku 模式）：后台监控安装事件与广告日志
- 顶部显示当前生效模式 Chip，降级时高亮提示
- 广告应用在列表中带红色「广告」标签并排在最前
- Material 3 设计，支持深色模式，Android 12+ 动态取色

## 三种控制模式

优先级从高到低，可用性由 `ModeManager` 在应用启动时自动探测。

| 优先级 | 模式 | 实现方式 | 能力边界 |
|---|---|---|---|
| 1 | **Shizuku 模式** | 通过 UserService 以 shell 权限执行 `appops` | 可真正禁用/恢复传感器权限，功能最完整。需安装并授权 Shizuku |
| 2 | **Device Owner 模式** | `DevicePolicyManager.setPermissionGrantState()` | 可管理传感器权限授予状态，但无法执行 shell 操作。需 ADB 设为设备所有者 |
| 3 | **无障碍模式** | `AdSkipAccessibilityService` 读取节点自动点击 | 无法禁用权限，只能在广告弹出后点击「跳过」。属保底方案 |

### 自动降级规则

1. 取用户在设置页选择的模式
2. 该模式可用 → 直接使用
3. 该模式不可用 → 按优先级顺序（不高于用户期望的层级）找第一个可用模式
4. 全部不可用 → 退回无障碍模式，并引导用户去系统设置开启

降级发生时 Toast 提示「当前模式不可用，已降级为 XXX 模式」，首页模式 Chip 旁也会显示「已从 XXX 降级」。

### Device Owner 配置步骤

```bash
adb shell dpm set-device-owner io.github.geekhonize.shakeoff/.DeviceAdminReceiver
```

> ⚠️ 手机**不能有已登录的账户**，否则命令会失败；必要时需恢复出厂设置后执行。

### 无障碍模式配置步骤

设置 → 无障碍 → 已下载的服务 → 找到「ShakeOff 广告跳过」→ 开启。

## 广告应用监控

- 特征库位于 `assets/adware_packages.json`，收录 55 条常见广告/追踪组件包名
- 来源参考 [AdwareZoo](https://adware.zone) 与 [Exodus Privacy](https://exodus-privacy.eu.org) 的公开条目
- `BroadcastReceiver` 监听 `ACTION_PACKAGE_ADDED`，新应用安装后立即匹配
- 另用 WorkManager 每 30 分钟兜底巡检（防止进程被系统杀掉后漏检）
- 命中后发高优先级通知，点击跳转该应用详情页
- 开启「自动拦截」后，会通过当前生效策略自动禁用其传感器权限

## Shell 守护进程（可选，仅 Shizuku 模式）

脚本位于 `assets/shakeoff_monitor.sh`，部署到 `/data/local/tmp/` 后以 shell 权限运行：

- `inotify` 监控 `/data/system/packages.xml` 感知安装事件
- 每 60 秒过滤 logcat 中的广告 SDK 日志标签
- 发现可疑活动直接执行 `appops set <包名> OP_MOTION_SENSORS ignore`
- 事件写入 `/data/local/tmp/shakeoff_events.log`，可在「拦截记录」页查看

> 实现说明：Shizuku 13.1.1 起 `Shizuku.newProcess` 已废弃并转为 private，
> 因此改用项目内的 UserService 通道启动脚本，该通道同样以 shell 权限执行，效果等价。

## 工作原理

大量国产 App 通过「摇一摇」触发广告，其底层依赖加速度传感器（`OP_MOTION_SENSORS`）。

Shizuku 模式借助 [Shizuku](https://github.com/RikkaApps/Shizuku) 获得 shell 权限后，执行：

```bash
# 查询当前状态
appops get <包名> OP_MOTION_SENSORS

# 屏蔽摇一摇
appops set <包名> OP_MOTION_SENSORS ignore

# 恢复
appops set <包名> OP_MOTION_SENSORS allow
```

`ignore` 后目标应用读不到加速度传感器数据，摇一摇广告自然失效。

## 环境要求

| 项目 | 值 |
|---|---|
| minSdk | 26（Android 8.0） |
| compileSdk / targetSdk | 36 |
| Kotlin | 2.2.20 |
| AGP | 8.13.2 |
| Gradle | 8.14.3 |
| JDK | 17 |

## 构建

```bash
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 测试步骤

1. 安装 [Shizuku](https://github.com/RikkaApps/Shizuku/releases/latest)
2. 启动 Shizuku：
   - **无线调试**：手机与电脑处于同一局域网，先执行
     ```bash
     adb pair <手机IP>:<配对端口>
     adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
     ```
   - **root**：直接在 Shizuku App 内点击启动
3. 在 Shizuku 中授予 ShakeOff 权限
4. 打开 ShakeOff，确认顶部显示「Shizuku 已授权」
5. 找到目标应用（如淘宝、京东），打开右侧开关
6. 回到目标应用测试摇一摇 —— 广告不再出现

未安装 Shizuku 时，开关会跳转系统应用详情页，可手动关闭「传感器」权限达到同样效果。

## 下载

前往 [Actions](https://github.com/GHhuang1057/ShakeOff/actions/workflows/android.yml) 下载 `ShakeOff-debug` artifact。

## 项目结构

```
app/src/main/java/io/github/geekhonize/shakeoff/
├── MainActivity.kt              # 单 Activity 入口与页面导航
├── data/
│   ├── AppInfo.kt               # 应用信息模型（含 isAdware）
│   └── AppOpsRepository.kt      # 应用列表加载 + 广告标记
├── strategy/
│   ├── ControlMode.kt           # 模式枚举与可用性模型
│   ├── SensorControlStrategy.kt # 策略接口
│   ├── ShizukuStrategy.kt       # shell 权限实现
│   ├── DeviceOwnerStrategy.kt   # 设备管理员实现
│   ├── AccessibilityStrategy.kt # 无障碍实现
│   └── ModeManager.kt           # 优先级探测与自动降级
├── monitor/
│   ├── AdwareMatcher.kt         # 广告特征库匹配
│   ├── AdwareMonitorReceiver.kt # 安装广播监听 + 自动拦截
│   ├── AdwareScanWorker.kt      # WorkManager 兜底巡检
│   ├── AdwareNotifier.kt        # 高优先级通知
│   ├── EventLog.kt              # 拦截记录存储
│   └── ShellDaemonManager.kt    # Shell 守护进程部署与控制
├── deviceowner/
│   └── DeviceAdminReceiver.kt   # 设备管理员接收器
├── accessibility/
│   └── AdSkipAccessibilityService.kt  # 广告跳过无障碍服务
├── ui/
│   ├── MainViewModel.kt         # MVVM ViewModel
│   ├── screens/
│   │   ├── HomeScreen.kt        # 首页应用列表 + 模式 Chip
│   │   └── SettingsScreen.kt    # 设置页/拦截记录/关于页
│   └── theme/                   # Material 3 主题
├── util/
│   ├── ShizukuManager.kt        # Shizuku 状态、授权与 UserService 提权
│   └── StatusUi.kt              # 状态文案与指示色
└── aidl/
    └── ICommandService.aidl     # UserService 命令执行接口

app/src/main/assets/
├── adware_packages.json         # 广告特征库（55 条）
└── shakeoff_monitor.sh          # Shell 守护进程脚本
```

## 开源许可

基于 [GPL-3.0](LICENSE) 开源。

作者：**huang1057**  
组织：**Geekhonize Software**

## 免责声明

本应用按「现状」提供，不对任何因使用本应用导致的直接或间接损失承担责任。用户应自行确保操作符合当地法律法规，并自行承担全部后果。本项目开源，仅供学习交流使用。
