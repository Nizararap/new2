package com.overlay.service;

import android.content.SharedPreferences;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import com.overlay.KeyAuthManager;
import com.overlay.auth.FeatureManager;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NativeSender {
    private static final ExecutorService socketExecutor = Executors.newSingleThreadExecutor();

    private static boolean isFeatureEnabled(SharedPreferences prefs, String prefKey) {
        String featureKey = FeatureManager.mapKeyToFeature(prefKey);
        if (FeatureManager.isFeatureLocked(prefs, featureKey)) return false;
        return prefs.getBoolean(prefKey, false);
    }

    public static void sendConfigToCpp(SharedPreferences prefs, KeyAuthManager authManager, int realScreenW, int realScreenH) {
        socketExecutor.execute(() -> {
            try {
                LocalSocket socket = new LocalSocket();
                socket.connect(new LocalSocketAddress("and.sys.audio.config", LocalSocketAddress.Namespace.ABSTRACT));
                OutputStream out = socket.getOutputStream();
                
                ByteBuffer bb = ByteBuffer.allocate(136);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                
                bb.putInt(0x4D4C4242); 
                long expirySeconds = authManager.getExpiryTimestamp();
                bb.putLong(expirySeconds * 1000); 

                boolean lingLocked = FeatureManager.isFeatureLocked(prefs, "ling");
                int lingMode = lingLocked ? 0 : prefs.getInt("ling_mode", 0);
                int lingManual = (lingMode == 1) ? 1 : 0;
                int lingAuto   = (lingMode == 2) ? 1 : 0;

                boolean comboLocked = FeatureManager.isFeatureLocked(prefs, "combo");
                String selectedCombo = comboLocked ? "none" : prefs.getString("selected_combo", "none");
                int activeCombo = 0;
                
                if (selectedCombo.contains("gusion")) activeCombo = 1;
                else if (selectedCombo.contains("kadita")) activeCombo = 2;
                else if (selectedCombo.contains("beatrix")) activeCombo = 3;
                else if (selectedCombo.contains("xavier")) activeCombo = 5;
                else if (selectedCombo.contains("selena")) activeCombo = 6;

                bb.putInt(isFeatureEnabled(prefs, "aimbot_enable") ? 1 : 0);
                bb.putInt(lingManual);
                bb.putInt(lingAuto);
                bb.putInt(activeCombo);
                bb.putInt(prefs.getInt("aimbot_target", 0));
                bb.putFloat(prefs.getFloat("aimbot_fov", 200f));
                
                bb.putInt(isFeatureEnabled(prefs, "retri_buff") ? 1 : 0);
                bb.putInt(isFeatureEnabled(prefs, "retri_lord") ? 1 : 0);
                bb.putInt(isFeatureEnabled(prefs, "retri_turtle") ? 1 : 0);
                bb.putInt(isFeatureEnabled(prefs, "retri_litho") ? 1 : 0);
                bb.putInt(isFeatureEnabled(prefs, "lock_hero_enable") ? 1 : 0);
                
                String heroName = prefs.getString("locked_hero_name", "");
                byte[] nameBytes = heroName.getBytes();
                byte[] finalName = new byte[32];
                System.arraycopy(nameBytes, 0, finalName, 0, Math.min(nameBytes.length, 31));
                bb.put(finalName);
        
                bb.putFloat(prefs.getFloat("drone_view", 0f));
                bb.putInt(isFeatureEnabled(prefs, "esp_circle") ? 1 : 0);
                bb.putInt(isFeatureEnabled(prefs, "esp_cooldown") ? 1 : 0);
                
                bb.putFloat(prefs.getFloat("aimbot_predict", 1.0f));
                
                int fpsIndex = prefs.getInt("fps_index", 0);
                int[] fpsValues = {0, 30, 45, 60, 90, 120};
                bb.putInt(fpsValues[fpsIndex]);
                
                int priorityIndex = prefs.getInt("thread_priority", 2);
                int[] priorityValues = {0, 1, 2, 4};
                bb.putInt(priorityValues[priorityIndex]);
                
                boolean doQuit = prefs.getBoolean("action_quit", false);
                boolean doClearCache = prefs.getBoolean("action_clear_cache", false);
                
                bb.putInt(doQuit ? 1 : 0);
                bb.putInt(doClearCache ? 1 : 0);

                float resScale = prefs.getFloat("res_scale", 100f) / 100f; 
                int targetW = 0;
                int targetH = 0;
                
                if (resScale < 1.0f) {
                    targetW = (int) (realScreenW * resScale);
                    targetH = (int) (realScreenH * resScale);
                }
                
                bb.putInt(targetW);
                bb.putInt(targetH);

                bb.putInt(isFeatureEnabled(prefs, "disable_shadows") ? 1 : 0);
                bb.putInt(isFeatureEnabled(prefs, "disable_aa") ? 1 : 0);

                out.write(bb.array());
                out.flush();
                
                if (doQuit || doClearCache) {
                    prefs.edit()
                         .putBoolean("action_quit", false)
                         .putBoolean("action_clear_cache", false)
                         .apply();
                }
                socket.close();
            } catch (Exception ignored) {}
        });
    }
}