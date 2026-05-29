package com.overlay.ui.tabs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.overlay.KeyAuthManager;
import com.overlay.RadarView;
import com.overlay.ui.UIFactory;
import com.overlay.service.NativeSender;

public class TabDashboard {
    public static LinearLayout build(Context ctx, SharedPreferences prefs, KeyAuthManager authManager, RadarView radar, int realScreenW, int realScreenH, Runnable refreshUI, Runnable applyScale) {
        LinearLayout t = new LinearLayout(ctx); 
        t.setOrientation(LinearLayout.VERTICAL);

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "SYSTEM SETTINGS"));
            l.addView(uiScaleSlider(ctx, prefs, applyScale));
            l.addView(UIFactory.vgap(ctx, 8));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Lock Position", "Disable menu dragging", "ui_lock", false));
        }));

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "PERFORMANCE & ENGINE"));
            l.addView(UIFactory.secTitle(ctx, "FORCE TARGET FPS"));
            l.addView(UIFactory.radioRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "fps_index", new String[]{"Auto", "30", "45", "60", "90", "120"}));
            l.addView(UIFactory.vgap(ctx, 8));
            
            l.addView(UIFactory.secTitle(ctx, "RESOLUTION SCALE (BOOST FPS)"));
            l.addView(UIFactory.slider(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Render Scale %", "res_scale", 30, 100, 100));
            l.addView(UIFactory.vgap(ctx, 8));

            l.addView(UIFactory.secTitle(ctx, "GRAPHICS OPTIMIZER"));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Disable Shadows", "Remove all shadows to boost FPS", "disable_shadows", false));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Disable Anti-Aliasing", "Remove edge smoothing for pure performance", "disable_aa", false));
            l.addView(UIFactory.vgap(ctx, 8));

            l.addView(UIFactory.secTitle(ctx, "LOADING PRIORITY"));
            l.addView(UIFactory.radioRowVertical(ctx, prefs, authManager, radar, realScreenW, realScreenH, "thread_priority", new String[]{
                "Low (Smooth In-Game, Slow Loading)", 
                "Below Normal", 
                "Normal (MLBB Default)", 
                "High (Fast Loading, Drop FPS)"
            }));
        }));

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "UTILITIES & CLEANER"));
            l.addView(UIFactory.btn(ctx, "🗑️ CLEAR CACHE", UIFactory.C_BTN_DRK, () -> {
                prefs.edit().putBoolean("action_clear_cache", true).apply();
                NativeSender.sendConfigToCpp(prefs, authManager, realScreenW, realScreenH);
                Toast.makeText(ctx, "Cache Cleared!", Toast.LENGTH_SHORT).show();
            }));
            l.addView(UIFactory.vgap(ctx, 4));
            l.addView(UIFactory.btn(ctx, "🚪 FORCE QUIT GAME", Color.parseColor("#C62828"), () -> {
                prefs.edit().putBoolean("action_quit", true).apply();
                NativeSender.sendConfigToCpp(prefs, authManager, realScreenW, realScreenH);
            }));
        }));

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "COMMUNITY & SUPPORT"));
            l.addView(UIFactory.btn(ctx, "JOIN TELEGRAM CHANNEL", UIFactory.C_TELEGRAM, () -> {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/modfreew"));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            }));
            l.addView(UIFactory.vgap(ctx, 8));
            l.addView(UIFactory.btn(ctx, "RESET ALL CONFIGURATIONS", UIFactory.C_BTN_DRK, () -> {
                prefs.edit().clear().apply();
                NativeSender.sendConfigToCpp(prefs, authManager, realScreenW, realScreenH);
                refreshUI.run();
                radar.invalidate();
                Toast.makeText(ctx, "Settings reset to default", Toast.LENGTH_SHORT).show();
            }));
        }));

        return t;
    }

    private static View uiScaleSlider(Context ctx, SharedPreferences prefs, Runnable applyScale) {
        LinearLayout col = new LinearLayout(ctx); col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, UIFactory.dp(ctx, 4), 0, UIFactory.dp(ctx, 4));

        LinearLayout labelRow = new LinearLayout(ctx); labelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView ttl = new TextView(ctx); ttl.setText("UI Scale");
        ttl.setTextColor(UIFactory.C_TEXT); ttl.setTextSize(12f);
        ttl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(ttl);

        float initScale = prefs.getFloat("ui_scale", 1.0f);
        TextView tvVal = new TextView(ctx);
        tvVal.setText(String.format("%.1fx", initScale));
        tvVal.setTextColor(UIFactory.C_ACCENT); tvVal.setTextSize(11f); tvVal.setTypeface(null, Typeface.BOLD);
        labelRow.addView(tvVal);
        col.addView(labelRow);

        TextView sub = new TextView(ctx); sub.setText("Scale window & text");
        sub.setTextColor(UIFactory.C_SUBTEXT); sub.setTextSize(10f); sub.setPadding(0, 0, 0, UIFactory.dp(ctx, 4));
        col.addView(sub);

        SeekBar sb = new SeekBar(ctx);
        sb.setMax(20);
        sb.setProgress((int)(((initScale - 0.5f) / 0.05f)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                float scale = 0.5f + (p * 0.05f);
                tvVal.setText(String.format("%.1fx", scale));
                prefs.edit().putFloat("ui_scale", scale).apply();
                applyScale.run();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        col.addView(sb);
        return col;
    }
}
