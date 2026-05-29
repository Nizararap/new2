package com.overlay.ui.tabs;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.LinearLayout;

import com.overlay.KeyAuthManager;
import com.overlay.RadarView;
import com.overlay.ui.UIFactory;

public class TabRadar {
    public static LinearLayout build(Context ctx, SharedPreferences prefs, KeyAuthManager authManager, RadarView radar, int realScreenW, int realScreenH) {
        LinearLayout t = new LinearLayout(ctx); 
        t.setOrientation(LinearLayout.VERTICAL);

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "RADAR"));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Enable Radar", "Show minimap overlay", "radar_enable", false));
            l.addView(UIFactory.vgap(ctx, 6));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Draw Border", "Border around radar", "radar_border", true));
        }));

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "SIZE & POSITION"));
            l.addView(UIFactory.slider(ctx, prefs, authManager, radar, realScreenW, realScreenH, "X Position", "radar_pos_x", 0, 2000, 71));
            l.addView(UIFactory.slider(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Map Size", "radar_size", 80, 600, 338));
            l.addView(UIFactory.slider(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Icon Size", "radar_icon_size", 10, 100, 37));
            
            l.addView(UIFactory.vgap(ctx, 8));
            l.addView(UIFactory.btn(ctx, "Reset Defaults", UIFactory.C_BTN_DRK, () -> {
                prefs.edit().putFloat("radar_pos_x", 71f)
                    .putFloat("radar_size", 338f)
                    .putFloat("radar_icon_size", 37f).apply();
                radar.invalidate();
            }));
        }));

        return t;
    }
}
