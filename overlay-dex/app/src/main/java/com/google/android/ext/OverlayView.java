package com.google.android.ext;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class OverlayView extends LinearLayout {

    // MONDEV Palette
    private static final int C_BG      = Color.argb(220, 10, 10, 15);
    private static final int C_CARD    = Color.argb(170, 20, 20, 30);
    private static final int C_HEADER  = Color.argb(245, 5, 5, 10);
    private static final int C_ACCENT  = Color.parseColor("#D4AF37"); // Muted Gold
    private static final int C_TELEGRAM = Color.parseColor("#0088cc"); // Telegram Blue
    private static final int C_TEXT    = Color.parseColor("#FFFFFF");
    private static final int C_SUBTEXT = Color.parseColor("#A0A0A0"); // Gray subtext
    private static final int C_DIVIDER = Color.argb(60, 212, 175, 55); // Muted Gold divider
    private static final int C_BTN_DRK = Color.argb(200, 30, 30, 45);

    private final WindowManager wm;
    private final WindowManager.LayoutParams lp;
    private final RadarView radar;
    private final SharedPreferences prefs;
    private final KeyAuthManager authManager;
    private final Context context;

    private static final java.util.concurrent.ExecutorService socketExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    private int realScreenW;
    private int realScreenH;

    private float tx, ty;
    private int ix, iy;
    private boolean dragging;

    private LinearLayout panel, tabDash, tabRad, tabCombat;
    private TextView tvPill;
    private TextView[] tabBtns;
    private ScrollView scrollView;
    private LinearLayout tabRoom;

    public OverlayView(Context ctx, WindowManager wm, WindowManager.LayoutParams lp, RadarView radar) {
        super(ctx);
        this.context = ctx;
        this.wm    = wm;
        this.lp    = lp;
        this.radar = radar;
        this.prefs = ctx.getSharedPreferences("mod_settings", Context.MODE_PRIVATE);
        this.authManager = new KeyAuthManager(ctx);
        fetchRealScreenSize();
        setOrientation(VERTICAL);
        buildPill(ctx);
        buildPanel(ctx);
        showExpanded();
        sendConfigToCpp(this.prefs);
    }

    @SuppressWarnings("deprecation")
    private void fetchRealScreenSize() {
        Display display = wm.getDefaultDisplay();
        DisplayMetrics realMetrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            display.getRealMetrics(realMetrics);
        } else {
            display.getMetrics(realMetrics);
        }
        realScreenW = realMetrics.widthPixels;
        realScreenH = realMetrics.heightPixels;
    }

    private void buildPill(Context ctx) {
    tvPill = new TextView(ctx);
    tvPill.setGravity(Gravity.CENTER);

    try {
        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(
            ctx.getAssets().open("background2.jpg")); // ganti nama file sesuai keinginan
        if (bmp != null) {
            int pillSize = dp(44);
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            float scale = Math.min((float) pillSize / w, (float) pillSize / h);
            int newW = Math.round(w * scale);
            int newH = Math.round(h * scale);
            // Scale bitmap proporsional
            android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bmp, newW, newH, true);
            
            // Buat canvas lingkaran ukuran pill
            android.graphics.Bitmap circle = android.graphics.Bitmap.createBitmap(pillSize, pillSize, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(circle);
            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            
            // Gambar lingkaran (sebagai mask)
            canvas.drawCircle(pillSize / 2f, pillSize / 2f, pillSize / 2f, paint);
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            
            // Gambar bitmap di tengah
            int left = (pillSize - newW) / 2;
            int top = (pillSize - newH) / 2;
            canvas.drawBitmap(scaled, left, top, paint);
            
         /*   // (Opsional) overlay gelap jika ingin
            paint.setXfermode(null);
            paint.setColor(Color.argb(160, 0, 0, 0));
            canvas.drawCircle(pillSize / 2f, pillSize / 2f, pillSize / 2f, paint);
            */
            android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), circle);
            bd.setGravity(Gravity.CENTER);
            tvPill.setBackground(bd);
            tvPill.setPadding(0, 0, 0, 0);
        }
    } catch (Exception e) {
        // fallback gradient gold
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColors(new int[]{C_ACCENT, Color.parseColor("#8B7500")});
        bg.setOrientation(GradientDrawable.Orientation.TL_BR);
        tvPill.setBackground(bg);
        tvPill.setText("M");
        tvPill.setTextColor(Color.BLACK);
        tvPill.setTextSize(14f);
        tvPill.setTypeface(null, Typeface.BOLD);
        tvPill.setPadding(dp(12), dp(12), dp(12), dp(12));
    }

tvPill.setElevation(dp(6));
tvPill.setOnTouchListener(dragL);

LayoutParams pillLp = new LayoutParams(dp(44), dp(44));
tvPill.setLayoutParams(pillLp);

addView(tvPill);
    }

    private void buildPanel(Context ctx) {
        panel = new LinearLayout(ctx);
        panel.setOrientation(VERTICAL);
        
        // --- 🟢 UBAH BAGIAN INI 🟢 ---
        // WRAP_CONTENT = Otomatis menyesuaikan isi konten
        LayoutParams panelLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        panel.setLayoutParams(panelLp);
        
        // Set minimum width so Dashboard tab doesn't look too narrow
        panel.setMinimumWidth(dp(340)); 
        // ------------------------------

        // Background with Image
        try {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(ctx.getAssets().open("background.jpg"));
            if (bmp != null) {
                // Add a dark overlay on top of the image
                android.graphics.Bitmap overlay = android.graphics.Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), bmp.getConfig());
                android.graphics.Canvas canvas = new android.graphics.Canvas(overlay);
                canvas.drawBitmap(bmp, 0, 0, null);
                canvas.drawColor(Color.argb(200, 0, 0, 0)); // Dark overlay
                
                android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), overlay);
                panel.setBackground(bd);
            } else {
                panel.setBackgroundColor(C_BG);
            }
        } catch (Exception e) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(C_BG);
            bg.setCornerRadius(dp(20));
            bg.setStroke(dp(1), C_ACCENT);
            panel.setBackground(bg);
        }

        // Clip to rounded corners
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            panel.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
                }
            });
            panel.setClipToOutline(true);
        }

        panel.addView(buildHeader(ctx));
        panel.addView(buildTabs(ctx));
        panel.addView(buildContent(ctx));
        addView(panel);
        switchTab(0);
    }

    private View buildHeader(Context ctx) {
        LinearLayout h = new LinearLayout(ctx);
        GradientDrawable hbg = new GradientDrawable();
        hbg.setColor(C_HEADER);
        hbg.setCornerRadii(new float[]{dp(20), dp(20), dp(20), dp(20), 0, 0, 0, 0});
        h.setBackground(hbg);
        h.setPadding(dp(20), dp(16), dp(14), dp(16));
        h.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(VERTICAL);
        col.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout titleRow = new LinearLayout(ctx);
        titleRow.setGravity(Gravity.BOTTOM);

        TextView t1 = new TextView(ctx);
        t1.setText("MONDEV");
        t1.setLetterSpacing(0.1f);
        t1.setTextColor(C_TEXT); t1.setTextSize(18f); t1.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        titleRow.addView(t1);

        TextView tBeta = new TextView(ctx);
        tBeta.setText(" beta");
        tBeta.setTextColor(C_ACCENT); tBeta.setTextSize(10f); tBeta.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        tBeta.setPadding(dp(4), 0, 0, dp(2));
        titleRow.addView(tBeta);

        col.addView(titleRow);

        long rem = authManager.getRemainingTime();
        String timeStr = formatTime(rem);

        TextView t2 = new TextView(ctx);
        t2.setText("Subscription: " + timeStr);
        t2.setTextColor(C_ACCENT); t2.setTextSize(10f);
        t2.setAlpha(0.8f);
        col.addView(t2);
        h.addView(col);

        TextView minBtn = new TextView(ctx);
        minBtn.setText("—");
        minBtn.setTextColor(C_TEXT);
        minBtn.setTextSize(18f);
        minBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        minBtn.setOnClickListener(v -> showCollapsed());
        h.addView(minBtn);
        h.setOnTouchListener(dragL);
        return h;
    }

    private View buildTabs(Context ctx) {
        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(C_HEADER);
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(HORIZONTAL);
        bar.setPadding(dp(12), dp(2), dp(12), dp(10));
        String[] labels = {"DASHBOARD", "RADAR", "COMBAT", "ROOM"};
        tabBtns = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            tabBtns[i] = new TextView(ctx);
            tabBtns[i].setText(labels[i]);
            tabBtns[i].setTextSize(10f); 
            tabBtns[i].setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            tabBtns[i].setLetterSpacing(0.08f);
            tabBtns[i].setPadding(dp(16), dp(8), dp(16), dp(8));
            tabBtns[i].setGravity(Gravity.CENTER);
            LayoutParams tlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tlp.setMargins(0, 0, dp(6), 0);
            tabBtns[i].setLayoutParams(tlp);
            tabBtns[i].setOnClickListener(v -> switchTab(idx));
            bar.addView(tabBtns[i]);
        }
        hsv.addView(bar);
        return hsv;
    }

    private void switchTab(int idx) {
        for (int i = 0; i < tabBtns.length; i++) {
            boolean a = i == idx;
            tabBtns[i].setTextColor(a ? C_ACCENT : C_SUBTEXT);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(a ? Color.argb(40, 255, 215, 0) : Color.argb(15, 255, 255, 255));
            tbg.setCornerRadius(dp(12));
            if (a) tbg.setStroke(dp(1), Color.argb(150, 255, 215, 0));
            else tbg.setStroke(dp(1), Color.argb(20, 255, 255, 255));
            tabBtns[i].setBackground(tbg);
        }
        if (tabDash   != null) tabDash.setVisibility(idx == 0 ? VISIBLE : GONE);
    if (tabRad    != null) tabRad.setVisibility(idx == 1 ? VISIBLE : GONE);
    if (tabCombat != null) tabCombat.setVisibility(idx == 2 ? VISIBLE : GONE);
    if (tabRoom   != null) tabRoom.setVisibility(idx == 3 ? VISIBLE : GONE);
    }

    private View buildContent(Context ctx) {
        scrollView = new ScrollView(ctx);
        scrollView.setFillViewport(false);

        FrameLayout frame = new FrameLayout(ctx);
        frame.setPadding(dp(10), dp(8), dp(10), dp(10));

        tabDash   = buildDash(ctx);
        tabRad    = buildRadar(ctx);
        tabCombat = buildCombat(ctx);
        tabRoom   = buildRoomInfo(ctx);

        // Use consistent LayoutParams for all tabs to avoid layout shifts
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tabDash.setLayoutParams(flp);
        tabRad.setLayoutParams(flp);
        tabCombat.setLayoutParams(flp);
        tabRoom.setLayoutParams(flp);

        frame.addView(tabDash);
        frame.addView(tabRad);
        frame.addView(tabCombat);
        frame.addView(tabRoom);
        scrollView.addView(frame);

        scrollView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        int statusBarH = 0;
        try {
            int resId = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resId > 0) statusBarH = ctx.getResources().getDimensionPixelSize(resId);
        } catch (Exception ignored) {}
        final int maxScrollH = Math.max(dp(80), realScreenH - statusBarH - dp(100));

        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(
            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    if (scrollView.getHeight() > maxScrollH) {
                        scrollView.setLayoutParams(
                            new LayoutParams(LayoutParams.MATCH_PARENT, maxScrollH));
                    }
                }
            }
        );

        return scrollView;
    }

    // ==================== DASHBOARD ====================
    private LinearLayout buildDash(Context ctx) {
    LinearLayout t = new LinearLayout(ctx); 
    t.setOrientation(VERTICAL);

    t.addView(card(ctx, l -> {
        l.addView(secTitle(ctx, "SYSTEM SETTINGS"));
        l.addView(uiScaleSlider(ctx));
        l.addView(vgap(ctx, 8));
        l.addView(toggleRow(ctx, "Lock Position", "Disable menu dragging", "ui_lock", false));
    }));

    t.addView(card(ctx, l -> {
        l.addView(secTitle(ctx, "COMMUNITY & SUPPORT"));
        l.addView(btn(ctx, "JOIN TELEGRAM CHANNEL", C_TELEGRAM, () -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/modfreew"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }));
        l.addView(vgap(ctx, 8));
        l.addView(btn(ctx, "RESET ALL CONFIGURATIONS", C_BTN_DRK, () -> {
            prefs.edit().clear().apply();
            sendConfigToCpp(prefs);
            refreshAllUI();
            radar.invalidate();
            android.widget.Toast.makeText(ctx, "Settings reset to default", android.widget.Toast.LENGTH_SHORT).show();
        }));
    }));

    return t;
}

    // ==================== RADAR MAP ====================
    private LinearLayout buildRadar(Context ctx) {
    LinearLayout t = new LinearLayout(ctx); 
    t.setOrientation(VERTICAL);

    t.addView(card(ctx, l -> {
        l.addView(secTitle(ctx, "RADAR"));
        l.addView(toggleRow(ctx, "Enable Radar", "Show minimap overlay", "radar_enable", false));
        l.addView(vgap(ctx, 6));
        l.addView(toggleRow(ctx, "Draw Border", "Border around radar", "radar_border", true));
    }));

    t.addView(card(ctx, l -> {
        l.addView(secTitle(ctx, "SIZE & POSITION"));
        l.addView(slider(ctx, "X Position", "radar_pos_x", 0, 2000, 71));
        l.addView(slider(ctx, "Map Size", "radar_size", 80, 600, 338));
        l.addView(slider(ctx, "Icon Size", "radar_icon_size", 10, 100, 37));
        
        l.addView(vgap(ctx, 8));
        l.addView(btn(ctx, "Reset Defaults", C_BTN_DRK, () -> {
            prefs.edit().putFloat("radar_pos_x",71f)
                .putFloat("radar_size",338f)
                .putFloat("radar_icon_size",37f).apply();
            radar.invalidate();
        }));
    }));

    return t;
}
    // ==================== COMBAT & AIM ====================
    private LinearLayout buildCombat(Context ctx) {
        LinearLayout t = new LinearLayout(ctx); t.setOrientation(VERTICAL);

        t.addView(card(ctx, l -> {
            LinearLayout cols = new LinearLayout(ctx); cols.setOrientation(HORIZONTAL);

            LinearLayout ac = new LinearLayout(ctx); ac.setOrientation(VERTICAL);
            ac.addView(secTitle(ctx, "AIMBOT"));
            ac.addView(checkRow(ctx, "Aimbot All",   "aimbot_enable", false));
            ac.addView(vgap(ctx, 8));
            ac.addView(secTitle(ctx, "LING MODE"));
            ac.addView(radioRowVertical(ctx, "ling_mode", new String[]{"Off", "Manual", "Auto"}));

            cols.addView(ac, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            View vd = new View(ctx);
            vd.setLayoutParams(new LayoutParams(dp(1), LayoutParams.MATCH_PARENT));
            vd.setBackgroundColor(C_DIVIDER); cols.addView(vd);

            LinearLayout rc = new LinearLayout(ctx); rc.setOrientation(VERTICAL);
            rc.setPadding(dp(10), 0, 0, 0);
            rc.addView(secTitle(ctx, "RETRIBUTION"));
            rc.addView(checkRowSpanned(ctx, "Buff (", "Blue", " & ", "Red", ")", "retri_buff", false));
            rc.addView(checkRow(ctx, "Lord",   "retri_lord",   false));
            rc.addView(checkRow(ctx, "Turtle", "retri_turtle", false));
            rc.addView(checkRow(ctx, "Litho",  "retri_litho",  false));
            cols.addView(rc, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            l.addView(cols);
        }));

        // ---------- LOCK HERO ----------
        // ---------- LOCK HERO ----------
        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "LOCK HERO"));
            l.addView(toggleRow(ctx, "Enable Hero Lock", "Prioritize specific target", "lock_hero_enable", false));
            
            String currentHero = prefs.getString("locked_hero_name", "");
            if (currentHero.isEmpty()) currentHero = "None";

            final TextView[] btnHeroRef = new TextView[1];
            
            btnHeroRef[0] = (TextView) btn(ctx, "Select Hero: [" + currentHero + "]", C_BTN_DRK, () -> {
                java.util.List<String> listHero = radar.getActiveEnemyNames();
                if (listHero.isEmpty()) {
                    android.widget.Toast.makeText(ctx, "Enemy not detected yet!", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    String[] items = listHero.toArray(new String[0]);
                    // Show custom modern floating dialog
                    showModernDialog(ctx, "TARGET LOCK HERO", items, selected -> {
                        prefs.edit().putString("locked_hero_name", selected).apply();
                        if (btnHeroRef[0] != null) btnHeroRef[0].setText("Select Hero: [" + selected + "]");
                        sendConfigToCpp(prefs);
                    });
                }
            });
            l.addView(btnHeroRef[0]);
        }));


      // ---------- HERO COMBO ----------
        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "HERO SETTING"));
            
            String currentCombo = prefs.getString("selected_combo", "none");
            String displayCombo = "None";
            
            // Use .contains() so modified combo text doesn't break detection
            if (currentCombo.contains("gusion")) displayCombo = "Gusion";
            else if (currentCombo.contains("kadita")) displayCombo = "Kadita";
            else if (currentCombo.contains("beatrix")) displayCombo = "Beatrix ultimate";
            else if (currentCombo.contains("kimmy")) displayCombo = "Kimmy auto Experimental";
            
            final TextView[] btnComboRef = new TextView[1];
            
            btnComboRef[0] = (TextView) btn(ctx, "Select Hero: [" + displayCombo + "]", C_BTN_DRK, () -> {
                String[] comboList = {"None", "Gusion", "Kadita", "Beatrix ultimate", "Kimmy auto Experimental"};
                
                showModernDialog(ctx, "SELECT HERO COMBO", comboList, selected -> {
                    // Save entire string as lowercase
                    String valueToSave = selected.equals("None") ? "none" : selected.toLowerCase();
                    prefs.edit().putString("selected_combo", valueToSave).apply();
                    
                    if (btnComboRef[0] != null) btnComboRef[0].setText("Select Combo: [" + selected + "]");
                    sendConfigToCpp(prefs);
                });
            });
            l.addView(btnComboRef[0]);
        }));

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "TARGET PRIORITY"));
            l.addView(radioRow(ctx, "aimbot_target", new String[]{"Nearest", "Low HP", "Low HP %"}));
        }));

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "DETECTION"));
            l.addView(slider(ctx, "Aimbot FOV Range", "aimbot_fov", 0, 250, 20));
        }));
        return t;
    }

    // ==================== UI SCALE SLIDER ====================
    private View uiScaleSlider(Context ctx) {
        LinearLayout col = new LinearLayout(ctx); col.setOrientation(VERTICAL);
        col.setPadding(0, dp(4), 0, dp(4));

        LinearLayout labelRow = new LinearLayout(ctx); labelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView ttl = new TextView(ctx); ttl.setText("UI Scale");
        ttl.setTextColor(C_TEXT); ttl.setTextSize(12f);
        ttl.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(ttl);

        float initScale = prefs.getFloat("ui_scale", 1.0f);
        TextView tvVal = new TextView(ctx);
        tvVal.setText(String.format("%.1fx", initScale));
        tvVal.setTextColor(C_ACCENT); tvVal.setTextSize(11f); tvVal.setTypeface(null, Typeface.BOLD);
        labelRow.addView(tvVal);
        col.addView(labelRow);

        TextView sub = new TextView(ctx); sub.setText("Scale window & text");
        sub.setTextColor(C_SUBTEXT); sub.setTextSize(10f); sub.setPadding(0, 0, 0, dp(4));
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
                applyUIScale(scale);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        col.addView(sb);

        post(() -> applyUIScale(initScale));
        return col;
    }

    private void applyUIScale(float scale) {
        if (panel == null) return;
        panel.setPivotX(0f);
        panel.setPivotY(0f);
        panel.setScaleX(scale);
        panel.setScaleY(scale);
    }

    private void refreshAllUI() {
        if (scrollView == null) return;
        FrameLayout frame = (FrameLayout) scrollView.getChildAt(0);
        if (frame == null) return;

        frame.removeAllViews();

        Context ctx = getContext();
        tabDash   = buildDash(ctx);
        tabRad    = buildRadar(ctx);
        tabCombat = buildCombat(ctx);
        tabRoom   = buildRoomInfo(ctx);

        // Apply consistent LayoutParams during refresh
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tabDash.setLayoutParams(flp);
        tabRad.setLayoutParams(flp);
        tabCombat.setLayoutParams(flp);
        tabRoom.setLayoutParams(flp);

        frame.addView(tabDash);
        frame.addView(tabRad);
        frame.addView(tabCombat);
        frame.addView(tabRoom);

        switchTab(0);
    }

    // ==================== HELPER UI ====================
    interface CardB { void b(LinearLayout l); }

    private View card(Context ctx, CardB cb) {
        LinearLayout c = new LinearLayout(ctx); c.setOrientation(VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD); 
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.argb(15, 255, 255, 255));
        c.setBackground(bg); cb.b(c);
        LayoutParams clp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, 0, 0, dp(12));
        c.setLayoutParams(clp);
        return c;
    }

    private View secTitle(Context ctx, String title) {
        LinearLayout r = new LinearLayout(ctx); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(4), 0, dp(12));
        View bar = new View(ctx);
        LayoutParams blp = new LayoutParams(dp(4), dp(14)); blp.setMargins(0,0,dp(8),0);
        bar.setLayoutParams(blp);
        GradientDrawable bbg = new GradientDrawable(); bbg.setColor(C_ACCENT); bbg.setCornerRadius(dp(100));
        bar.setBackground(bbg); r.addView(bar);
        TextView tv = new TextView(ctx); tv.setText(title.toUpperCase()); tv.setTextColor(C_ACCENT);
        tv.setTextSize(10f); tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD)); tv.setLetterSpacing(0.12f);
        tv.setAlpha(0.9f);
        r.addView(tv);
        return r;
    }

    private View toggleRow(Context ctx, String title, String sub, String key, boolean def) {
        LinearLayout r = new LinearLayout(ctx); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(4), 0, dp(4));
        LinearLayout tc = new LinearLayout(ctx); tc.setOrientation(VERTICAL);
        tc.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView t1 = new TextView(ctx); t1.setText(title); t1.setTextColor(C_TEXT); t1.setTextSize(12f);
        tc.addView(t1);
        if (sub != null && !sub.isEmpty()) {
            TextView t2 = new TextView(ctx); t2.setText(sub); t2.setTextColor(C_SUBTEXT); t2.setTextSize(10f);
            tc.addView(t2);
        }
        r.addView(tc);
        r.addView(buildToggle(ctx, prefs.getBoolean(key, def), on -> {
            if (!authManager.isKeyValid()) {
                android.widget.Toast.makeText(getContext(), " Key Expired! Please Relogin.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putBoolean(key, on).apply();
            sendConfigToCpp(prefs);
            radar.invalidate();
        }));
        return r;
    }

    private View checkRow(Context ctx, String title, String key, boolean def) {
        LinearLayout r = new LinearLayout(ctx); 
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));
        
        boolean init = prefs.getBoolean(key, def);
        final boolean[] st = {init};

        // Custom Modern Checkbox Box (Bentuk Kotak)
        TextView box = new TextView(ctx);
        box.setLayoutParams(new LayoutParams(dp(18), dp(18)));
        box.setGravity(Gravity.CENTER);
        box.setTextSize(11f);
        box.setTypeface(null, Typeface.BOLD);

        Runnable updateBox = () -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(4));
            if (st[0]) {
                bg.setColor(C_ACCENT); // Fill color when on
                box.setText("✓");
                box.setTextColor(C_BG); // Warna centang gelap
            } else {
                bg.setColor(C_BG);
                bg.setStroke(dp(1), C_SUBTEXT); // Border only when off
                box.setText("");
            }
            box.setBackground(bg);
        };
        updateBox.run();

        TextView lbl = new TextView(ctx); 
        lbl.setText(title); 
        lbl.setTextColor(C_TEXT); 
        lbl.setTextSize(12f);
        LayoutParams llp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        llp.setMargins(dp(10), 0, 0, 0);
        lbl.setLayoutParams(llp);

        r.addView(box); r.addView(lbl);
        r.setOnClickListener(v -> {
            if (!authManager.isKeyValid()) {
                android.widget.Toast.makeText(getContext(), " Key Expired! Please Relogin.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            st[0] = !st[0];
            prefs.edit().putBoolean(key, st[0]).apply();
            sendConfigToCpp(prefs);
            updateBox.run(); // Animasi update centang
            radar.invalidate();
        });
        return r;
    }
    interface TCb { void t(boolean on); }

    // Checkbox with a two-color SpannableString label (e.g. "Blue" in blue, "Red" in red)
    private View checkRowSpanned(Context ctx, String pre, String word1, String mid, String word2, String post, String key, boolean def) {
        LinearLayout r = new LinearLayout(ctx);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));

        boolean init = prefs.getBoolean(key, def);
        final boolean[] st = {init};

        TextView box = new TextView(ctx);
        box.setLayoutParams(new LayoutParams(dp(18), dp(18)));
        box.setGravity(Gravity.CENTER);
        box.setTextSize(11f);
        box.setTypeface(null, Typeface.BOLD);

        Runnable updateBox = () -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(4));
            if (st[0]) {
                bg.setColor(C_ACCENT);
                box.setText("✓");
                box.setTextColor(C_BG);
            } else {
                bg.setColor(C_BG);
                bg.setStroke(dp(1), C_SUBTEXT);
                box.setText("");
            }
            box.setBackground(bg);
        };
        updateBox.run();

        // Build SpannableString: "Buff (Blue & Red)"
        String full = pre + word1 + mid + word2 + post;
        android.text.SpannableString span = new android.text.SpannableString(full);
        int s1 = pre.length();
        int e1 = s1 + word1.length();
        int s2 = e1 + mid.length();
        int e2 = s2 + word2.length();
        span.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#42A5F5")), s1, e1, 0);
        span.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#EF5350")), s2, e2, 0);

        TextView lbl = new TextView(ctx);
        lbl.setText(span);
        lbl.setTextColor(C_TEXT);
        lbl.setTextSize(12f);
        LayoutParams llp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        llp.setMargins(dp(10), 0, 0, 0);
        lbl.setLayoutParams(llp);

        r.addView(box);
        r.addView(lbl);
        r.setOnClickListener(v -> {
            if (!authManager.isKeyValid()) {
                android.widget.Toast.makeText(getContext(), "Key Expired! Please re-login.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            st[0] = !st[0];
            prefs.edit().putBoolean(key, st[0]).apply();
            sendConfigToCpp(prefs);
            updateBox.run();
            radar.invalidate();
        });
        return r;
    }

    private View buildToggle(Context ctx, boolean init, TCb cb) {
        final boolean[] on = {init};
        LinearLayout track = new LinearLayout(ctx);
        track.setGravity(init ? Gravity.END|Gravity.CENTER_VERTICAL : Gravity.START|Gravity.CENTER_VERTICAL);
        track.setPadding(dp(4), dp(4), dp(4), dp(4));
        track.setLayoutParams(new LayoutParams(dp(44), dp(24)));
        
        final GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(init ? C_ACCENT : Color.argb(40, 160, 160, 184)); 
        tbg.setCornerRadius(dp(100));
        track.setBackground(tbg);
        
        View thumb = new View(ctx); 
        thumb.setLayoutParams(new LayoutParams(dp(16), dp(16)));
        GradientDrawable thbg = new GradientDrawable(); 
        thbg.setShape(GradientDrawable.OVAL);
        thbg.setColor(Color.WHITE); 
        thumb.setBackground(thbg); 
        track.addView(thumb);
        
        track.setOnClickListener(v -> {
            on[0] = !on[0];
            tbg.setColor(on[0] ? C_ACCENT : Color.argb(40, 160, 160, 184));
            track.setGravity(on[0] ? Gravity.END|Gravity.CENTER_VERTICAL
                                   : Gravity.START|Gravity.CENTER_VERTICAL);
            cb.t(on[0]);
        });
        return track;
    }

    private View slider(Context ctx, String title, String key, float min, float max, float def) {
        LinearLayout c = new LinearLayout(ctx); c.setOrientation(VERTICAL); c.setPadding(0, dp(6), 0, dp(6));
        
        LinearLayout lr = new LinearLayout(ctx); lr.setGravity(Gravity.CENTER_VERTICAL);
        TextView tt = new TextView(ctx); tt.setText(title); tt.setTextColor(C_SUBTEXT); tt.setTextSize(11f);
        tt.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)); lr.addView(tt);
        
        float cur = prefs.getFloat(key, def);
        TextView tv = new TextView(ctx); tv.setText(String.format("%.0f", cur));
        tv.setTextColor(C_ACCENT); tv.setTextSize(11f); tv.setTypeface(null, Typeface.BOLD); lr.addView(tv);
        c.addView(lr);

        LinearLayout controls = new LinearLayout(ctx);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(4), 0, 0);

        SeekBar sb = new SeekBar(ctx); sb.setMax(100);
        sb.setProgress((int)(((cur-min)/(max-min))*100)); 
        sb.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sb.setProgressTintList(android.content.res.ColorStateList.valueOf(C_ACCENT));
            sb.setThumbTintList(android.content.res.ColorStateList.valueOf(C_ACCENT));
        }

        // Helper to update value
        java.util.function.Consumer<Float> updateVal = (v) -> {
            float finalV = Math.max(min, Math.min(max, v));
            tv.setText(String.format("%.0f", finalV));
            sb.setProgress((int)(((finalV-min)/(max-min))*100));
            prefs.edit().putFloat(key, finalV).apply();
            sendConfigToCpp(prefs);
            radar.invalidate();
        };

        TextView btnMinus = new TextView(ctx);
        btnMinus.setText("−"); btnMinus.setTextColor(C_TEXT); btnMinus.setTextSize(16f);
        btnMinus.setPadding(dp(10), dp(5), dp(10), dp(5));
        btnMinus.setBackground(pillBtnBg(C_BTN_DRK));
        btnMinus.setOnClickListener(v -> {
            float val = prefs.getFloat(key, def) - 1;
            updateVal.accept(val);
        });

        TextView btnPlus = new TextView(ctx);
        btnPlus.setText("+"); btnPlus.setTextColor(C_TEXT); btnPlus.setTextSize(16f);
        btnPlus.setPadding(dp(10), dp(5), dp(10), dp(5));
        btnPlus.setBackground(pillBtnBg(C_BTN_DRK));
        btnPlus.setOnClickListener(v -> {
            float val = prefs.getFloat(key, def) + 1;
            updateVal.accept(val);
        });

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                if (!u) return;
                float v = min+((max-min)*(p/100f));
                tv.setText(String.format("%.0f", v));
                prefs.edit().putFloat(key, v).apply();
                sendConfigToCpp(prefs);
                radar.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        controls.addView(btnMinus);
        controls.addView(sb);
        controls.addView(btnPlus);
        c.addView(controls); 
        return c;
    }

    private GradientDrawable pillBtnBg(int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(8));
        return gd;
    }

    private View radioRow(Context ctx, String key, String[] opts) {
        RadioGroup rg = new RadioGroup(ctx); rg.setOrientation(HORIZONTAL); rg.setPadding(0,dp(4),0,dp(4));
        int cur = prefs.getInt(key, 0);
        for (int i = 0; i < opts.length; i++) {
            RadioButton rb = new RadioButton(ctx); rb.setText(opts[i]); rb.setTextColor(C_TEXT);
            rb.setTextSize(11.5f); rb.setId(i);
            if (i == cur) rb.setChecked(true);
            rb.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((g,id) -> {
            prefs.edit().putInt(key,id).apply();
            sendConfigToCpp(prefs);
        });
        return rg;
    }

    private View radioRowVertical(Context ctx, String key, String[] opts) {
        RadioGroup rg = new RadioGroup(ctx);
        rg.setOrientation(VERTICAL);
        int cur = prefs.getInt(key, 0);
        for (int i = 0; i < opts.length; i++) {
            RadioButton rb = new RadioButton(ctx);
            rb.setText(opts[i]);
            rb.setTextColor(C_TEXT);
            rb.setTextSize(11.5f);
            rb.setId(i);
            rb.setPadding(0, 0, 0, 0);
            rb.setGravity(Gravity.CENTER_VERTICAL);
            if (i == cur) rb.setChecked(true);
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((g,id) -> {
            prefs.edit().putInt(key,id).apply();
            sendConfigToCpp(prefs);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START;
        rg.setLayoutParams(lp);
        return rg;
    }

    private View btn(Context ctx, String text, int color, Runnable r) {
        TextView b = new TextView(ctx); b.setText(text); b.setTextColor(C_TEXT);
        b.setTextSize(12f); b.setGravity(Gravity.CENTER); b.setPadding(0, dp(9), 0, dp(9));
        b.setTypeface(null, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(8));
        b.setBackground(bg);
        LayoutParams blp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, dp(4), 0, dp(4)); b.setLayoutParams(blp);
        b.setOnClickListener(v -> r.run()); return b;
    }

    private TextView pillBtn(Context ctx, String text, int tc, int bgC) {
        TextView tv = new TextView(ctx); tv.setText(text); tv.setTextColor(tc);
        tv.setTextSize(12f); tv.setGravity(Gravity.CENTER); tv.setPadding(dp(10),dp(6),dp(10),dp(6));
        GradientDrawable d = new GradientDrawable(); d.setColor(bgC); d.setCornerRadius(dp(6));
        tv.setBackground(d); return tv;
    }

    private View vgap(Context ctx, int dpVal) {
        View v = new View(ctx); v.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(dpVal)));
        return v;
    }

    private void showCollapsed() { panel.setVisibility(GONE); tvPill.setVisibility(VISIBLE); }
    private void showExpanded()  { tvPill.setVisibility(GONE); panel.setVisibility(VISIBLE); }
    private String formatTime(long seconds) {
        if (seconds <= 0) return "Expired";
        if (seconds > 86400) return (seconds / 86400) + " Day";
        if (seconds > 3600) return (seconds / 3600) + "";
        return (seconds / 60) + " Minute";
    }

    private int dp(int v) { return (int)(v * getContext().getResources().getDisplayMetrics().density); }

    // ==================== DRAG ====================
    private final OnTouchListener dragL = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            boolean locked = prefs.getBoolean("ui_lock", false);
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    tx = e.getRawX(); ty = e.getRawY();
                    ix = lp.x;       iy = lp.y;
                    dragging = false;
                    fetchRealScreenSize();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (locked) return true;
                    int dx = (int)(e.getRawX() - tx);
                    int dy = (int)(e.getRawY() - ty);
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        dragging = true;
                        int viewW = getWidth();
                        int viewH = getHeight();
                        float scale = prefs.getFloat("ui_scale", 1.0f);
                        int scaledW = (int)(viewW * scale);
                        int scaledH = (int)(viewH * scale);
                        
                        // Fix: Using 0 to allow moving to very edge, and properly calculate maxX/Y
                        int maxX = realScreenW - (v == tvPill ? viewW : scaledW);
                        int maxY = realScreenH - (v == tvPill ? viewH : scaledH);
                        
                        lp.x = Math.max(0, Math.min(ix + dx, maxX));
                        lp.y = Math.max(0, Math.min(iy + dy, maxY));
                        wm.updateViewLayout(OverlayView.this, lp);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging && v == tvPill) showExpanded();
                    else if (!dragging) v.performClick();
                    return true;
            }
            return false;
        }
    };
    
// Interface for dialog item selection callback
    public interface DialogCallback {
        void onSelect(String item);
    }

    // Custom UI Dialog Floating Ala Premium Mod
    private void showModernDialog(Context ctx, String title, String[] items, final DialogCallback callback) {
        final FrameLayout dimBg = new FrameLayout(ctx);
        dimBg.setBackgroundColor(Color.argb(180, 0, 0, 0)); // Background gelap blur
        dimBg.setOnClickListener(v -> { try { wm.removeView(dimBg); } catch (Exception ignored) {} }); // Click outside to close

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(C_CARD);
        gd.setCornerRadius(dp(16));
        gd.setStroke(dp(1), Color.argb(100, 255, 215, 0));// Border neon tipis
        card.setBackground(gd);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        
        // Prevent card click from bubbling up to dimBg (which would close the dialog)
        card.setOnClickListener(v -> {});

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextColor(C_ACCENT);
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, dp(15));
        card.addView(tvTitle);

        ScrollView sv = new ScrollView(ctx);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(VERTICAL);

        for (final String item : items) {
            TextView btn = new TextView(ctx);
            // Color warning tags red: "(ultimate lock)" and "(maybe bug)"
            android.text.SpannableString spanItem = new android.text.SpannableString(item);
            String[] redTags = {"ultimate", "Experimental"};
            for (String tag : redTags) {
                int idx = item.toLowerCase().indexOf(tag.toLowerCase());
                if (idx >= 0) {
                    spanItem.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#EF5350")),
                            idx, idx + tag.length(), 0);
                }
            }
            btn.setText(spanItem);
            btn.setTextColor(C_TEXT);
            btn.setPadding(dp(12), dp(12), dp(12), dp(12));
            btn.setTextSize(13f);
            btn.setGravity(Gravity.CENTER);
            btn.setTypeface(null, Typeface.BOLD);

            GradientDrawable bbg = new GradientDrawable();
            bbg.setColor(C_BG);
            bbg.setCornerRadius(dp(8));
            btn.setBackground(bbg);

            LayoutParams blp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            blp.setMargins(0, 0, 0, dp(8));
            btn.setLayoutParams(blp);

            btn.setOnClickListener(v -> {
                try { wm.removeView(dimBg); } catch (Exception ignored) {}
                callback.onSelect(item);
            });
            list.addView(btn);
        }
        sv.addView(list);

        LayoutParams svLp = new LayoutParams(dp(240), LayoutParams.WRAP_CONTENT);
        card.addView(sv, svLp);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        dimBg.addView(card, cardLp);

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        );
        try { wm.addView(dimBg, lp); } catch (Exception ignored) {}
    }
    
    private LinearLayout roomTableContainer;
    private boolean isRoomSocketRunning = false;

    private LinearLayout buildRoomInfo(Context ctx) {
        LinearLayout t = new LinearLayout(ctx); 
        t.setOrientation(VERTICAL);

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "ROOM INFO & DRAFT PICK"));
            
            // TOMBOL ON/OFF AGAR TIDAK BIKIN LAG!
            l.addView(toggleRow(ctx, "Enable Room Info", "Show player data", "room_info_enable", false));
            l.addView(vgap(ctx, 10));

            roomTableContainer = new LinearLayout(ctx);
            roomTableContainer.setOrientation(VERTICAL);
            l.addView(roomTableContainer);
        }));

        if (!isRoomSocketRunning) {
            isRoomSocketRunning = true;
            startRoomSocketThread();
        }

        return t;
    }

    // Helper class for temporary parsed player data
    private static class RoomPlayerData {
        int camp, uid, zone, heroId, rank, mythPt, matches, wins, accLv, spellId, countryId;
        boolean isLeader;
        String name;
    }

    private void startRoomSocketThread() {
        new Thread(() -> {
            while (isRoomSocketRunning) {
                android.net.LocalSocket socket = null;
                java.io.DataInputStream dis = null;
                try {
                    socket = new android.net.LocalSocket();
                    socket.connect(new android.net.LocalSocketAddress("and.sys.sensor.data", android.net.LocalSocketAddress.Namespace.ABSTRACT));
                    dis = new java.io.DataInputStream(socket.getInputStream());

                    byte[] countBuf = new byte[4];
                    // NEW SIZE: 12 integers * 4 bytes + 32 char bytes = 80 bytes
                    byte[] packetBuf = new byte[80];

                    while (isRoomSocketRunning) {
                        dis.readFully(countBuf);
                        int count = java.nio.ByteBuffer.wrap(countBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
                        if (count > 10 || count < 0) count = 0;

                        final java.util.List<RoomPlayerData> players = new java.util.ArrayList<>();
                        
                        // Always read data from C++ to prevent buffer overflow,
                        // but only parse and render if the toggle is enabled.
                        boolean isEnabled = prefs.getBoolean("room_info_enable", false);

                        for (int i = 0; i < count; i++) {
                            dis.readFully(packetBuf);
                            
                            if (!isEnabled) continue; // Skip CPU-heavy parsing when disabled

                            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(packetBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                            RoomPlayerData p = new RoomPlayerData();
                            p.camp = bb.getInt();
                            p.uid = bb.getInt();
                            p.zone = bb.getInt();
                            p.heroId = bb.getInt();
                            p.rank = bb.getInt();
                            p.mythPt = bb.getInt();
                            p.matches = bb.getInt();
                            p.wins = bb.getInt();
                            p.accLv = bb.getInt();
                            p.spellId = bb.getInt();
                            p.countryId = bb.getInt();
                            p.isLeader = bb.getInt() == 1;

                            byte[] nameBytes = new byte[32];
                            bb.get(nameBytes);
                            p.name = new String(nameBytes).trim();
                            
                            players.add(p);
                        }

                        // Update UI on Main Thread
                        post(() -> {
                            if (roomTableContainer == null) return;
                            roomTableContainer.removeAllViews();
                            
                            if (!isEnabled) {
                                TextView tv = new TextView(getContext());
                                tv.setText("Room Info is Disabled. Turn on to view.");
                                tv.setTextColor(C_SUBTEXT);
                                tv.setTextSize(12f);
                                roomTableContainer.addView(tv);
                                return;
                            }

                            if (players.isEmpty()) {
                                TextView tv = new TextView(getContext());
                                tv.setText("Waiting data...");
                                tv.setTextColor(C_ACCENT);
                                tv.setTextSize(12f);
                                roomTableContainer.addView(tv);
                                return;
                            }

                            // --- LOGIKA AUTO-GROUPING TEAM ---
java.util.List<RoomPlayerData> blueTeam = new java.util.ArrayList<>();
java.util.List<RoomPlayerData> redTeam = new java.util.ArrayList<>();

// 1. Pisahkan berdasarkan Camp (1 = Biru/Ally, 2 = Merah/Enemy)
for (RoomPlayerData p : players) {
    if (p.camp == 1) {
        blueTeam.add(p);
    } else {
        redTeam.add(p);
    }
}

// 2. Render Team Biru (Ally)
if (!blueTeam.isEmpty()) {
    roomTableContainer.addView(createTeamLabel(getContext(), "ALLY TEAM", android.graphics.Color.parseColor("#42A5F5")));
    for (RoomPlayerData p : blueTeam) {
        roomTableContainer.addView(createPlayerCard(p));
    }
}

// 3. Render Pemisah (VS)
if (!blueTeam.isEmpty() && !redTeam.isEmpty()) {
    roomTableContainer.addView(createVSDivider(getContext()));
}

// 4. Render Team Merah (Enemy)
if (!redTeam.isEmpty()) {
    roomTableContainer.addView(createTeamLabel(getContext(), "ENEMY TEAM", android.graphics.Color.parseColor("#EF5350")));
    for (RoomPlayerData p : redTeam) {
        roomTableContainer.addView(createPlayerCard(p));
    }
}
                        });
                    }
                } catch (Exception e) {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                } finally {
                    try { if (dis != null) dis.close(); } catch (Exception ignored) {}
                    try { if (socket != null) socket.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }
    
    // Membuat Label Header (ALLY TEAM / ENEMY TEAM)
    private View createTeamLabel(Context ctx, String text, int color) {
        android.widget.TextView tv = new android.widget.TextView(ctx);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(11f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setLetterSpacing(0.1f);
        
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(4), 0, dp(8));
        tv.setLayoutParams(lp);
        
        return tv;
    }

    // Membuat Garis Pemisah (VS) yang keren
    private View createVSDivider(Context ctx) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(HORIZONTAL);
        container.setGravity(Gravity.CENTER);
        
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(12));
        container.setLayoutParams(lp);

        // Garis Kiri
        View lineLeft = new View(ctx);
        lineLeft.setBackgroundColor(Color.argb(80, 255, 255, 255));
        lineLeft.setLayoutParams(new LayoutParams(0, dp(1), 1f));

        // Teks VS
        android.widget.TextView tvVs = new android.widget.TextView(ctx);
        tvVs.setText(" VS ");
        tvVs.setTextColor(C_SUBTEXT);
        tvVs.setTextSize(10f);
        tvVs.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
        tvVs.setPadding(dp(8), 0, dp(8), 0);

        // Garis Kanan
        View lineRight = new View(ctx);
        lineRight.setBackgroundColor(Color.argb(80, 255, 255, 255));
        lineRight.setLayoutParams(new LayoutParams(0, dp(1), 1f));

        container.addView(lineLeft);
        container.addView(tvVs);
        container.addView(lineRight);

        return container;
    }

// ==========================================
    // UI BUILDER UNTUK PLAYER CARD (+ GAMBAR ICON)
    // ==========================================
    private View createPlayerCard(RoomPlayerData p) {
        Context ctx = getContext();
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        
        GradientDrawable bg = new GradientDrawable();
        if (p.camp == 1) {
            bg.setColor(Color.parseColor("#102030"));
            bg.setStroke(dp(1), Color.parseColor("#1565C0")); 
        } else {
            bg.setColor(Color.parseColor("#301010"));
            bg.setStroke(dp(1), Color.parseColor("#C62828")); 
        }
        bg.setCornerRadius(dp(8));
        card.setBackground(bg);
        
        LayoutParams cardLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(6));
        card.setLayoutParams(cardLp);

        // LEFT SECTION: Hero Icon & Spell Icon (with fallback ID)
        LinearLayout colLeft = new LinearLayout(ctx);
        colLeft.setOrientation(HORIZONTAL);
        colLeft.setGravity(Gravity.CENTER_VERTICAL);
        colLeft.setLayoutParams(new LayoutParams(dp(80), LayoutParams.WRAP_CONTENT));
        
// --- 1. HERO ICON ---
        FrameLayout heroContainer = new FrameLayout(ctx);
        heroContainer.setLayoutParams(new LayoutParams(dp(36), dp(36)));

        android.widget.ImageView ivHero = new android.widget.ImageView(ctx);
        ivHero.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        ivHero.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        
        // Use raw hero ID from game directly (no division formula)
        String heroFileName = getHeroNameStr(p.heroId); 
        android.graphics.Bitmap heroBmp = radar.getRawIcon(heroFileName);

        if (heroBmp == null && p.name != null) {
            heroBmp = radar.getRawIcon(p.name.toLowerCase().replaceAll("[^a-z0-9]", ""));
        }

        if (heroBmp != null) {
            ivHero.setImageBitmap(heroBmp);
            heroContainer.addView(ivHero);
        } else {
            ivHero.setBackgroundColor(Color.parseColor("#444444"));
            heroContainer.addView(ivHero);
            
            TextView tvHeroId = new TextView(ctx);
            tvHeroId.setText(String.valueOf(p.heroId));
            tvHeroId.setTextColor(Color.WHITE);
            tvHeroId.setTextSize(9f);
            tvHeroId.setGravity(Gravity.CENTER);
            tvHeroId.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            heroContainer.addView(tvHeroId);
        }
        
        // --- 2. SPELL ICON ---
        FrameLayout spellContainer = new FrameLayout(ctx);
        LayoutParams spellLp = new LayoutParams(dp(22), dp(22));
        spellLp.setMargins(dp(6), 0, 0, 0); 
        spellContainer.setLayoutParams(spellLp);

        android.widget.ImageView ivSpell = new android.widget.ImageView(ctx);
        ivSpell.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        ivSpell.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        
        String spellFileName = getSpellNameStr(p.spellId);
        android.graphics.Bitmap spellBmp = radar.getRawIcon(spellFileName);
        
        if (spellBmp != null) {
            ivSpell.setImageBitmap(spellBmp);
            spellContainer.addView(ivSpell);
        } else {
            // If spell image not found, show box with numeric ID
            ivSpell.setBackgroundColor(Color.parseColor("#444444"));
            spellContainer.addView(ivSpell);
            
            TextView tvSpellId = new TextView(ctx);
            tvSpellId.setText(String.valueOf(p.spellId)); // Shows actual MLBB spell ID
            tvSpellId.setTextColor(Color.WHITE);
            tvSpellId.setTextSize(6f);
            tvSpellId.setGravity(Gravity.CENTER);
            tvSpellId.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            spellContainer.addView(tvSpellId);
        }

        colLeft.addView(heroContainer);
        colLeft.addView(spellContainer);

        // BAGIAN TENGAH: Nama, UID, Level Akun
        LinearLayout colMid = new LinearLayout(ctx);
        colMid.setOrientation(VERTICAL);
        colMid.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        
        TextView tvName = new TextView(ctx);
        String leaderIcon = p.isLeader ? "👑 " : "";
        tvName.setText(leaderIcon + p.name);
        tvName.setTextColor(C_TEXT);
        tvName.setTextSize(12f);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setSingleLine(true);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END); // Truncate long names with "..."

        TextView tvUid = new TextView(ctx);
        tvUid.setText("UID: " + p.uid + " | Lv." + p.accLv);
        tvUid.setTextColor(C_SUBTEXT);
        tvUid.setTextSize(9.5f);

        colMid.addView(tvName);
        colMid.addView(tvUid);

        // BAGIAN KANAN: Rank & Winrate
        LinearLayout colRight = new LinearLayout(ctx);
        colRight.setOrientation(VERTICAL);
        colRight.setGravity(Gravity.END);
        
        // Add margin so text doesn't hug the right edge
        LayoutParams rightLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        rightLp.setMargins(dp(10), 0, dp(5), 0); 
        colRight.setLayoutParams(rightLp);
        
        TextView tvRank = new TextView(ctx);
        tvRank.setText(getRankName(p.rank, p.mythPt));
        tvRank.setTextColor(Color.parseColor("#FFD700"));
        tvRank.setTextSize(10f);
        tvRank.setTypeface(null, Typeface.BOLD);

        float wr = (p.matches > 0) ? ((float)p.wins / p.matches * 100f) : 0f;
        TextView tvWr = new TextView(ctx);
        tvWr.setText(String.format("%.1f%% (%d M)", wr, p.matches));
        tvWr.setTextSize(10.5f);
        
        if (wr >= 65.0f && p.matches >= 20) tvWr.setTextColor(Color.parseColor("#00E676")); 
        else if (wr >= 50.0f) tvWr.setTextColor(Color.parseColor("#FFA726")); 
        else if (wr > 0f && p.matches >= 10) tvWr.setTextColor(Color.parseColor("#EF5350")); 
        else tvWr.setTextColor(Color.LTGRAY); 

        colRight.addView(tvRank);
        colRight.addView(tvWr);

        card.addView(colLeft);
        card.addView(colMid);
        card.addView(colRight);

        return card;
    }
    
    
    // Array Rank Lengkap 136 Index (Sesuai source C++ kamu)
    private final String[] strRank = {
        "Warrior III *1", "Warrior III *2", "Warrior III *3",
        "Warrior II *0", "Warrior II *1", "Warrior II *2", "Warrior II *3",
        "Warrior I *0", "Warrior I *1", "Warrior I *2", "Warrior I *3",
        "Elite III *0", "Elite III *1", "Elite III *2", "Elite III *3", "Elite III *4",
        "Elite II *0", "Elite II *1", "Elite II *2", "Elite II *3", "Elite II *4",
        "Elite I *0", "Elite I *1", "Elite I *2", "Elite I *3", "Elite I *4",
        "Master IV *0", "Master IV *1", "Master IV *2", "Master IV *3", "Master IV *4",
        "Master III *0", "Master III *1", "Master III *2", "Master III *3", "Master III *4",
        "Master II *0", "Master II *1", "Master II *2", "Master II *3", "Master II *4",
        "Master I *0", "Master I *1", "Master I *2", "Master I *3", "Master I *4",
        "Grandmaster V *0", "Grandmaster V *1", "Grandmaster V *2", "Grandmaster V *3", "Grandmaster V *4", "Grandmaster V *5",
        "Grandmaster IV *0", "Grandmaster IV *1", "Grandmaster IV *2", "Grandmaster IV *3", "Grandmaster IV *4", "Grandmaster IV *5",
        "Grandmaster III *0", "Grandmaster III *1", "Grandmaster III *2", "Grandmaster III *3", "Grandmaster III *4", "Grandmaster III *5",
        "Grandmaster II *0", "Grandmaster II *1", "Grandmaster II *2", "Grandmaster II *3", "Grandmaster II *4", "Grandmaster II *5",
        "Grandmaster I *0", "Grandmaster I *1", "Grandmaster I *2", "Grandmaster I *3", "Grandmaster I *4", "Grandmaster I *5",
        "Epic V *0", "Epic V *1", "Epic V *2", "Epic V *3", "Epic V *4", "Epic V *5",
        "Epic IV *0", "Epic IV *1", "Epic IV *2", "Epic IV *3", "Epic IV *4", "Epic IV *5",
        "Epic III *0", "Epic III *1", "Epic III *2", "Epic III *3", "Epic III *4", "Epic III *5",
        "Epic II *0", "Epic II *1", "Epic II *2", "Epic II *3", "Epic II *4", "Epic II *5",
        "Epic I *0", "Epic I *1", "Epic I *2", "Epic I *3", "Epic I *4", "Epic I *5",
        "Legend V *0", "Legend V *1", "Legend V *2", "Legend V *3", "Legend V *4", "Legend V *5",
        "Legend IV *0", "Legend IV *1", "Legend IV *2", "Legend IV *3", "Legend IV *4", "Legend IV *5",
        "Legend III *0", "Legend III *1", "Legend III *2", "Legend III *3", "Legend III *4", "Legend III *5",
        "Legend II *0", "Legend II *1", "Legend II *2", "Legend II *3", "Legend II *4", "Legend II *5",
        "Legend I *0", "Legend I *1", "Legend I *2", "Legend I *3", "Legend I *4", "Legend I *5"
    };

    private String getRankName(int rankLevel, int mythPoint) {
        if (rankLevel <= 0) return "Unranked";
        if (rankLevel < strRank.length) { 
            return strRank[rankLevel];
        } else {
            int star = rankLevel - 136; 
            if (star > 99) return "Immortal *" + star;
            if (star > 49) return "Glory *" + star;
            if (star > 24) return "Honor *" + star;
            return "Mythic *" + star;
        }
    }

    // ==========================================
    // TRANSLATOR: SPELL ID -> NAMA FILE PNG/WEBP
    // ==========================================
    private String getSpellNameStr(int spellId) {
        switch (spellId) {
            case 20150: return "execute";
            case 20020: return "retribution";
            case 20030: return "inspire";
            case 20040: return "sprint";
            case 20050: return "revitalize";
            case 20060: return "aegis";
            case 20070: return "petrify";
            case 20080: return "purify";
            case 20100: return "flicker";
            case 20140: return "flameshot";
            case 20110: return "arrival";
            case 20190: return "vengeance";
            // JIKA NANTI EXECUTE TETAP ANGKA (Misal 80001), TAMBAHKAN DI SINI:
            // case 80001: return "execute";
            default: return "unknown_spell"; 
        }
    }

    // ==========================================
    // TRANSLATOR LENGKAP: HERO ID -> NAMA FILE
    // ==========================================
    private String getHeroNameStr(int heroId) {
    switch(heroId) {
        case 1: return "miya";
        case 2: return "balmond";
        case 3: return "saber";
        case 4: return "alice";
        case 5: return "nana";
        case 6: return "tigreal";
        case 7: return "alucard";
        case 8: return "karina";
        case 9: return "akai";
        case 10: return "franco";
        case 11: return "bane";
        case 12: return "bruno";
        case 13: return "clint";
        case 14: return "rafaela";
        case 15: return "eudora";
        case 16: return "zilong";
        case 17: return "fanny";
        case 18: return "layla";
        case 19: return "minotaur";
        case 20: return "lolita";
        case 21: return "hayabusa";
        case 22: return "freya";
        case 23: return "gord";
        case 24: return "natalia";
        case 25: return "kagura";
        case 26: return "chou";
        case 27: return "sun";
        case 28: return "alpha";
        case 29: return "ruby";
        case 30: return "yisunshin";
        case 31: return "moskov";
        case 32: return "johnson";
        case 33: return "cyclops";
        case 34: return "estes";
        case 35: return "hilda";
        case 36: return "aurora";
        case 37: return "lapulapu";
        case 38: return "vexana";
        case 39: return "roger";
        case 40: return "karrie";
        case 41: return "gatotkaca";
        case 42: return "harley";
        case 43: return "irithel";
        case 44: return "grock";
        case 45: return "argus";
        case 46: return "odette";
        case 47: return "lancelot";
        case 48: return "diggie";
        case 49: return "hylos";
        case 50: return "zhask";
        case 51: return "helcurt";
        case 52: return "pharsa";
        case 53: return "lesley";
        case 54: return "jawhead";
        case 55: return "angela";
        case 56: return "gusion";
        case 57: return "valir";
        case 58: return "martis";
        case 59: return "uranus";
        case 60: return "hanabi";
        case 61: return "change";
        case 62: return "kaja";
        case 63: return "selena";
        case 64: return "aldous";
        case 65: return "claude";
        case 66: return "vale";
        case 67: return "leomord";
        case 68: return "lunox";
        case 69: return "hanzo";
        case 70: return "belerick";
        case 71: return "kimmy";
        case 72: return "thamuz";
        case 73: return "harith";
        case 74: return "minsitthar";
        case 75: return "kadita";
        case 76: return "faramis";
        case 77: return "badang";
        case 78: return "khufra";
        case 79: return "granger";
        case 80: return "guinevere";
        case 81: return "esmeralda";
        case 82: return "terizla";
        case 83: return "xborg";
        case 84: return "ling";
        case 85: return "dyrroth";
        case 86: return "lylia";
        case 87: return "baxia";
        case 88: return "masha";
        case 89: return "wanwan";
        case 90: return "silvanna";
        case 91: return "cecilion";
        case 92: return "carmilla";
        case 93: return "atlas";
        case 94: return "popolandkupa";
        case 95: return "yuzhong";
        case 96: return "luoyi";
        case 97: return "benedetta";
        case 98: return "khaleed";
        case 99: return "barats";
        case 100: return "brody";
        case 101: return "yve";
        case 102: return "mathilda";
        case 103: return "paquito";
        case 104: return "gloo";
        case 105: return "beatrix";
        case 106: return "phoveus";
        case 107: return "natan";
        case 108: return "aulus";
        case 109: return "aamon";
        case 110: return "valentina";
        case 111: return "edith";
        case 112: return "floryn";
        case 113: return "yin";
        case 114: return "melissa";
        case 115: return "xavier";
        case 116: return "julian";
        case 117: return "fredrinn";
        case 118: return "joy";
        case 119: return "novaria";
        case 120: return "arlott";
        case 121: return "ixia";
        case 122: return "nolan";
        case 123: return "cici";
        case 124: return "chip";
        case 125: return "zhuxin";
        case 126: return "suyou";
        case 127: return "lukas";
        case 128: return "kalea";
        case 129: return "zetian";
        case 130: return "obsidia";
        case 131: return "sora";
        case 132: return "marcel";
        default: return "unknown_hero";
    }
}


    // ==================== PENGIRIMAN SOCKET KE C++ ====================
    private void sendConfigToCpp(SharedPreferences prefs) {
        socketExecutor.execute(() -> {
            try {
                android.net.LocalSocket socket = new android.net.LocalSocket();
                socket.connect(new android.net.LocalSocketAddress("and.sys.audio.config", android.net.LocalSocketAddress.Namespace.ABSTRACT));
                java.io.OutputStream out = socket.getOutputStream();
                
                // [MAGIC:4] [EXPIRY:8] [CONFIG_DATA:76] = 88 bytes
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(88);
                bb.order(java.nio.ByteOrder.LITTLE_ENDIAN);
                
              // 1. Security Header
bb.putInt(0x4D4C4242); // MAGIC "MLBB"
long expirySeconds = authManager.getExpiryTimestamp();
bb.putLong(expirySeconds * 1000); // Send as milliseconds

                // 2. Config Data
                int lingMode = prefs.getInt("ling_mode", 0);
                int lingManual = (lingMode == 1) ? 1 : 0;
                int lingAuto   = (lingMode == 2) ? 1 : 0;
                String selectedCombo = prefs.getString("selected_combo", "none");
                int activeCombo = 0;
                
                // FIX: Use .contains() so modified combo text is still detected correctly
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

                out.write(bb.array());
                out.flush();
                socket.close();
            } catch (Exception ignored) {}
        });
    }
}