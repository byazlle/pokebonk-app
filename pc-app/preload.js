const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('native', {
  /** 向游戏注入键盘事件（code: DOM code，如 KeyW / Space） */
  sendKey: (code, down) => ipcRenderer.invoke('send-key', { code, down }),
  /** 注入鼠标右键点击（锁定目标） */
  mouseRight: () => ipcRenderer.invoke('mouse-right'),
  /** 让游戏获得焦点 */
  focusGame: () => ipcRenderer.invoke('focus-game'),
  /** 静音/取消静音 */
  setMuted: (m) => ipcRenderer.invoke('set-muted', m),
  /** Ins 键回调（主进程拦截，任何焦点状态都触发） */
  onToggleSettings: (cb) => ipcRenderer.on('toggle-settings', () => cb())
});
