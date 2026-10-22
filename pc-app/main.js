/**
 * Pokebonk 电脑手柄版 — Electron 主进程
 * - 游戏以 <webview> 加载（Chromium 内核），主菜单悬浮于其上，游戏在背后持续运行
 * - Ins 键（无论焦点在哪）→ 通知渲染进程弹出/关闭设置菜单
 * - 渲染进程通过 IPC 把手柄输入合成为键盘/鼠标事件，注入到游戏 webview
 * - 打开设置时：注入游戏暂停键 + 静音（挂起游戏），关闭时恢复
 */
const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');

let win = null;
let guest = null; // 游戏 webview 的 webContents

app.on('web-contents-created', (e, contents) => {
  if (contents.getType() === 'webview') {
    guest = contents;
  }
});

function createWindow() {
  win = new BrowserWindow({
    width: 1280,
    height: 720,
    minWidth: 960,
    minHeight: 540,
    autoHideMenuBar: true,
    title: 'Pokebonk 手柄版 (PC)',
    backgroundColor: '#141420',
    webPreferences: {
      webviewTag: true,
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  win.loadFile('wrapper.html');

  // Ins 键全局拦截：即使游戏 webview 持有键盘焦点也能触发设置菜单
  win.webContents.on('before-input-event', (event, input) => {
    if (input.type === 'keyDown' && input.key === 'Insert') {
      win.webContents.send('toggle-settings');
    }
  });
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());

// ---------- IPC：手柄输入注入 / 游戏控制 ----------

// 把键事件注入游戏（code 为 DOM code 风格：KeyW / Space / ArrowUp / Digit1 ...）
ipcMain.handle('send-key', (e, { code, down }) => {
  if (!guest) return false;
  try {
    guest.sendInputEvent({ type: down ? 'keyDown' : 'keyUp', keyCode: code });
    return true;
  } catch (err) {
    return false;
  }
});

// 鼠标右键点击（游戏内"锁定目标"）
ipcMain.handle('mouse-right', () => {
  if (!guest) return false;
  try {
    const [w, h] = win.getContentSize();
    guest.sendInputEvent({ type: 'mouseDown', x: Math.floor(w / 2), y: Math.floor(h / 2), button: 'right', clickCount: 1 });
    guest.sendInputEvent({ type: 'mouseUp', x: Math.floor(w / 2), y: Math.floor(h / 2), button: 'right', clickCount: 1 });
    return true;
  } catch (err) {
    return false;
  }
});

// 让游戏 webview 获得焦点（原生键盘可直接操作游戏）
ipcMain.handle('focus-game', () => {
  if (guest) { try { guest.focus(); } catch (err) {} }
});

// 静音（打开设置时挂起游戏声音）
ipcMain.handle('set-muted', (e, m) => {
  if (guest) { try { guest.setAudioMuted(!!m); } catch (err) {} }
});
