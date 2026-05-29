package com.overlay.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.overlay.KeyAuthManager;
import com.overlay.RadarView;
import com.overlay.auth.FeatureManager;
import com.overlay.service.NativeSender;

public class UIFactory {
    public static final int C_BG      = Color.argb(220, 10, 10, 15);
    public static final int C_CARD    = Color.argb(170, 20, 20, 30);
    public static final int C_HEADER  = Color.argb(245, 5, 5, 10);
    public static final int C_ACCENT  = Color.parseColor("#D4AF37");
    public static final int C_TELEGRAM = Color.parseColor("#0088cc");
    public static final int C_TEXT    = Color.parseColor("#FFFFFF");
    public static final int C_SUBTEXT = Color.parseColor("#A0A0A0");
    public static final int C_DIVIDER = Color.argb(60, 212, 175, 55);
    public static final int C_BTN_DRK = Color.argb(200, 30, 30, 45);

    public static int dp(Context ctx, int p) {
        return (int) (p * ctx.getResources().getDisplayMetrics().density);
    }

    public interface CardB { void b(LinearLayout l); }

    public static View card(Context ctx, CardB cb) {
        LinearLayout c = new LinearLayout(ctx); c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD); 
        bg.setCornerRadius(dp(ctx, 16));
        bg.setStroke(dp(ctx, 1), Color.argb(15, 255, 255, 255));
        c.setBackground(bg); cb.b(c);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, 0, 0, dp(ctx, 12));
        c.setLayoutParams(clp);
        return c;
    }

    public static View secTitle(Context ctx, String title) {
        LinearLayout r = new LinearLayout(ctx); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(ctx, 4), 0, dp(ctx, 12));
        View bar = new View(ctx);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(ctx, 4), dp(ctx, 14)); blp.setMargins(0,0,dp(ctx, 8),0);
        bar.setLayoutParams(blp);
        GradientDrawable bbg = new GradientDrawable(); bbg.setColor(C_ACCENT); bbg.setCornerRadius(dp(ctx, 100));
        bar.setBackground(bbg); r.addView(bar);
        TextView tv = new TextView(ctx); tv.setText(title.toUpperCase()); tv.setTextColor(C_ACCENT);
        tv.setTextSize(10f); tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD)); tv.setLetterSpacing(0.12f);
        tv.setAlpha(0.9f);
        r.addView(tv);
        return r;
    }

    public static View toggleRow(Context ctx, SharedPreferences prefs, KeyAuthManager authManager, RadarView radar, int realScreenW, int realScreenH, String title, String sub, String key, boolean def) {
        String featureKey = FeatureManager.mapKeyToFeature(key);
        boolean locked = FeatureManager.isFeatureLocked(prefs, featureKey);
        
        LinearLayout r = new LinearLayout(ctx);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(ctx, 4), 0, dp(ctx, 4));
        
        LinearLayout tc = new LinearLayout(ctx);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        
        String displayTitle = locked ? "🔒 " + title + " (VIP Only)" : title;
        TextView t1 = new TextView(ctx);
        t1.setText(displayTitle);
        t1.setTextColor(locked ? C_SUBTEXT : C_TEXT);
        t1.setTextSize(12f);
        tc.addView(t1);
        
        if (sub != null && !sub.isEmpty()) {
            TextView t2 = new TextView(ctx);
            t2.setText(sub);
            t2.setTextColor(C_SUBTEXT);
            t2.setTextSize(10f);
            tc.addView(t2);
        }
        r.addView(tc);
        
        boolean init = prefs.getBoolean(key, def);
        View toggle = buildToggle(ctx, init, on -> {
            if (locked) return;
            if (!authManager.isKeyValid()) {
                Toast.makeText(ctx, "Key Expired! Please Relogin.", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putBoolean(key, on).apply();
            NativeSender.sendConfigToCpp(prefs, authManager, realScreenW, realScreenH);
            radar.invalidate();
        });
        
        if (locked) {
            toggle.setEnabled(false);
            toggle.setAlpha(0.5f);
            r.setOnClickListener(v -> {
                Toast.makeText(ctx, "🔒 VIP Only! Upgrade to use " + title, Toast.LENGTH_SHORT).show();
            });
        }
        
        r.addView(toggle);
        return r;
    }

    public interface TCb { void t(boolean on); }

    public static View buildToggle(Context ctx, boolean init, TCb cb) {
        final boolean[] on = {init};
        LinearLayout track = new LinearLayout(ctx);
        track.setGravity(init ? Gravity.END|Gravity.CENTER_VERTICAL : Gravity.START|Gravity.CENTER_VERTICAL);
        track.setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4));
        track.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 24)));
        
        final GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(init ? C_ACCENT : Color.argb(40, 160, 160, 184)); 
        tbg.setCornerRadius(dp(ctx, 100));
        track.setBackground(tbg);
        
        View thumb = new View(ctx); 
        thumb.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 16), dp(ctx, 16)));
        GradientDrawable thbg = new GradientDrawable(); 
        thbg.setShape(GradientDrawable.OVAL);
        thbg.setColor(Color.WHITE); 
        thumb.setBackground(thbg); 
        track.addView(thumb);
        
        track.setOnClickListener(v -> {
            on[0] = !on[0];
            tbg.setColor(on[0] ? C_ACCENT : Color.argb(40, 160, 160, 184));
            track.setGravity(on[0] ? Gravity.END|Gravity.CENTER_VERTICAL : Gravity.START|Gravity.CENTER_VERTICAL);
            cb.t(on[0]);
        });
        return track;
    }

    public static View checkRow(Context ctx, SharedPreferences prefs, KeyAuthManager authManager, RadarView radar, int realScreenW, int realScreenH, String title, String key, boolean def) {
        String featureKey = FeatureManager.mapKeyToFeature(key);
        boolean locked = FeatureManager.isFeatureLocked(prefs, featureKey);
        
        LinearLayout r = new LinearLayout(ctx);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6));
        
        boolean init = prefs.getBoolean(key, def);
        final boolean[] st = {init};
        
        TextView box = new TextView(ctx);
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)));
        box.setGravity(Gravity.CENTER);
        box.setTextSize(11f);
        box.setTypeface(null, Typeface.BOLD);
        
        Runnable updateBox = () -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(ctx, 4));
            if (st[0]) {
                bg.setColor(C_ACCENT);
                box.setText("✓");
                box.setTextColor(C_BG);
            } else {
                bg.setColor(C_BG);
                bg.setStroke(dp(ctx, 1), C_SUBTEXT);
                box.setText("");
            }
            box.setBackground(bg);
        };
        updateBox.run();
        
        String displayTitle = locked ? "🔒 " + title + " (VIP Only)" : title;
        TextView lbl = new TextView(ctx);
        lbl.setText(displayTitle);
        lbl.setTextColor(locked ? C_SUBTEXT : C_TEXT);
        lbl.setTextSize(12f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.setMargins(dp(ctx, 10), 0, 0, 0);
        lbl.setLayoutParams(llp);
        
        r.addView(box);
        r.addView(lbl);
        
        if (locked) {
            r.setAlpha(0.5f);
            r.setEnabled(false);
            r.setOnClickListener(v -> {
                Toast.makeText(ctx, "🔒 VIP Only! Upgrade to use " + title, Toast.LENGTH_SHORT).show();
            });
        } else {
            r.setOnClickListener(v -> {
                if (!authManager.isKeyValid()) {
                    Toast.makeText(ctx, "Key Expired! Please Relogin.", Toast.LENGTH_SHORT).show();
                    return;
                }
                st[0] = !st[0];
                prefs.edit().putBoolean(key, st[0]).apply();
                NativeSender.sendConfigToCpp(prefs, authManager, realScreenW, realScreenH);
                updateBox.run();
                radar.invalidate();
            });
        }
        return r;
    }

    public static View checkRowSpanned(Context ctx, SharedPreferences prefs, KeyAuthManager authManager, RadarView radar, int realScreenW, int realScreenH, String pre, String word1, String mid, String word2, String post, String key, boolean def) {
        String featureKey = FeatureManager.mapKeyToFeature(key);
        boolean locked = FeatureManager.isFeatureLocked(prefs, featureKey);
        LinearLayout r = new LinearLayout(ctx);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6));
        
        boolean init = prefs.getBoolean(key, def);
        final boolean[] st = {init};
        
        TextView box = new TextView(ctx);
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)));
        box.setGravity(Gravity.CENTER);
        box.setTextSize(11f);
        box.setTypeface(null, Typeface.BOLD);
        
        Runnable updateBox = () -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(ctx, 4));
            if (st[0]) {
                bg.setColor(C_ACCENT);
                box.setText("✓");
                box.setTextColor(C_BG);
            } else {
                bg.setColor(C_BG);
                bg.setStroke(dp(ctx, 1), C_SUBTEXT);
                box.setText("");
            }
            box.setBackground(bg);
        };
        updateBox.run();
        
        String full = pre + word1 + mid + word2 + post;
        SpannableString span = new SpannableString(full);
        int s1 = pre.length();
        int e1 = s1 + word1.length();
        int s2 = e1 + mid.length();
        int e2 = s2 + word2.length();
        span.setSpan(new ForegroundColorSpan(Color.parseColor("#42A5F5")), s1, e1, 0);
        span.setSpan(new ForegroundColorSpan(Color.parseColor("#EF5350")), s2, e2, 0);
        
        TextView lbl = new TextView(ctx);
        lbl.setText(span);
        lbl.setTextColor(locked ? C_SUBTEXT : C_TEXT);
        lbl.setTextSize(12f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.setMargins(dp(ctx, 10), 0, 0, 0);
        lbl.setLayoutParams(llp);
        
        r.addView(box);
        r.addView(lbl);
        
        if (locked) {
            r.setAlpha(0.5f);
            r.setEnabled(false);
            r.setOnClickListener(v -> {
                Toast.makeText(ctx, "🔒 VIP Only! Upgrade to use " + full, Toast.LENGTH_SHORT).show();
            });
        } else {
            r.setOnClickListener(v -> {
                if (!authManager.isKeyValid()) {
                    Toast.makeText(ctx, "Key Expired! Please re-login.", Toast.LENGTH_SHORT).show();
                    return;
                }
                st[0] = !st[0];
                prefs.edit().putBoolean(key, st[0]).apply();
                NativeSender.sendConfigToCpp(prefs, authManager, realScreenW, realScreenH);
                updateBox.run();
                radar.invalidate();
            });
        }
        return r;
    }

    public static View radioRow(Context ctx, SharedPreferences prefs, KeyAuthManager authManager, RadarView radar, int realScreenW, int realScreenH, String key, String[] options) {
        String featureKey = FeatureManager.mapKeyToFeature(key);
        boolean locked = FeatureManager.isFeatureLocked(prefs, featureKey);
        RadioGroup rg = new RadioGroup(ctx);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        rg.setPadding(0, dp(ctx, 4), 0, dp(ctx, 4));
        int cur = prefs.getInt(key, 0);
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(ctx);
            rb.setText(options[i]);
            rb.setTextColor(locked ? C_SUBTEXT : C_TEXT);
            rb.setTextSize(11.5f);
            rb.setId(i);
            if (i == cur) rb.setChecked(true);
            rb.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            rg.addView(rb);
        }
        if (locked) {
            rg.setEnabled(false);
            rg.setAlpha(0.5f);
            rg.setOnClickListener(v -> {
                Toast.makeText(ctx, "🔒 VIP Only! Upgrade to use this feature", Toast.LENGTH_SHORT).show();
            });
        } else {
            rg.setOnCheckedChangeListener((g, id) -> {
                prefs.edit().putInt(key, id).apply();
                NativeSender.sendConfigToCpp(prefs, authManager, realScreenW, realScreenH);
            });
        }
        return rg;
    }

    public static View radioRowVertical(Context ctx, SharedPreferences prefs, KeyAuthManager authManager, RadarView radar, int realScreenW, int realScreenH, String key, String[] options) {
        String featureKey = FeatureManager.mapKeyToFeature(key);
        boolean locked = FeatureManager.isFeatureLocked(prefs, featureKey);
        RadioGroup rg = new RadioGroup(ctx);
        rg.setOrientation(RadioGroup.VERTICAL);
        int cur = prefs.getInt(key, 0);
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(ctx);
            rb.setText(options[i]);
            rb.setTextColor(locked ? C_SUBTEXT : C_TEXT);
            rb.setTextSize(11.5f);
            rb.setId(i);
            rb.setPadding(0, 0, 0, 0);
            rb.setGravity(Gravity.CENTER_VERTICAL);
            if (i == cur) rb.setChecked(true);
            rg.addView(rb);
        }
        if (locked) {
            rg.setEnabled(false);
            rg.setAlpha(0.5f);
            rg.setOnClickListener(v -> {
                Toast.makeText(ctx, "🔒 VIP Only! Upgrade to use this feature", Toast.LENGTH_SHORT).show();
            });
        } else {
            rg.setOnCheckedChangeListener((g, id) -> {
                prefs.edit().putInt(key, id).apply();
                NativeSender.sendConfigToCpp(prefs, authManager, realScreenW, realScreenH);
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START;
        rg.setLayoutParams(lp);
        return rg;
    }

    public static View slider(Context ctx, SharedPreferences prefs, KeyAuthManager authManager, RadarView radar, int realScreenW, int realScreenH, String title, String key, float min, float max, float def) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(ctx, 8), 0, dp(ctx, 8));

        LinearLayout labelRow = new LinearLayout(ctx);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView ttl = new TextView(ctx); ttl.setText(title);
        ttl.setTextColor(C_TEXT); ttl.setTextSize(12f);
        ttl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(ttl);

        float initVal = prefs.getFloat(key, def);
        TextView tvVal = new TextView(ctx);
        tvVal.setText(String.format("%.0f", initVal));
        tvVal.setTextColor(C_ACCENT); tvVal.setTextSize(11f); tvVal.setTypeface(null, Typeface.BOLD);
        labelRow.addView(tvVal);
        col.addView(labelRow);

        SeekBar sb = new SeekBar(ctx);
        sb.setMax((int) (max - min));
        sb.setProgress((int) (initVal - min));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                float val = min + p;
                tvVal.setText(String.format("%.0f", val));
                if (u) {
                    prefs.edit().putFloat(key, val).apply();
                    NativeSender.sendConfigToCpp(prefs, authManager, realScreenW, realScreenH);
                    radar.invalidate();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        col.addView(sb);
        return col;
    }

    public static View vgap(Context ctx, int dpVal) {
        View v = new View(ctx); v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, dpVal)));
        return v;
    }

    public static View btn(Context ctx, String text, int color, Runnable r) {
        TextView b = new TextView(ctx); b.setText(text); b.setTextColor(C_TEXT);
        b.setTextSize(12f); b.setGravity(Gravity.CENTER); b.setPadding(0, dp(ctx, 9), 0, dp(ctx, 9));
        b.setTypeface(null, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(ctx, 8));
        b.setBackground(bg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, dp(ctx, 4), 0, dp(ctx, 4)); b.setLayoutParams(blp);
        b.setOnClickListener(v -> r.run()); return b;
    }
}
