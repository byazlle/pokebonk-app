# Pokebonk 安卓应用（网页游戏封装 · 原版 + 手柄版）

把 https://pokebonk.com/ 封装为原生安卓应用，共两个版本（一次构建同时产出）：

| 版本 | 包名 | 说明 |
|------|------|------|
| **原版** | `com.pokebonk.app` | 纯触屏/键盘 WebView 封装 |
| **手柄版** | `com.pokebonk.app.gamepad` | Xbox 手柄支持 + 启动菜单 + 按键映射设置 |

## 手柄版功能

- **每次启动先弹菜单**：「▶ 进入游戏」/「🎮 映射按键设置」
- **映射按键设置**：三列对照表——`游戏内对应功能 | 手柄按键 | 绑定目标键`
  - 点按一行 → 修改手柄按键
  - 长按一行 → 修改绑定的目标键
  - 「重置」恢复出厂映射；修改实时保存（SharedPreferences）
- **输入注入原理**：手柄按键 → 合成 Android KeyEvent 注入 WebView → 游戏收到对应键盘事件（W/A/S/D、空格、Shift 等）；「锁定目标」默认绑定鼠标右键，通过向页面注入 MouseEvent 实现
- 支持左摇杆（模拟移动）、十字键（HAT 轴兼容）、LT/RT 扳机（多轴兼容）、滞回防抖

## 默认映射表（Xbox 手柄标准）

| 游戏内对应功能 | 手柄按键 | 绑定目标键 |
|----------------|----------|------------|
| 移动·上/下/左/右 | 左摇杆 | W / S / A / D |
| 移动·上/下/左/右 | 十字键 | W / S / A / D |
| 跳跃 | A | 空格 |
| 滑铲（空中：俯冲） | B | Shift |
| 互动 | X | E |
| 终极技 | Y | R |
| 锁定目标 | LB | 鼠标右键（注入） |
| 自动/手动瞄准 | RB | Q |
| 自动终极技能 | LT | Y |
| 自动前进 | RT | T |
| 放逐（升级时） | L3 | Z |
| 刷新（升级时） | R3 | F |
| 跳过（升级时） | Back(视图) | X |
| 大地图 | Start(菜单) | M |
| 暂停 | — | 未绑定（可自行设置） |

> 注：因手柄按键数量少于游戏功能数，默认表中"暂停"等个别功能留空或复用，均可在应用内"映射按键设置"中自行调整。

## 共同特性（两个版本）

| 要求 | 实现方式 |
|------|----------|
| Chromium 内核 | Android System WebView（内核即 Chromium，与 Edge 同源） |
| 手机 UA | 固定 Pixel 8 + Chrome 126 移动端 User-Agent |
| 强制横屏 | `sensorLandscape` + `configChanges` 防旋转重建 |
| 默认全屏沉浸 | 隐藏状态栏/导航栏，边缘上滑临时呼出；刘海屏 shortEdges 适配；屏幕常亮 |
| 正常交互 | JS、DOM Storage、自动播放音效、HTML5 全屏、硬件加速 |

## 获取 APK（GitHub Actions 自动打包）

1. 推送到本仓库后，进入 **Actions** 页签
2. 等 "Build APK" 工作流跑完，在运行结果页下载 **pokebonk-apks**
3. 解压得两个 APK：`Pokebonk-原版-debug.apk`、`Pokebonk-手柄版-debug.apk`，传到手机安装

两版本包名不同，可同时安装对比。

## 本地构建

Android Studio 打开项目 → `Build → Build APK(s)`；或命令行：

```bash
gradle assembleOriginalDebug assembleGamepadDebug
```

产物位于 `app/build/outputs/apk/<flavor>/debug/`。

## 修改入口

- 游戏网址：`GameActivity.java` 的 `HOME_URL`
- 默认手柄映射：`GamepadMap.java` 的 `DEFAULTS`
- 应用名：`app/build.gradle` 中两个 flavor 的 `resValue`
