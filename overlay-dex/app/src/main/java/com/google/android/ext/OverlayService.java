package com.google.android.ext;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;
import android.content.SharedPreferences;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class OverlayService extends Service {
    private WindowManager windowManager;
    private OverlayView overlayView;
    private RadarView radarView;
    private LoginView loginView;
    private KeyAuthManager authManager;
    private SHMManager shmManager;

    private Handler handler;
    private Runnable connectionChecker;

    private boolean isOverlayShown = false;
    private boolean isLoginShown = false;
    private boolean isLoggedOut = false;
    
    private static final int RETRY_DELAY_MS = 2000;
    // Rekomendasi: 2 Jam (Sangat aman dari limit GitHub, performa tetap adem, dan kalau ada user nakal gak kelamaan main gratisnya)
private static final long KEY_CHECK_INTERVAL_MS = 2 * 60 * 60 * 1000L;
    private long lastKeyCheckTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();

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
        shmManager = new SHMManager();

// Validasi ulang jika ada key tersimpan, jika tidak langsung login
        String savedKey = authManager.getPlainKey();
        if (savedKey != null) {
            // Validasi ulang ke server (sekali saat startup)
            authManager.validateKey(savedKey, new KeyAuthManager.AuthCallback() {
                @Override
                public void onSuccess() {
                    startConnectionChecker();
                }
                @Override
                public void onFailure(String reason) {
                    if (reason.startsWith("NET_ERROR")) {
                        // JANGAN logout jika hanya jaringan lambat saat virtual space baru dibuka
                        if (authManager.isKeyValid()) {
                            // Masuk menggunakan sisa waktu lokal (Offline Grace Period)
                            startConnectionChecker(); 
                        } else {
                            authManager.logout();
                            showLoginUI();
                        }
                    } else {
                        // Key terbukti expired atau dihapus dari github
                        authManager.logout();
                        showLoginUI();
                        Toast.makeText(OverlayService.this, reason, Toast.LENGTH_LONG).show();
                    }
                }
            });
        } else {
            showLoginUI();
        }
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
            // Setelah login sukses, tidak perlu validasi lagi, langsung start connection
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

            // Cek koneksi ke native
            if (tryConnectToNative()) {
                if (!isOverlayShown) {
                    showOverlayUI();
                }
            } else {
                if (isOverlayShown) {
                    hideOverlayUI();
                }
            }

            // Periodic check key ke server (setiap interval)
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
        return shmManager.connect();
    }

    private void showOverlayUI() {
        if (isOverlayShown) return;

        isOverlayShown = true;
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // --- PERBAIKAN RADAR BORDER MISMATCH (Ada di bawah) ---
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
        
        // Izinkan Radar tembus area Poni (Notch) layar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            radarParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        radarView = new RadarView(this);
        windowManager.addView(radarView, radarParams);

        // Menu
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
        try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
        overlayView = null;
    }
    if (radarView != null) {
        try {
            radarView.destroy();
            windowManager.removeView(radarView);
        } catch (Exception ignored) {}
        radarView = null;
    }
}
    
    private void performKeyCheck() {
    String savedKey = authManager.getPlainKey();
    if (savedKey == null) return;

    authManager.validateKey(savedKey, new KeyAuthManager.AuthCallback() {
        @Override
        public void onSuccess() { }

        @Override
        public void onFailure(String reason) {
            if (reason.startsWith("NET_ERROR")) return;
            
            isLoggedOut = true;
            authManager.logout();
            disableAllFeatures();     // matikan semua fitur
            
            if (isOverlayShown) {
                hideOverlayUI();
            }
            
            if (!isLoginShown) {
                showLoginUI();
            }
            
            Toast.makeText(OverlayService.this, 
                "Key expired! All features disabled.", 
                Toast.LENGTH_LONG).show();
        }
    });
}

private void disableAllFeatures() {
    SharedPreferences prefs = getSharedPreferences("mod_settings", MODE_PRIVATE);
    SharedPreferences.Editor editor = prefs.edit();
    editor.putBoolean("radar_enable", false);
    editor.putBoolean("aimbot_enable", false);
    editor.putBoolean("lock_hero_enable", false);
    editor.putBoolean("retri_buff", false);
    editor.putBoolean("retri_lord", false);
    editor.putBoolean("retri_turtle", false);
    editor.putBoolean("retri_litho", false);
    editor.putInt("ling_mode", 0);
    editor.putString("selected_combo", "none");
    editor.apply();
    
    sendConfigToCpp(prefs);
}

private void sendConfigToCpp(SharedPreferences prefs) {
    ByteBuffer bb = ByteBuffer.allocate(88);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    bb.putInt(0x4D4C4242); // MAGIC
    long expirySeconds = authManager.getExpiryTimestamp();
    bb.putLong(expirySeconds * 1000);
    
    int lingMode = prefs.getInt("ling_mode", 0);
    int lingManual = (lingMode == 1) ? 1 : 0;
    int lingAuto = (lingMode == 2) ? 1 : 0;
    String selectedCombo = prefs.getString("selected_combo", "none");
    int activeCombo = 0;
    if (selectedCombo.contains("gusion")) activeCombo = 1;
    else if (selectedCombo.contains("kadita")) activeCombo = 2;
    else if (selectedCombo.contains("beatrix")) activeCombo = 3;
    else if (selectedCombo.contains("kimmy")) activeCombo = 4;
    
    bb.putInt(prefs.getBoolean("aimbot_enable", false) ? 1 : 0);
    bb.putInt(lingManual);
    bb.putInt(lingAuto);
    bb.putInt(activeCombo);
    bb.putInt(prefs.getInt("aimbot_target", 0));
    bb.putFloat(prefs.getFloat("aimbot_fov", 200f));
    bb.putInt(prefs.getBoolean("retri_buff", false) ? 1 : 0);
    bb.putInt(prefs.getBoolean("retri_lord", false) ? 1 : 0);
    bb.putInt(prefs.getBoolean("retri_turtle", false) ? 1 : 0);
    bb.putInt(prefs.getBoolean("retri_litho", false) ? 1 : 0);
    bb.putInt(prefs.getBoolean("lock_hero_enable", false) ? 1 : 0);
    
    String heroName = prefs.getString("locked_hero_name", "");
    byte[] nameBytes = heroName.getBytes();
    byte[] finalName = new byte[32];
    System.arraycopy(nameBytes, 0, finalName, 0, Math.min(nameBytes.length, 31));
    bb.put(finalName);
    
    shmManager.writeConfig(bb.array());
}

    @Override
    public int onStartCommand(Intent i, int f, int s) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(connectionChecker);
        hideOverlayUI();
        hideLoginUI();
    }
}