# ShakeOff

**摇一摇广告拦截器** — 通过关闭目标应用的传感器权限，阻断摇一摇广告。

[![Android CI](https://github.com/GHhuang1057/ShakeOff/actions/workflows/android.yml/badge.svg)](https://github.com/GHhuang1057/ShakeOff/actions/workflows/android.yml)

## 功能

- 列出设备上所有可启动的应用（自动过滤无启动 Intent 的后台组件）
- 按应用名称或包名搜索
- 可选显示系统应用
- 下拉刷新
- 一键开关单个应用的传感器权限（`OP_MOTION_SENSORS`）
- 顶部实时显示 Shizuku 状态：未安装 / 未运行 / 未授权 / 已授权
- 未授权 Shizuku 时自动跳转系统应用详情页，引导手动关闭传感器权限
- Material 3 设计，支持深色模式，Android 12+ 动态取色

## 工作原理

大量国产 App 通过「摇一摇」触发广告，其底层依赖加速度传感器（`OP_MOTION_SENSORS`）。

ShakeOff 借助 [Shizuku](https://github.com/RikkaApps/Shizuku) 获得 shell 权限后，执行：

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
app/src/main/java/io/github/geekhonzie/shakeoff/
├── MainActivity.kt              # 单 Activity 入口与页面导航
├── data/
│   ├── AppInfo.kt               # 应用信息模型
│   └── AppOpsRepository.kt      # appops 读写 + 应用列表加载
├── ui/
│   ├── MainViewModel.kt         # MVVM ViewModel
│   ├── screens/
│   │   ├── HomeScreen.kt        # 首页应用列表
│   │   └── SettingsScreen.kt    # 设置页与关于页
│   └── theme/                   # Material 3 主题
└── util/
    ├── ShizukuManager.kt        # Shizuku 状态、授权与提权执行
    └── StatusUi.kt              # 状态文案与指示色
```

## 开源许可

基于 [GPL-3.0](LICENSE) 开源。

作者：**huang1057**  
组织：**Geekhonize Software**

## 免责声明

本应用按「现状」提供，不对任何因使用本应用导致的直接或间接损失承担责任。用户应自行确保操作符合当地法律法规，并自行承担全部后果。本项目开源，仅供学习交流使用。
