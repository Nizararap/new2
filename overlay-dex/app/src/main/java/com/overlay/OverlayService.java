package com.overlay;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

import com.overlay.service.NativeSender;
import com.overlay.ui.OverlayView;

public class OverlayService extends Service {

    private static OverlayService activeInstance;

    private WindowManager windowManager;
    private OverlayView overlayView;
    private RadarView radarView;
    private LoginView loginView;
    private KeyAuthManager authManager;

    private Handler handler;
    private Runnable connectionChecker;

    private boolean isOverlayShown = false;
    private boolean isLoginShown = false;
    private boolean isLoggedOut = false;

    private static final int RETRY_DELAY_MS = 2000;
    private static final long KEY_CHECK_INTERVAL_MS = 2 * 60 * 60 * 1000L;
    private long lastKeyCheckTime = 0;

    private int realScreenW, realScreenH;

    @Override
    public void onCreate() {
        super.onCreate();

        if (activeInstance != null && activeInstance != this) {
            try {
                activeInstance.forceCleanup();
            } catch (Exception ignored) {}
        }
        activeInstance = this;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        authManager = new KeyAuthManager(this);
        fetchRealScreenSize();

        String savedKey = authManager.getPlainKey();
        if (savedKey != null) {
            authManager.validateKey(savedKey, new KeyAuthManager.AuthCallback() {
                @Override
                public void onSuccess() {
                    startConnectionChecker();
                }
                @Override
                public void onFailure(String reason) {
                    if (reason.startsWith("NET_ERROR")) {
                        if (authManager.isKeyValid()) {
                            startConnectionChecker();
                        } else {
                            authManager.logout();
                            showLoginUI();
                        }
                    } else {
                        authManager.logout();
                        showLoginUI();
                        Toast.makeText(OverlayService.this, reason, Toast.LENGTH_LONG).show();
                    }
                }
                @Override
                public void onUpdateInfo(String info) { }
            });
        } else {
            showLoginUI();
        }
    }

    private void fetchRealScreenSize() {
        DisplayMetrics realMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(realMetrics);
        realScreenW = realMetrics.widthPixels;
        realScreenH = realMetrics.heightPixels;
    }

    public void forceCleanup() {
        if (handler != null && connectionChecker != null) {
            handler.removeCallbacks(connectionChecker);
        }
        hideOverlayUI();
        hideLoginUI();
        stopSelf();
    }

    private void showLoginUI() {
        if (isLoginShown) return;

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.CENTER;

        loginView = new LoginView(this, windowManager, lp, () -> {
            hideLoginUI();
            isLoggedOut = false;
            startConnectionChecker();
        });

        try {
            windowManager.addView(loginView, lp);
            isLoginShown = true;
        } catch (Exception ignored) {}
    }

    private void hideLoginUI() {
        if (isLoginShown && loginView != null) {
            try { windowManager.removeView(loginView); } catch (Exception ignored) {}
            isLoginShown = false;
            loginView = null;
        }
    }

    private void startConnectionChecker() {
        if (connectionChecker != null) handler.removeCallbacks(connectionChecker);
        connectionChecker = new Runnable() {
            @Override
            public void run() {
                if (isLoggedOut) return;

                if (tryConnectToNative()) {
                    if (!isOverlayShown) {
                        showOverlayUI();
                        final SharedPreferences modPrefs = getSharedPreferences("mod_settings", MODE_PRIVATE);
                        authManager.fetchRemoteConfig(modPrefs, () -> {
                            if (overlayView != null) overlayView.onRemoteConfigUpdated();
                            NativeSender.sendConfigToCpp(modPrefs, authManager, realScreenW, realScreenH);
                        });
                    }
                } else {
                    if (isOverlayShown) {
                        hideOverlayUI();
                    }
                }

                long now = System.currentTimeMillis();
                if (now - lastKeyCheckTime >= KEY_CHECK_INTERVAL_MS) {
                    lastKeyCheckTime = now;
                    performKeyCheck();
                }

                handler.postDelayed(this, RETRY_DELAY_MS);
            }
        };
        handler.post(connectionChecker);
    }

    private boolean tryConnectToNative() {
        try {
            LocalSocket socket = new LocalSocket();
            socket.connect(new LocalSocketAddress("and.sys.audio.config", LocalSocketAddress.Namespace.ABSTRACT));
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void showOverlayUI() {
        if (isOverlayShown) return;

        isOverlayShown = true;
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams radarParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            radarParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        radarView = new RadarView(this);
        windowManager.addView(radarView, radarParams);

        WindowManager.LayoutParams menuParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.TOP | Gravity.START;
        overlayView = new OverlayView(this, windowManager, menuParams, radarView);
        windowManager.addView(overlayView, menuParams);
    }

    private void hideOverlayUI() {
        if (!isOverlayShown) return;
        isOverlayShown = false;

        if (overlayView != null) {
            try {
                if (overlayView.isAttachedToWindow()) {
                    windowManager.removeView(overlayView);
                }
            } catch (Exception ignored) {}
            overlayView = null;
        }
        if (radarView != null) {
            try {
                radarView.destroy();
                if (radarView.isAttachedToWindow()) {
                    windowManager.removeView(radarView);
                }
            } catch (Exception ignored) {}
            radarView = null;
        }
    }

    private void performKeyCheck() {
        String savedKey = authManager.getPlainKey();
        if (savedKey == null) return;

        authManager.validateKey(savedKey, new KeyAuthManager.AuthCallback() {
            @Override
            public void onSuccess() {
                final SharedPreferences modPrefs = getSharedPreferences("mod_settings", MODE_PRIVATE);
                authManager.fetchRemoteConfig(modPrefs, () -> {
                    if (overlayView != null) overlayView.onRemoteConfigUpdated();
                    NativeSender.sendConfigToCpp(modPrefs, authManager, realScreenW, realScreenH);
                });
            }

            @Override
            public void onFailure(String reason) {
                if (reason.startsWith("NET_ERROR")) return;

                isLoggedOut = true;
                authManager.logout();
                disableAllFeatures();

                if (isOverlayShown) {
                    hideOverlayUI();
                }

                if (!isLoginShown) {
                    showLoginUI();
                }

                Toast.makeText(OverlayService.this, "Key expired!.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onUpdateInfo(String info) { }
        });
    }

    private void disableAllFeatures() {
        SharedPreferences prefs = getSharedPreferences("mod_settings", MODE_PRIVATE);
        prefs.edit()
            .putBoolean("radar_enable", false)
            .putBoolean("aimbot_enable", false)
            .putBoolean("lock_hero_enable", false)
            .putBoolean("retri_buff", false)
            .putBoolean("retri_lord", false)
            .putBoolean("retri_turtle", false)
            .putBoolean("retri_litho", false)
            .putInt("ling_mode", 0)
            .putString("selected_combo", "none")
            .apply();

        NativeSender.sendConfigToCpp(prefs, authManager, realScreenW, realScreenH);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        forceCleanup();
        activeInstance = null;
    }
}
