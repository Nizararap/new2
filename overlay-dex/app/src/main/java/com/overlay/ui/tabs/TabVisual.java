package com.overlay.ui.tabs;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.LinearLayout;

import com.overlay.KeyAuthManager;
import com.overlay.RadarView;
import com.overlay.ui.UIFactory;

public class TabVisual {
    public static LinearLayout build(Context ctx, SharedPreferences prefs, KeyAuthManager authManager, RadarView radar, int realScreenW, int realScreenH) {
        LinearLayout t = new LinearLayout(ctx); 
        t.setOrientation(LinearLayout.VERTICAL);

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "CAMERA DRONE VIEW"));
            l.addView(UIFactory.slider(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Drone Height", "drone_view", 0, 40, 0));
        }));

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "ESP (EXTRA SENSORY PERCEPTION)"));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "ESP Circle", "Draw icon to enemy & circle below", "esp_circle", false));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "ESP Line (Snaplines)", "Draw lines from bottom to enemies", "esp_line", false));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "ESP Health Arc", "Sci-Fi health ring around enemy", "esp_health", false));
            l.addView(UIFactory.vgap(ctx, 6));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Skill & Spell CD", "Show enemy cooldown timers", "esp_cooldown", false));
        }));

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "MONSTER ESP & ALERTS"));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "ESP Monster Info", "Show Buff, Turtle & Lord health", "esp_monster", false));
            l.addView(UIFactory.vgap(ctx, 6));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Boss HP Alert (Top Screen)", "Always show Lord/Turtle HP on top screen", "alert_monster_hp", false));
        }));

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "COMBAT ALERTS"));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Enemy Ulti Tracker", "Show Toast Notification when enemy ultimate", "alert_enemy_ulti", false));
        }));

        return t;
    }
}
