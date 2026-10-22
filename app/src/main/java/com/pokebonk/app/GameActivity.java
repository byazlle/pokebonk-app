package com.pokebonk.app;

import android.annotation.SuppressLint;
import android.animation.ValueAnimator;
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
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 游戏主界面：Chromium(System WebView) + 沉浸式全屏横屏。
 *
 * v0.2 结构：
 *  - 启动即加载游戏，主菜单为覆盖在 WebView 之上的悬浮层（游戏在背后继续运行）
 *  - 「开始游戏」隐藏菜单；「设置」(仅手柄版) 打开映射设置页
 *  - 手柄专用键 Back(视图键)：游戏中随时呼出设置，打开设置时游戏挂起
 *  - 游戏运行期间切后台/看菜单均不暂停 WebView（需求：后台继续运行）
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

    /** 主菜单悬浮层 */
    private View menuOverlay;
    private boolean menuVisible = true;
    /** 标记本次 onPause 是否因打开设置引起（设置打开 → 挂起游戏） */
    private boolean settingsRequested = false;
    /** 标题浮动动画（菜单显示期间循环） */
    private ValueAnimator titleFloat;

    /** 当前已按下的注入目标键，避免重复 DOWN / 漏 UP */
    private final Set<Integer> heldKeys = new HashSet<>();

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
        setupMenuOverlay();

        if (savedInstanceState != null) {
            menuVisible = savedInstanceState.getBoolean("menuVisible", true);
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }
        menuOverlay.setVisibility(menuVisible ? View.VISIBLE : View.GONE);
        if (menuVisible) playEntrance();   // 首次进入：菜单元素错峰入场
    }

    private void reloadMapping() {
        mapping = GamepadMap.load(this);
    }

    /** 沉浸式全屏：隐藏状态栏 + 导航栏，从屏幕边缘上滑可临时呼出 */
    private void setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /** 主菜单悬浮层：游戏在背后持续运行 */
    private void setupMenuOverlay() {
        menuOverlay = getLayoutInflater().inflate(R.layout.menu_overlay, rootLayout, false);

        String appName = getString(R.string.app_name);
        ((TextView) menuOverlay.findViewById(R.id.tv_version)).setText("v0.2 · " + appName);

        Button btnSettings = menuOverlay.findViewById(R.id.btn_settings);
        TextView hint = menuOverlay.findViewById(R.id.tv_hint);
        if (gamepadMode) {
            btnSettings.setVisibility(View.VISIBLE);
            btnSettings.setOnClickListener(v -> openSettings());
            hint.setText("游戏内按手柄 Back(视图)键 随时打开设置");
        } else {
            btnSettings.setVisibility(View.GONE);
            hint.setText("触屏操作 · 需要 Xbox 手柄支持请安装手柄版");
        }

        menuOverlay.findViewById(R.id.btn_play).setOnClickListener(v -> hideMenu());
        rootLayout.addView(menuOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void hideMenu() {
        menuVisible = false;
        if (titleFloat != null) titleFloat.cancel();
        // 推进转场：镜头从主菜单"推入"游戏画面（放大 + 淡出）
        menuOverlay.setPivotX(menuOverlay.getWidth() / 2f);
        menuOverlay.setPivotY(menuOverlay.getHeight() / 2f);
        menuOverlay.animate()
                .scaleX(3.2f).scaleY(3.2f).alpha(0f)
                .setDuration(520)
                .setInterpolator(new AccelerateInterpolator(1.6f))
                .withEndAction(() -> {
                    menuOverlay.setVisibility(View.GONE);
                    menuOverlay.setScaleX(1f);
                    menuOverlay.setScaleY(1f);
                    menuOverlay.setAlpha(1f);
                })
                .start();
    }

    private void showMenu() {
        menuVisible = true;
        menuOverlay.setVisibility(View.VISIBLE);
        // 返回菜单：轻微缩放淡入
        menuOverlay.setScaleX(1.12f);
        menuOverlay.setScaleY(1.12f);
        menuOverlay.setAlpha(0f);
        menuOverlay.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(380)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        playEntrance();
    }

    /** 菜单元素错峰入场（上浮 + 淡入） */
    private void playEntrance() {
        long delay = 0;
        int[] ids = {R.id.tv_title, R.id.tv_version, R.id.btn_play, R.id.btn_settings, R.id.tv_hint};
        for (int id : ids) {
            View v = menuOverlay.findViewById(id);
            if (v == null || v.getVisibility() == View.GONE) continue;
            v.setAlpha(0f);
            v.setTranslationY(44f);
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(delay)
                    .setDuration(430)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            delay += 90;
        }
        // 标题缓慢上下浮动
        View title = menuOverlay.findViewById(R.id.tv_title);
        if (titleFloat != null) titleFloat.cancel();
        titleFloat = ValueAnimator.ofFloat(0f, -12f, 0f);
        titleFloat.setDuration(2800);
        titleFloat.setRepeatCount(ValueAnimator.INFINITE);
        titleFloat.setInterpolator(new DecelerateInterpolator());
        titleFloat.addUpdateListener(a -> title.setTranslationY((Float) a.getAnimatedValue()));
        titleFloat.setStartDelay(600);
        titleFloat.start();
    }

    private void openSettings() {
        settingsRequested = true;   // 设置打开期间挂起游戏（见 onPause）
        startActivity(new Intent(this, MappingActivity.class));
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
        if (!gamepadMode || mapping == null || menuVisible) return false;
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
        if (gamepadMode && isGamepadSource(event) && event.getRepeatCount() == 0) {
            // 专用键：Back(视图键) → 打开设置
            if (keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
                openSettings();
                return true;
            }
            // 菜单中：A 键 = 开始游戏；其余手柄键不注入
            if (menuVisible) {
                if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                    hideMenu();
                }
                return true;
            }
            if (handleGamepadButton(keyCode, true)) return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (gamepadMode && isGamepadSource(event)) {
            if (keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) return true;
            if (menuVisible) return true;
            if (handleGamepadButton(keyCode, false)) return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // 菜单显示期间不处理摇杆（防止误注入）
        if (gamepadMode && !menuVisible
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

    /** 轴向状态缓存（带滞回） */
    private final Set<Integer> axisPressed = new HashSet<>();

    /** 处理一根双向轴（摇杆方向 / HAT 十字键） */
    private void handleDir(MotionEvent ev, int axis, int negBtn, int posBtn) {
        float v = ev.getAxisValue(axis);
        updateAxisButton(negBtn, v < -0.55f, v > -0.35f);
        updateAxisButton(posBtn, v > 0.55f, v < 0.35f);
    }

    /** 处理扳机轴 */
    private void handleTrigger(MotionEvent ev, int axis, int buttonCode) {
        float v = ev.getAxisValue(axis);
        if (v > 0.5f) updateAxisButton(buttonCode, true, false);
        else updateAxisButton(buttonCode, false, true);
    }

    /** 带滞回的轴按键状态机 */
    private void updateAxisButton(int button, boolean wantOn, boolean isOff) {
        boolean pressed = axisPressed.contains(button);
        if (!pressed && wantOn) {
            axisPressed.add(button);
            handleGamepadButton(button, true);
        } else if (pressed && isOff) {
            axisPressed.remove(button);
            handleGamepadButton(button, false);
        }
    }

    /** 释放所有按住的键，避免"按键卡死" */
    private void releaseAll() {
        for (int kc : new HashSet<>(heldKeys)) {
            dispatchKey(kc, false);
        }
        heldKeys.clear();
        axisPressed.clear();
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
        // 2) 游戏进行中 → 回到主菜单（游戏在背后继续运行）
        if (!menuVisible) {
            showMenu();
            return;
        }
        // 3) 有历史记录 → 网页后退
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        // 4) 主菜单下双击返回键退出（防误触）
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
        // 仅在"打开设置"时挂起游戏；其余情况（看菜单/切后台）游戏继续运行
        if (settingsRequested && webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gamepadMode) reloadMapping();   // 从设置页返回后刷新映射
        if (webView != null) webView.onResume();  // 设置关闭 → 恢复游戏
        settingsRequested = false;
        if (insetsController != null) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("menuVisible", menuVisible);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (titleFloat != null) titleFloat.cancel();
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
