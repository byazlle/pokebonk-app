# Pokebonk 安卓/桌面封装（网页游戏 · 三版本）

把 https://pokebonk.com/ 封装为原生应用，共三个版本（v0.2 起）：

| 版本 | 平台 | 说明 |
|------|------|------|
| **Pokebonk（原版）** | Android | 纯触屏封装 |
| **Pokebonk 手柄版** | Android | Xbox 手柄支持 + 启动菜单 + 按键映射设置 |
| **Pokebonk 电脑手柄版** | Windows (Electron) | Ins 键设置菜单 + Xbox 手柄映射 |

## 下载（Release v0.2）

进入 [Releases · v0.2](https://github.com/byazlle/pokebonk-app/releases/tag/v0.2) 下载：

- `Pokebonk-0.2-original-debug.apk` — 手机原版
- `Pokebonk-0.2-gamepad-debug.apk` — 手机手柄版
- `Pokebonk-0.2-pc-win64-setup.exe` — **电脑手柄版安装包**（推荐：双击安装，可选安装目录，自动创建开始菜单/桌面快捷方式）
- `Pokebonk-0.2-pc-win64.zip` — 电脑手柄版免安装压缩包（解压运行 `Pokebonk.exe`）

## v0.2 新特性

- **主菜单**：启动即显示（开始游戏 / 设置），**游戏在菜单背后持续加载运行**；点"开始游戏"有推进转场动画
- **专用设置键**：游戏进行中按手柄 **Back(视图键)** 随时呼出设置（打开设置时游戏挂起，关闭后恢复）
- **电脑版 Ins 键**：任何焦点状态下按 **Ins** 弹出/关闭设置菜单
- 菜单美化：渐变背景、发光标题浮动、按钮渐变与错峰入场动画

## 主菜单说明

| 版本 | 菜单内容 |
|------|----------|
| 手机原版 | 只显示「开始游戏」 |
| 手机手柄版 | 「开始游戏」+「设置」，并提示手柄专用键 |
| 电脑手柄版 | 「开始游戏」+「设置」，提示 Ins 键与手柄专用键 |

## 手柄版：按键映射

- 设置页三列对照：`游戏内对应功能 | 手柄按键 | 绑定目标键`
- 手机版：点按行改手柄按键，长按行改目标键；电脑版：下拉框直接改；均实时保存（SharedPreferences / localStorage）
- 输入注入原理：手柄按键 → 合成键盘事件注入游戏 WebView；「锁定目标」默认注入鼠标右键
- 支持左摇杆、十字键（HAT 轴）、LT/RT 扳机，滞回防抖

## 默认映射表（Xbox 手柄标准，v0.2）

| 游戏内对应功能 | 手柄按键 | 绑定目标键 |
|----------------|----------|------------|
| 移动 | 左摇杆 | W / S / A / D |
| 跳跃 | A | 空格 |
| 滑铲（空中：俯冲） | B | Shift |
| 互动 | X | E |
| 终极技 | Y | R |
| 锁定目标 | LB | 鼠标右键（注入） |
| 自动/手动瞄准 | RB | Q |
| 自动终极技能 | 十字键·上 | Y |
| 暂停 | 十字键·下 | P |
| 刷新（升级时） | 十字键·左 | F |
| 跳过（升级时） | 十字键·右 | X |
| 放逐（升级时） | L3 | Z |
| 大地图 | LT | M |
| 自动前进 | RT | T |
| 打开设置 | **Back(视图键)** | 专用键，不参与游戏映射 |

> 个别键位（如 Start、R3）默认留空，可在设置中自行绑定。

## 共同特性

| 要求 | 实现方式 |
|------|----------|
| Chromium 内核 | Android System WebView / Electron(Chromium) |
| 手机 UA | Pixel 8 + Chrome 126 移动端 User-Agent（Android） |
| 强制横屏 | `sensorLandscape`（Android）；PC 版为窗口自适应 |
| 默认全屏沉浸 | 隐藏状态栏/导航栏；刘海屏 shortEdges；屏幕常亮 |

## 从源码构建

- Android：Android Studio 打开项目 → `Build APK(s)`；或 `gradle assembleOriginalDebug assembleGamepadDebug`
- Windows（生成安装包）：`cd pc-app && npm install && npx electron-builder --win --x64`，产物 `dist/Pokebonk-0.2-pc-win64-setup.exe`

推送任意提交也会触发 Actions 自动构建三份产物。

## 修改入口

- 游戏网址：`GameActivity.java` 的 `HOME_URL` / `pc-app/wrapper.html` 的 webview src
- 默认手柄映射：`GamepadMap.java` 的 `DEFAULTS` / `pc-app/renderer.js` 的 `DEFAULTS`
- 版本号：`app/build.gradle` 的 `versionName` / `pc-app/package.json` 的 `version`
