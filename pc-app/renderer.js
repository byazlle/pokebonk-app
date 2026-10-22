/* Pokebonk 电脑手柄版 — 渲染进程：主菜单 / 设置页 / 手柄轮询与注入 */
"use strict";

// ==================== 映射数据 ====================

// 手柄标准按钮索引（Xbox）：0 A,1 B,2 X,3 Y,4 LB,5 RB,6 LT,7 RT,8 Back(专用),9 Start,10 L3,11 R3,12-15 十字键
const BTN_NAMES = {
  b0: "A", b1: "B", b2: "X", b3: "Y", b4: "LB", b5: "RB", b6: "LT", b7: "RT",
  b9: "Start", b10: "L3", b11: "R3",
  b12: "十字键·上", b13: "十字键·下", b14: "十字键·左", b15: "十字键·右",
  stickUp: "左摇杆·上", stickDown: "左摇杆·下", stickLeft: "左摇杆·左", stickRight: "左摇杆·右"
};
const BTN_ORDER = Object.keys(BTN_NAMES);

const TARGET_NAMES = {
  KeyW: "W", KeyA: "A", KeyS: "S", KeyD: "D", KeyE: "E", KeyR: "R", KeyQ: "Q",
  KeyT: "T", KeyY: "Y", KeyP: "P", KeyM: "M", KeyF: "F", KeyX: "X", KeyZ: "Z",
  KeyC: "C", KeyV: "V", KeyB: "B", KeyN: "N",
  Space: "空格", ShiftLeft: "Shift", Escape: "Esc", Enter: "Enter",
  ArrowUp: "↑", ArrowDown: "↓", ArrowLeft: "←", ArrowRight: "→",
  Digit1: "1", Digit2: "2", Digit3: "3", Digit4: "4",
  MouseRight: "鼠标右键", None: "未绑定"
};

// 出厂默认（与安卓手柄版一致）
const DEFAULTS = [
  ["移动·上", "stickUp", "KeyW"], ["移动·下", "stickDown", "KeyS"],
  ["移动·左", "stickLeft", "KeyA"], ["移动·右", "stickRight", "KeyD"],
  ["跳跃", "b0", "Space"], ["滑铲（空中：俯冲）", "b1", "ShiftLeft"],
  ["互动", "b2", "KeyE"], ["终极技", "b3", "KeyR"],
  ["锁定目标", "b4", "MouseRight"], ["自动/手动瞄准", "b5", "KeyQ"],
  ["自动终极技能", "b12", "KeyY"], ["暂停", "b13", "KeyP"],
  ["刷新（升级时）", "b14", "KeyF"], ["跳过（升级时）", "b15", "KeyX"],
  ["放逐（升级时）", "b10", "KeyZ"], ["大地图", "b6", "KeyM"],
  ["自动前进", "b7", "KeyT"], ["开始(菜单键)", "b9", "None"],
  ["L3右摇杆按下(备用)", "b11", "None"]
];

const LS_KEY = "pokebonk_map_v02";
let map = loadMap();

function loadMap() {
  try {
    const raw = localStorage.getItem(LS_KEY);
    if (raw) {
      const saved = JSON.parse(raw);
      if (Array.isArray(saved) && saved.length === DEFAULTS.length) {
        return saved.map((s, i) => ({ func: DEFAULTS[i][0], button: s[1], target: s[2] }));
      }
    }
  } catch (e) {}
  return DEFAULTS.map(d => ({ func: d[0], button: d[1], target: d[2] }));
}
function saveMap() { localStorage.setItem(LS_KEY, JSON.stringify(map.map(e => [e.button, e.target]))); }

// ==================== 主菜单 / 设置页 ====================

const $ = (id) => document.getElementById(id);
const menuEl = $("menu"), settingsEl = $("settings");
let settingsOpen = false;

function hideMenu() { menuEl.style.display = "none"; window.native.focusGame(); }
function openSettings() {
  if (settingsOpen) return;
  settingsOpen = true;
  settingsEl.style.display = "flex";
  suspendGame();   // 打开设置 → 挂起游戏（暂停键 + 静音）
}
function closeSettings() {
  if (!settingsOpen) return;
  settingsOpen = false;
  settingsEl.style.display = "none";
  resumeGame();
}
function toggleSettings() { settingsOpen ? closeSettings() : openSettings(); }

// "如果可以，打开设置时暂停/挂起游戏"：注入游戏暂停键 + 静音（尽力而为）
function pauseTargetCode() {
  const e = map.find(x => x.func === "暂停");
  return e && e.target !== "None" && e.target !== "MouseRight" ? e.target : "KeyP";
}
async function suspendGame() {
  await window.native.setMuted(true);
  await window.native.sendKey(pauseTargetCode(), true);
  await window.native.sendKey(pauseTargetCode(), false);
}
async function resumeGame() {
  await window.native.setMuted(false);
  await window.native.sendKey(pauseTargetCode(), true);
  await window.native.sendKey(pauseTargetCode(), false);
}

$("btn-play").onclick = hideMenu;
$("btn-settings").onclick = openSettings;
$("btn-close").onclick = closeSettings;
$("btn-reset").onclick = () => { localStorage.removeItem(LS_KEY); map = loadMap(); buildMapTable(); };
window.native.onToggleSettings(toggleSettings);

// ==================== 映射表 UI ====================

function buildMapTable() {
  const list = $("map-list");
  list.innerHTML = "";
  map.forEach((entry) => {
    const row = document.createElement("div");
    row.className = "map-row";

    const c1 = document.createElement("div"); c1.className = "c1"; c1.textContent = entry.func;

    const c2 = document.createElement("div"); c2.className = "c2";
    const selBtn = document.createElement("select");
    BTN_ORDER.forEach(b => {
      const o = document.createElement("option"); o.value = b; o.textContent = BTN_NAMES[b];
      selBtn.appendChild(o);
    });
    selBtn.value = entry.button;
    selBtn.onchange = () => { entry.button = selBtn.value; saveMap(); };
    c2.appendChild(selBtn);

    const c3 = document.createElement("div"); c3.className = "c3";
    const selTg = document.createElement("select");
    Object.keys(TARGET_NAMES).forEach(t => {
      const o = document.createElement("option"); o.value = t; o.textContent = TARGET_NAMES[t];
      selTg.appendChild(o);
    });
    selTg.value = entry.target;
    selTg.onchange = () => { entry.target = selTg.value; saveMap(); };
    c3.appendChild(selTg);

    row.appendChild(c1); row.appendChild(c2); row.appendChild(c3);
    list.appendChild(row);
  });
}
buildMapTable();

// ==================== 手柄轮询 → 注入 ====================

const AX_ON = 0.55, AX_OFF = 0.35;
const btnState = {};   // 各虚拟按键当前是否按下（边沿检测）

function dispatch(btnId, on) {
  if (settingsOpen) return;                 // 设置打开期间不注入
  const entry = map.find(e => e.button === btnId);
  if (!entry || entry.target === "None") return;
  if (entry.target === "MouseRight") {
    if (on) window.native.mouseRight();
    return;
  }
  window.native.sendKey(entry.target, on);
}

function edge(id, on) {
  const prev = !!btnState[id];
  if (!prev && on) { btnState[id] = true; dispatch(id, true); }
  else if (prev && !on) { btnState[id] = false; dispatch(id, false); }
}

function poll() {
  const gp = navigator.getGamepads && (navigator.getGamepads()[0] || navigator.getGamepads()[1]);
  if (gp) {
    // 按键（b8=Back 为设置专用键，不参与映射）
    for (let i = 0; i <= 15; i++) {
      if (i === 8) continue;
      const b = gp.buttons[i];
      edge("b" + i, !!(b && (b.pressed || b.value > 0.5)));
    }
    // 左摇杆
    edge("stickLeft",  gp.axes[0] < -AX_ON);
    edge("stickRight", gp.axes[0] >  AX_ON);
    edge("stickUp",    gp.axes[1] < -AX_ON);
    edge("stickDown",  gp.axes[1] >  AX_ON);
  }
  requestAnimationFrame(poll);
}
requestAnimationFrame(poll);
