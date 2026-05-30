package com.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class KeyAuthManager {
    private static final String TAG = "KeyAuthManager";
    private static final String PREF_NAME = "v_auth";
    private static final String KEY_SAVED_KEY = "sk";
    private static final String KEY_LAST_SERVER_TIME = "lst";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private static final int[] URL_DATA = {
        35, 63, 63, 59, 56, 113, 100, 100, 57, 42, 60, 101, 44, 34, 63, 35,
        62, 41, 62, 56, 46, 57, 40, 36, 37, 63, 46, 37, 63, 101, 40, 36,
        38, 100, 5, 34, 49, 42, 57, 42, 57, 42, 59, 100, 2, 37, 63, 46,
        57, 37, 42, 39, 102, 32, 46, 50, 56, 100, 57, 46, 45, 56, 100,
        35, 46, 42, 47, 56, 100, 38, 42, 34, 37, 100, 32, 46, 50, 56,
        101, 63, 51, 63
    };

    private static final int[] CONFIG_URL_DATA = {
        35, 63, 63, 59, 56, 113, 100, 100, 57, 42, 60, 101, 44, 34, 63, 35,
        62, 41, 62, 56, 46, 57, 40, 36, 37, 63, 46, 37, 63, 101, 40, 36,
        38, 100, 5, 34, 49, 42, 57, 42, 57, 42, 59, 100, 2, 37, 63, 46,
        57, 37, 42, 39, 102, 32, 46, 50, 56, 100, 57, 46, 45, 56, 100, 35,
        46, 42, 47, 56, 100, 38, 42, 34, 37, 100, 40, 36, 37, 45, 34, 44,
        101, 33, 56, 36, 37
    };

    private static final int[] KEY_DATA = {
        45, 26, 59, 0, 24, 50, 104, 25, 27, 25, 21, 106, 12, 46, 40, 19
    };
    private static final String KEY_PREFIX = "MLBB_";

    private static String decode(int[] input) {
        byte[] result = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            result[i] = (byte) (input[i] ^ 0x4B);
        }
        return new String(result);
    }

    private final SharedPreferences prefs;
    private final Handler mainHandler;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String[] cachedData = null;

    public interface AuthCallback {
        void onSuccess();
        void onFailure(String reason);
        void onUpdateInfo(String info);
    }

    public KeyAuthManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void saveEncryptedData(String plainKey, long expirySec, String deviceId, String info, long serverTimeSec) {
        try {
            cachedData = null;
            String data = plainKey + "|" + expirySec + "|" + deviceId + "|" + info;
            String encrypted = encrypt(data);
            prefs.edit()
                .putString(KEY_SAVED_KEY, encrypted)
                .putLong(KEY_LAST_SERVER_TIME, serverTimeSec)
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "saveEncryptedData failed", e);
        }
    }

    private String[] getDecryptedData() {
        if (cachedData != null) return cachedData;
        try {
            String encrypted = prefs.getString(KEY_SAVED_KEY, null);
            if (encrypted == null) return null;
            String decrypted = decrypt(encrypted);
            cachedData = decrypted.split("\\|", 4);
            return cachedData;
        } catch (Exception e) {
            Log.w(TAG, "null");
            prefs.edit().remove(KEY_SAVED_KEY).apply();
            return null;
        }
    }

    public String getPlainKey() {
        String[] data = getDecryptedData();
        return (data != null && data.length >= 1) ? data[0] : null;
    }

    public long getExpiryTimestamp() {
        String[] data = getDecryptedData();
        if (data != null && data.length >= 2) {
            try { return Long.parseLong(data[1]); } catch (Exception ignored) {}
        }
        return 0;
    }

    public String getSavedDeviceId() {
        String[] data = getDecryptedData();
        return (data != null && data.length >= 3) ? data[2] : "";
    }

    public String getSavedInfo() {
        String[] data = getDecryptedData();
        return (data != null && data.length >= 4) ? data[3] : "";
    }

    public boolean isKeyValid() {
        long expiry = getExpiryTimestamp();
        String savedId = getSavedDeviceId();
        String currentId = getDeviceId();
        long currentTimeSec = System.currentTimeMillis() / 1000;
        long lastServerTime = prefs.getLong(KEY_LAST_SERVER_TIME, 0);

        if (lastServerTime > 0 && currentTimeSec < lastServerTime) {
            Log.w(TAG, "Clock rollback detected!");
            return false;
        }

        boolean timeValid = currentTimeSec < expiry;
        boolean deviceValid = savedId.isEmpty() || savedId.equals("FREE") || savedId.equals(currentId);
        return timeValid && deviceValid;
    }

    public long getRemainingTime() {
        long expiry = getExpiryTimestamp();
        return Math.max(0, expiry - (System.currentTimeMillis() / 1000));
    }

    public String getDeviceId() {
        try {
            return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    public void validateKey(final String userKey, final AuthCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String plainKey = userKey;
                if (plainKey.startsWith(KEY_PREFIX)) {
                    plainKey = plainKey.substring(KEY_PREFIX.length());
                }
                String hashedKey = sha256(plainKey);
                String currentDeviceId = getDeviceId();

                URL url = new URL(decode(URL_DATA));
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    mainHandler.post(() -> callback.onFailure("NET_ERROR: Server error " + responseCode));
                    return;
                }

                long serverTimeSec = conn.getHeaderFieldDate("Date", System.currentTimeMillis()) / 1000;

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                boolean found = false;

                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.trim().split(":", 4);
                    if (parts.length >= 2 && parts[0].equals(hashedKey)) {
                        long expiry = Long.parseLong(parts[1]);
                        String allowedDevice = (parts.length >= 3) ? parts[2] : "FREE";
                        String infoMsg = (parts.length >= 4) ? parts[3] : "No Info Available";

                        if (serverTimeSec >= expiry) {
                            final String finalInfo = infoMsg;
                            mainHandler.post(() -> {
                                callback.onUpdateInfo(finalInfo);
                                callback.onFailure("expired!");
                            });
                        } else if (!allowedDevice.equals("FREE") && !allowedDevice.equals(currentDeviceId)) {
                            mainHandler.post(() -> callback.onFailure(
                                "Key is bound to another phone!\nID: " + currentDeviceId));
                        } else {
                            saveEncryptedData(userKey, expiry, allowedDevice, infoMsg, serverTimeSec);
                            final String finalInfo = infoMsg;
                            mainHandler.post(() -> {
                                callback.onUpdateInfo(finalInfo);
                                callback.onSuccess();
                            });
                        }
                        found = true;
                        break;
                    }
                }
                reader.close();

                if (!found) {
                    mainHandler.post(() -> callback.onFailure("Invalid key!"));
                }

            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure("NET_ERROR: " + e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void logout() {
        cachedData = null;
        prefs.edit().clear().apply();
    }

    private String encrypt(String text) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        SecretKeySpec keySpec = new SecretKeySpec(decode(KEY_DATA).getBytes("UTF-8"), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] encrypted = cipher.doFinal(text.getBytes("UTF-8"));
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    private String decrypt(String encryptedBase64) throws Exception {
        byte[] combined = Base64.decode(encryptedBase64, Base64.NO_WRAP);
        byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
        SecretKeySpec keySpec = new SecretKeySpec(decode(KEY_DATA).getBytes("UTF-8"), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return new String(cipher.doFinal(ciphertext), "UTF-8");
    }

    private String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void fetchRemoteConfig(SharedPreferences modPrefs, final Runnable onComplete) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(decode(CONFIG_URL_DATA));
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    org.json.JSONObject json = new org.json.JSONObject(sb.toString());
                    org.json.JSONObject features = json.getJSONObject("features");
                    org.json.JSONArray vips = json.getJSONArray("vip_devices");
                    boolean isVip = false;
                    String myDeviceId = getDeviceId();
                    for (int i = 0; i < vips.length(); i++) {
                        if (vips.getString(i).equals(myDeviceId)) { isVip = true; break; }
                    }
                    SharedPreferences.Editor editor = modPrefs.edit();
                    editor.putBoolean("is_vip_user", isVip);
                    java.util.Iterator<String> keys = features.keys();
while (keys.hasNext()) {
    String key = keys.next();
    editor.putBoolean("remote_" + key, features.getBoolean(key));
}

// === LOGIKA ORIGINAL: Jika bukan VIP, paksa matikan toggle sesuai remote flag ===
if (!isVip) {
    // Aimbot
    if (features.has("aimbot") && !features.optBoolean("aimbot", true)) {
        editor.putBoolean("aimbot_enable", false);
    }
    // Radar
    if (features.has("radar") && !features.optBoolean("radar", true)) {
        editor.putBoolean("radar_enable", false);
    }
    // Combo
    if (features.has("combo") && !features.optBoolean("combo", true)) {
        editor.putString("selected_combo", "none");
    }
    // ESP Circle
    if (features.has("esp_circle") && !features.optBoolean("esp_circle", true)) {
        editor.putBoolean("esp_circle", false);
    }
    // ESP Line
    if (features.has("esp_line") && !features.optBoolean("esp_line", true)) {
        editor.putBoolean("esp_line", false);
    }
    // ESP Health
    if (features.has("esp_health") && !features.optBoolean("esp_health", true)) {
        editor.putBoolean("esp_health", false);
    }
    // ESP Cooldown
    if (features.has("esp_cooldown") && !features.optBoolean("esp_cooldown", true)) {
        editor.putBoolean("esp_cooldown", false);
    }
    // ESP Monster
    if (features.has("esp_monster") && !features.optBoolean("esp_monster", true)) {
        editor.putBoolean("esp_monster", false);
    }
    // Alert Monster HP
    if (features.has("alert_monster_hp") && !features.optBoolean("alert_monster_hp", true)) {
        editor.putBoolean("alert_monster_hp", false);
    }
    // Alert Enemy Ulti
    if (features.has("alert_enemy_ulti") && !features.optBoolean("alert_enemy_ulti", true)) {
        editor.putBoolean("alert_enemy_ulti", false);
    }
    // Disable Shadows
    if (features.has("disable_shadows") && !features.optBoolean("disable_shadows", true)) {
        editor.putBoolean("disable_shadows", false);
    }
    // Disable AA
    if (features.has("disable_aa") && !features.optBoolean("disable_aa", true)) {
        editor.putBoolean("disable_aa", false);
    }
    // Ling mode
    if (features.has("ling") && !features.optBoolean("ling", true)) {
        editor.putInt("ling_mode", 0);
    }
    // Retribution semua
    if (features.has("retribution") && !features.optBoolean("retribution", true)) {
        editor.putBoolean("retri_buff", false);
        editor.putBoolean("retri_lord", false);
        editor.putBoolean("retri_turtle", false);
        editor.putBoolean("retri_litho", false);
    }
    // Lock Hero
    if (features.has("lock_hero") && !features.optBoolean("lock_hero", true)) {
        editor.putBoolean("lock_hero_enable", false);
    }
}
editor.apply();
                }
            } catch (Exception e) { Log.e(TAG, "fetchRemoteConfig failed", e); }
            finally {
                if (conn != null) conn.disconnect();
                if (onComplete != null) mainHandler.post(onComplete);
            }
        });
    }
}
