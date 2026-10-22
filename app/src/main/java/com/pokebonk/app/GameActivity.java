package com.pokebonk.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 游戏主界面：Chromium(System WebView) + 沉浸式全屏横屏。
 * 手柄版（flavor=gamepad）额外支持 Xbox 手柄输入：手柄按键 → 注入键盘/鼠标事件到 WebView。
 */
public class GameActivity extends Activity {

    protected static final String HOME_URL = "https://pokebonk.com/";
    protected static final String HOME_HOST = "pokebonk.com";

    /** 手机版 Chrome 的 User-Agent，保证站点按移动端页面渲染 */
    private static final String MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";

    private WebView webView;
    private FrameLayout rootLayout;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private WindowInsetsControllerCompat insetsController;
    private long lastBackPressTime = 0;

    /** 是否启用手柄输入（仅 gamepad flavor 为 true） */
    private boolean gamepadMode;
    private List<GamepadMap.Entry> mapping;

    /** 当前已按下的注入目标键，避免重复 DOWN / 漏 UP */
    private final Set<Integer> heldKeys = new HashSet<>();
    /** 摇杆/十字键(HAT)/扳机等轴状态的按下标记 */
    private final Map<Integer, Boolean> axisState = new HashMap<>();

    private static final float AX_ON = 0.55f;   // 轴触发阈值
    private static final float AX_OFF = 0.35f;  // 轴释放阈值（滞回，防抖动）

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gamepadMode = "gamepad".equals(BuildConfig.FLAVOR);
        if (gamepadMode) reloadMapping();

        setupImmersiveMode();

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);
        webView = new WebView(this);
        webView.setLongClickable(false);          // 防止长按弹出文字选择/上下文菜单
        webView.setHapticFeedbackEnabled(false);
        rootLayout.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(rootLayout);

        setupWebView();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }
    }

    private void reloadMapping() {
        mapping = GamepadMap.load(this);
    }

    /** 沉浸式全屏：隐藏状态栏 + 导航栏，从屏幕边缘上滑可临时呼出（类似王者荣耀） */
    private void setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);                       // 必需：游戏逻辑
        s.setDomStorageEnabled(true);                       // 必需：localStorage 存档
        s.setDatabaseEnabled(true);
        s.setUserAgentString(MOBILE_UA);                    // 手机浏览器 UA
        s.setUseWideViewPort(true);                         // 支持 viewport meta
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);       // 允许自动播放音效
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setGeolocationEnabled(false);

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // 站点内跳转留在应用内，外部链接交给系统浏览器
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                if (!"http".equals(scheme) && !"https".equals(scheme)) {
                    openExternally(uri);
                    return true;
                }
                if (HOME_HOST.equals(uri.getHost()) || HOME_HOST.endsWith("." + HOME_HOST)) {
                    return false; // pokebonk.com 的页面留在 WebView 内
                }
                openExternally(uri);
                return true;
            }
        });

        // 支持 HTML5 全屏（部分 H5 游戏会请求进入全屏）
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                rootLayout.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                rootLayout.removeView(customView);
                customView = null;
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                webView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void openExternally(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
        }
    }

    // ==================== 手柄输入（gamepad 版） ====================

    /** 判断按键事件是否来自手柄（排除设备物理键盘） */
    private static boolean isGamepadSource(KeyEvent e) {
        int s = e.getSource();
        boolean gamepad = (s & InputDevice.SOURCE_GAMEPAD) != 0;
        boolean dpadOnly = (s & InputDevice.SOURCE_DPAD) != 0
                && (s & InputDevice.SOURCE_KEYBOARD) == 0;
        return gamepad || dpadOnly;
    }

    /** 由按键码查映射目标并注入；返回是否消费该事件 */
    private boolean handleGamepadButton(int buttonCode, boolean down) {
        if (!gamepadMode || mapping == null) return false;
        int target = Integer.MIN_VALUE;
        for (GamepadMap.Entry e : mapping) {
            if (e.button == buttonCode) { target = e.target; break; }
        }
        if (target == Integer.MIN_VALUE) return false;  // 不在映射表内
        if (target == GamepadMap.TARGET_NONE) return true; // 显式未绑定 → 拦截但不动作
        if (target == GamepadMap.TARGET_MOUSE_RIGHT) {
            if (down) injectMouseRightClick();           // 鼠标右键事件注入
            return true;
        }
        if (down) {
            if (heldKeys.add(target)) dispatchKey(target, true);
        } else {
            if (heldKeys.remove(target)) dispatchKey(target, false);
        }
        return true;
    }

    private void dispatchKey(int keyCode, boolean down) {
        if (webView == null) return;
        long now = SystemClock.uptimeMillis();
        webView.dispatchKeyEvent(new KeyEvent(now, now,
                down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, keyCode, 0, 0));
    }

    /** 向页面注入"鼠标右键点击"（游戏内"锁定目标"默认绑定的就是右键） */
    private void injectMouseRightClick() {
        if (webView == null) return;
        webView.evaluateJavascript(
            "(function(){var x=window.innerWidth/2,y=window.innerHeight/2;" +
            "var el=document.elementFromPoint(x,y)||document.body;" +
            "var o={bubbles:true,cancelable:true,view:window,button:2,buttons:2,clientX:x,clientY:y};" +
            "try{el.dispatchEvent(new MouseEvent('mousedown',o));}catch(e){}" +
            "try{el.dispatchEvent(new MouseEvent('mouseup',o));}catch(e){}" +
            "try{el.dispatchEvent(new MouseEvent('click',o));}catch(e){}" +
            "})()", null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() == 0 && isGamepadSource(event)
                && handleGamepadButton(keyCode, true)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isGamepadSource(event) && handleGamepadButton(keyCode, false)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (gamepadMode
                && (event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0
                && event.getAction() == MotionEvent.ACTION_MOVE) {
            // 左摇杆
            handleDir(event, MotionEvent.AXIS_X,
                    GamepadMap.BTN_STICK_LEFT, GamepadMap.BTN_STICK_RIGHT);
            handleDir(event, MotionEvent.AXIS_Y,
                    GamepadMap.BTN_STICK_UP, GamepadMap.BTN_STICK_DOWN);
            // 十字键（多数蓝牙手柄以 HAT 轴上报）
            handleDir(event, MotionEvent.AXIS_HAT_X,
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT);
            handleDir(event, MotionEvent.AXIS_HAT_Y,
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN);
            // LT / RT 扳机（不同手柄可能落在不同轴上）
            handleTrigger(event, MotionEvent.AXIS_BRAKE, KeyEvent.KEYCODE_BUTTON_L2);
            handleTrigger(event, MotionEvent.AXIS_LTRIGGER, KeyEvent.KEYCODE_BUTTON_L2);
            handleTrigger(event, MotionEvent.AXIS_GAS, KeyEvent.KEYCODE_BUTTON_R2);
            handleTrigger(event, MotionEvent.AXIS_RTRIGGER, KeyEvent.KEYCODE_BUTTON_R2);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    /** 处理一根双向轴（摇杆方向 / HAT 十字键） */
    private void handleDir(MotionEvent ev, int axis, int negBtn, int posBtn) {
        float v = ev.getAxisValue(axis);
        updateAxisButton(negBtn, v < -AX_ON, v > -AX_OFF);
        updateAxisButton(posBtn, v > AX_ON, v < AX_OFF);
    }

    /** 处理扳机轴 */
    private void handleTrigger(MotionEvent ev, int axis, int buttonCode) {
        float v = ev.getAxisValue(axis);
        if (v > 0.5f) updateAxisButton(buttonCode, true, false);
        else updateAxisButton(buttonCode, false, true);
    }

    /** 带滞回的轴按键状态机 */
    private void updateAxisButton(int button, boolean wantOn, boolean isOff) {
        Boolean cur = axisState.get(button);
        boolean pressed = cur != null && cur;
        if (!pressed && wantOn) {
            axisState.put(button, true);
            handleGamepadButton(button, true);
        } else if (pressed && isOff) {
            axisState.put(button, false);
            handleGamepadButton(button, false);
        }
    }

    /** 退出/切后台时释放所有按住的键，避免"按键卡死" */
    private void releaseAll() {
        for (int kc : new HashSet<>(heldKeys)) {
            dispatchKey(kc, false);
        }
        heldKeys.clear();
        axisState.clear();
    }

    // ==================== 生命周期 / 返回键 ====================

    @Override
    public void onBackPressed() {
        // 1) 正在 HTML5 全屏 → 先退出全屏
        if (customView != null) {
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
            }
            rootLayout.removeView(customView);
            customView = null;
            webView.setVisibility(View.VISIBLE);
            return;
        }
        // 2) 有历史记录 → 网页后退
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        // 3) 手柄版直接返回菜单；原版双击返回键退出（防误触）
        if (gamepadMode) {
            finish();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressTime < 2000) {
            finish();
        } else {
            lastBackPressTime = now;
            Toast.makeText(this, "再按一次返回键退出", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        releaseAll();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gamepadMode) reloadMapping();   // 从设置页返回后刷新映射
        if (webView != null) webView.onResume();
        if (insetsController != null) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
