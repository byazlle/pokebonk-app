package com.pokebonk.app;

import android.content.Context;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 手柄按键映射模型（Xbox 手柄为标准）。
 * 数据结构：每个功能条目 = { 游戏内功能名, 手柄按键码, 绑定目标键码 }。
 * 映射持久化到 SharedPreferences（JSON）。
 */
public final class GamepadMap {

    /** 特殊目标：注入鼠标右键事件（游戏内"锁定目标"默认绑定右键） */
    public static final int TARGET_MOUSE_RIGHT = -1;
    /** 未绑定 */
    public static final int TARGET_NONE = 0;

    /** 左摇杆方向伪按键码（由摇杆轴触发，不会以 KeyEvent 形式出现） */
    public static final int BTN_STICK_UP = -101;
    public static final int BTN_STICK_DOWN = -102;
    public static final int BTN_STICK_LEFT = -103;
    public static final int BTN_STICK_RIGHT = -104;

    /** 一条映射：功能名 / 手柄按键码 / 默认目标键码 */
    public static class Entry {
        public final String func;     // 游戏内对应功能
        public int button;            // 当前映射的手柄按键
        public final int defTarget;   // 出厂默认目标键
        public int target;            // 当前绑定目标键

        Entry(String func, int button, int defTarget, int target) {
            this.func = func; this.button = button;
            this.defTarget = defTarget; this.target = target;
        }
    }

    /** 出厂默认映射表（v0.2；Back(视图)键为专用设置键，不参与游戏映射） */
    private static final String[][] DEFAULTS = {
        // 功能名, 手柄按键码(字符串), 目标键名
        {"移动·上",            String.valueOf(BTN_STICK_UP),                       "W"},
        {"移动·下",            String.valueOf(BTN_STICK_DOWN),                     "S"},
        {"移动·左",            String.valueOf(BTN_STICK_LEFT),                     "A"},
        {"移动·右",            String.valueOf(BTN_STICK_RIGHT),                    "D"},
        {"跳跃",               String.valueOf(KeyEvent.KEYCODE_BUTTON_A),           "空格"},
        {"滑铲（空中：俯冲）",  String.valueOf(KeyEvent.KEYCODE_BUTTON_B),           "Shift"},
        {"互动",               String.valueOf(KeyEvent.KEYCODE_BUTTON_X),           "E"},
        {"终极技",             String.valueOf(KeyEvent.KEYCODE_BUTTON_Y),           "R"},
        {"锁定目标",           String.valueOf(KeyEvent.KEYCODE_BUTTON_L1),          "鼠标右键"},
        {"自动/手动瞄准",       String.valueOf(KeyEvent.KEYCODE_BUTTON_R1),          "Q"},
        {"自动终极技能",        String.valueOf(KeyEvent.KEYCODE_DPAD_UP),            "Y"},
        {"暂停",               String.valueOf(KeyEvent.KEYCODE_DPAD_DOWN),          "P"},
        {"刷新（升级时）",      String.valueOf(KeyEvent.KEYCODE_DPAD_LEFT),          "F"},
        {"跳过（升级时）",      String.valueOf(KeyEvent.KEYCODE_DPAD_RIGHT),         "X"},
        {"放逐（升级时）",      String.valueOf(KeyEvent.KEYCODE_BUTTON_THUMBL),      "Z"},
        {"大地图",             String.valueOf(KeyEvent.KEYCODE_BUTTON_L2),          "M"},
        {"自动前进",           String.valueOf(KeyEvent.KEYCODE_BUTTON_R2),          "T"},
        {"开始(菜单键)",        String.valueOf(KeyEvent.KEYCODE_BUTTON_START),       "未绑定"},
        {"L3右摇杆按下(备用)",  String.valueOf(KeyEvent.KEYCODE_BUTTON_THUMBR),      "未绑定"},
    };

    private static final String PREFS = "gamepad_mapping";
    private static final String KEY = "entries_v1";

    private GamepadMap() {}

    /** 加载映射（默认表 + 用户修改覆盖） */
    public static List<Entry> load(Context ctx) {
        List<Entry> list = new ArrayList<>();
        for (String[] d : DEFAULTS) {
            list.add(new Entry(d[0], Integer.parseInt(d[1]), targetFromName(d[2]), targetFromName(d[2])));
        }
        try {
            String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
            if (raw != null) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length() && i < list.size(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Entry e = list.get(i);
                    e.button = o.optInt("b", e.button);
                    e.target = o.optInt("t", e.target);
                }
            }
        } catch (Exception ignored) { }
        return list;
    }

    /** 保存映射 */
    public static void save(Context ctx, List<Entry> entries) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : entries) {
                JSONObject o = new JSONObject();
                o.put("b", e.button);
                o.put("t", e.target);
                arr.put(o);
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
               .edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** 恢复默认 */
    public static void reset(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    /** 由手柄按键码查当前目标键；未绑定返回 null（0 表示显式未绑定） */
    public static int targetFor(Context ctx, int button) {
        for (Entry e : load(ctx)) {
            if (e.button == button) return e.target;
        }
        return Integer.MIN_VALUE; // 该按键不在映射表中
    }

    // ---------- 名称 <-> 键码 ----------

    /** 目标键名 -> Android 键码 */
    public static int targetFromName(String name) {
        if (name == null) return TARGET_NONE;
        switch (name) {
            case "鼠标右键": return TARGET_MOUSE_RIGHT;
            case "空格":  return KeyEvent.KEYCODE_SPACE;
            case "Shift": return KeyEvent.KEYCODE_SHIFT_LEFT;
            case "Esc":   return KeyEvent.KEYCODE_ESCAPE;
            case "Enter": return KeyEvent.KEYCODE_ENTER;
            case "↑":     return KeyEvent.KEYCODE_DPAD_UP;
            case "↓":     return KeyEvent.KEYCODE_DPAD_DOWN;
            case "←":     return KeyEvent.KEYCODE_DPAD_LEFT;
            case "→":     return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "未绑定": return TARGET_NONE;
        }
        if (name.length() == 1) {
            char c = Character.toUpperCase(name.charAt(0));
            if (c >= 'A' && c <= 'Z') return KeyEvent.keyCodeFromString("KEYCODE_" + c);
            if (c >= '1' && c <= '9') return KeyEvent.keyCodeFromString("KEYCODE_" + c);
        }
        return TARGET_NONE;
    }

    /** Android 键码 -> 显示名 */
    public static String targetName(int code) {
        if (code == TARGET_MOUSE_RIGHT) return "鼠标右键";
        if (code == TARGET_NONE) return "未绑定";
        switch (code) {
            case KeyEvent.KEYCODE_SPACE: return "空格";
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return "Shift";
            case KeyEvent.KEYCODE_ESCAPE: return "Esc";
            case KeyEvent.KEYCODE_ENTER: return "Enter";
            case KeyEvent.KEYCODE_DPAD_UP: return "↑";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "↓";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "←";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "→";
        }
        String n = KeyEvent.keyCodeToString(code); // 形如 KEYCODE_W / KEYCODE_1
        if (n.startsWith("KEYCODE_")) n = n.substring(8);
        return n;
    }

    /** 手柄按键码 -> 显示名 */
    public static String buttonName(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "LB";
            case KeyEvent.KEYCODE_BUTTON_R1: return "RB";
            case KeyEvent.KEYCODE_BUTTON_L2: return "LT";
            case KeyEvent.KEYCODE_BUTTON_R2: return "RT";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3(左摇杆按下)";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3(右摇杆按下)";
            case KeyEvent.KEYCODE_BUTTON_START: return "Start(菜单)";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "Back(视图)";
            case KeyEvent.KEYCODE_DPAD_UP: return "十字键·上";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "十字键·下";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "十字键·左";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "十字键·右";
            case BTN_STICK_UP: return "左摇杆·上";
            case BTN_STICK_DOWN: return "左摇杆·下";
            case BTN_STICK_LEFT: return "左摇杆·左";
            case BTN_STICK_RIGHT: return "左摇杆·右";
        }
        return "按键" + code;
    }

    /** 可选手柄按键列表（供设置界面挑选；Back(视图)键为专用设置键，不列出） */
    public static List<Integer> allButtons() {
        List<Integer> l = new ArrayList<>();
        l.add((int) KeyEvent.KEYCODE_BUTTON_A);
        l.add((int) KeyEvent.KEYCODE_BUTTON_B);
        l.add((int) KeyEvent.KEYCODE_BUTTON_X);
        l.add((int) KeyEvent.KEYCODE_BUTTON_Y);
        l.add((int) KeyEvent.KEYCODE_BUTTON_L1);
        l.add((int) KeyEvent.KEYCODE_BUTTON_R1);
        l.add((int) KeyEvent.KEYCODE_BUTTON_L2);
        l.add((int) KeyEvent.KEYCODE_BUTTON_R2);
        l.add((int) KeyEvent.KEYCODE_BUTTON_THUMBL);
        l.add((int) KeyEvent.KEYCODE_BUTTON_THUMBR);
        l.add((int) KeyEvent.KEYCODE_BUTTON_START);
        l.add((int) KeyEvent.KEYCODE_DPAD_UP);
        l.add((int) KeyEvent.KEYCODE_DPAD_DOWN);
        l.add((int) KeyEvent.KEYCODE_DPAD_LEFT);
        l.add((int) KeyEvent.KEYCODE_DPAD_RIGHT);
        l.add(BTN_STICK_UP);
        l.add(BTN_STICK_DOWN);
        l.add(BTN_STICK_LEFT);
        l.add(BTN_STICK_RIGHT);
        return l;
    }

    /** 可选目标键列表（供设置界面挑选） */
    public static List<String> allTargetNames() {
        List<String> l = new ArrayList<>();
        String[] letters = {"W","A","S","D","E","R","Q","T","Y","P","M","F","X","Z","C","V","B","N"};
        for (String s : letters) l.add(s);
        l.add("空格"); l.add("Shift"); l.add("Esc"); l.add("Enter");
        l.add("↑"); l.add("↓"); l.add("←"); l.add("→");
        l.add("1"); l.add("2"); l.add("3"); l.add("4");
        l.add("鼠标右键"); l.add("未绑定");
        return l;
    }
}
