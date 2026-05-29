package com.overlay.auth;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

public class FeatureManager {
    private static final Map<String, Boolean> lockCache = new HashMap<>();

    public static boolean isFeatureLocked(SharedPreferences prefs, String featureKey) {
        if (lockCache.containsKey(featureKey)) return lockCache.get(featureKey);
        
        boolean isVip = prefs.getBoolean("is_vip_user", false);
        if (isVip) {
            lockCache.put(featureKey, false);
            return false;
        }
        
        boolean allowed = prefs.getBoolean("remote_" + featureKey, true);
        boolean locked = !allowed;
        lockCache.put(featureKey, locked);
        return locked;
    }

    public static String mapKeyToFeature(String prefKey) {
        switch (prefKey) {
            case "aimbot_enable": return "aimbot";
            case "radar_enable": return "radar";
            case "selected_combo": return "combo";
            case "esp_circle": return "esp_circle";
            case "esp_line": return "esp_line";
            case "esp_health": return "esp_health";
            case "esp_cooldown": return "esp_cooldown";
            case "esp_monster": return "esp_monster";
            case "alert_monster_hp": return "alert_monster_hp";
            case "alert_enemy_ulti": return "alert_enemy_ulti";
            case "disable_shadows": return "disable_shadows";
            case "disable_aa": return "disable_aa";
            case "ling_mode": return "ling";
            case "retri_buff": case "retri_lord": case "retri_turtle": case "retri_litho": return "retribution";
            case "lock_hero_enable": return "lock_hero";
            default: return prefKey;
        }
    }
}
