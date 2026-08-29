# 抖音时间控制 App

Android 应用，用于控制抖音使用时长。每隔1小时允许使用15分钟（按自然小时周期）。

## 功能

- **无障碍服务**：自动检测前台应用是否为抖音
- **定时拦截**：超过15分钟后自动返回首页（kill/拦截抖音）
- **自然小时周期**：按整点计时（如 10:00-10:15 可用，10:15-11:00 拦截）
- **设置可调**：支持调整周期时长和可用时长
- **密码保护**：防止误操作

## 技术栈

- 语言：Kotlin
- 架构：MVP
- UI：Material Design + ViewBinding
- 最小 SDK：26（Android 8.0）
- 目标 SDK：34（Android 14）

## 核心文件

| 文件 | 说明 |
|------|------|
| `MainActivity.kt` | 主界面，权限检查、服务启停 |
| `TimerService.kt` | 前台服务，定时逻辑 |
| `AccessibilityControlService.kt` | 无障碍服务，检测并拦截抖音 |
| `AndroidManifest.xml` | 权限声明、组件注册 |

## 构建步骤

```bash
cd android
./gradlew assembleDebug
```

## 安装说明

1. 用 Android Studio 打开项目
2. 连接 Android 设备（Android 8.0+）
3. 运行或生成 APK 安装
4. 首次使用需手动授权：
   - 设置 → 无障碍 → 开启"抖音管控"
   - 设置 → 应用 → 权限 → 开启"悬浮窗"

## 注意事项

- 需要 Android 8.0 及以上版本
- 首次使用需在系统设置中手动授权无障碍服务
- 部分机型可能需要额外授权"后台弹出界面"权限
- 拦截方式为调用 `home()` 返回首页，如需更强拦截可结合 KILL_BACKGROUND_PROCESSES 权限
