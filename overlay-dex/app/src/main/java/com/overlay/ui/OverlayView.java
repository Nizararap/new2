package com.overlay.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.overlay.KeyAuthManager;
import com.overlay.RadarView;
import com.overlay.models.RoomPlayerData;
import com.overlay.service.NativeSender;
import com.overlay.ui.tabs.*;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends LinearLayout {

    private final WindowManager wm;
    private final WindowManager.LayoutParams lp;
    private final RadarView radar;
    private final SharedPreferences prefs;
    private final KeyAuthManager authManager;
    private final Context context;

    private int realScreenW;
    private int realScreenH;

    private float tx, ty;
    private int ix, iy;
    private boolean dragging;

    private LinearLayout panel, tabDash, tabRad, tabCombat, tabRoom, tabVisual;
    private TextView tvPill;
    private TextView[] tabBtns;
    private ScrollView scrollView;
    private LinearLayout roomTableContainer;
    private boolean isRoomSocketRunning = false;

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
        NativeSender.sendConfigToCpp(this.prefs, this.authManager, realScreenW, realScreenH);
    }

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
            Bitmap bmp = BitmapFactory.decodeStream(ctx.getAssets().open("background2.jpg"));
            if (bmp != null) {
                int pillSize = UIFactory.dp(ctx, 44);
                int w = bmp.getWidth();
                int h = bmp.getHeight();
                float scale = Math.min((float) pillSize / w, (float) pillSize / h);
                int newW = Math.round(w * scale);
                int newH = Math.round(h * scale);
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, newW, newH, true);
                
                Bitmap circle = Bitmap.createBitmap(pillSize, pillSize, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(circle);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                
                canvas.drawCircle(pillSize / 2f, pillSize / 2f, pillSize / 2f, paint);
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                
                int left = (pillSize - newW) / 2;
                int top = (pillSize - newH) / 2;
                canvas.drawBitmap(scaled, left, top, paint);
                
                BitmapDrawable bd = new BitmapDrawable(ctx.getResources(), circle);
                bd.setGravity(Gravity.CENTER);
                tvPill.setBackground(bd);
                tvPill.setPadding(0, 0, 0, 0);
            }
        } catch (Exception e) {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColors(new int[]{UIFactory.C_ACCENT, Color.parseColor("#8B7500")});
            bg.setOrientation(GradientDrawable.Orientation.TL_BR);
            tvPill.setBackground(bg);
            tvPill.setText("M");
            tvPill.setTextColor(Color.BLACK);
            tvPill.setTextSize(14f);
            tvPill.setTypeface(null, Typeface.BOLD);
            tvPill.setPadding(UIFactory.dp(ctx, 12), UIFactory.dp(ctx, 12), UIFactory.dp(ctx, 12), UIFactory.dp(ctx, 12));
        }

        tvPill.setElevation(UIFactory.dp(ctx, 6));
        tvPill.setOnTouchListener(dragL);

        LayoutParams pillLp = new LayoutParams(UIFactory.dp(ctx, 44), UIFactory.dp(ctx, 44));
        tvPill.setLayoutParams(pillLp);

        addView(tvPill);
    }

    private void buildPanel(Context ctx) {
        panel = new LinearLayout(ctx);
        panel.setOrientation(VERTICAL);
        
        LayoutParams panelLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        panel.setLayoutParams(panelLp);
        panel.setMinimumWidth(UIFactory.dp(ctx, 340)); 

        try {
            Bitmap bmp = BitmapFactory.decodeStream(ctx.getAssets().open("background.jpg"));
            if (bmp != null) {
                Bitmap overlay = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), bmp.getConfig());
                Canvas canvas = new Canvas(overlay);
                canvas.drawBitmap(bmp, 0, 0, null);
                canvas.drawColor(Color.argb(200, 0, 0, 0));
                
                BitmapDrawable bd = new BitmapDrawable(ctx.getResources(), overlay);
                panel.setBackground(bd);
            } else {
                panel.setBackgroundColor(UIFactory.C_BG);
            }
        } catch (Exception e) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(UIFactory.C_BG);
            bg.setCornerRadius(UIFactory.dp(ctx, 20));
            bg.setStroke(UIFactory.dp(ctx, 1), UIFactory.C_ACCENT);
            panel.setBackground(bg);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            panel.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), UIFactory.dp(ctx, 20));
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
        hbg.setColor(UIFactory.C_HEADER);
        hbg.setCornerRadii(new float[]{UIFactory.dp(ctx, 20), UIFactory.dp(ctx, 20), UIFactory.dp(ctx, 20), UIFactory.dp(ctx, 20), 0, 0, 0, 0});
        h.setBackground(hbg);
        h.setPadding(UIFactory.dp(ctx, 20), UIFactory.dp(ctx, 16), UIFactory.dp(ctx, 14), UIFactory.dp(ctx, 16));
        h.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(VERTICAL);
        col.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout titleRow = new LinearLayout(ctx);
        titleRow.setGravity(Gravity.BOTTOM);

        TextView t1 = new TextView(ctx);
        t1.setText("MONDEV");
        t1.setLetterSpacing(0.1f);
        t1.setTextColor(UIFactory.C_TEXT); t1.setTextSize(18f); t1.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        titleRow.addView(t1);

        TextView tBeta = new TextView(ctx);
        tBeta.setText(" beta");
        tBeta.setTextColor(UIFactory.C_ACCENT); tBeta.setTextSize(10f); tBeta.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        tBeta.setPadding(UIFactory.dp(ctx, 4), 0, 0, UIFactory.dp(ctx, 2));
        titleRow.addView(tBeta);

        col.addView(titleRow);

        long rem = authManager.getRemainingTime();
        String timeStr = formatTime(rem);

        TextView t2 = new TextView(ctx);
        t2.setText("Subscription: " + timeStr);
        t2.setTextColor(UIFactory.C_ACCENT); t2.setTextSize(10f);
        t2.setAlpha(0.8f);
        col.addView(t2);
        h.addView(col);

        TextView upgradeBtn = new TextView(ctx);
        upgradeBtn.setText("🔓 VIP");
        upgradeBtn.setTextColor(UIFactory.C_ACCENT);
        upgradeBtn.setTextSize(10f);
        upgradeBtn.setTypeface(null, Typeface.BOLD);
        upgradeBtn.setPadding(UIFactory.dp(ctx, 8), UIFactory.dp(ctx, 6), UIFactory.dp(ctx, 8), UIFactory.dp(ctx, 6));
        GradientDrawable upgradeBg = new GradientDrawable();
        upgradeBg.setColor(Color.argb(30, 212, 175, 55));
        upgradeBg.setCornerRadius(UIFactory.dp(ctx, 30));
        upgradeBtn.setBackground(upgradeBg);
        upgradeBtn.setOnClickListener(v -> showVipUpgradeDialog());
        h.addView(upgradeBtn);

        TextView minBtn = new TextView(ctx);
        minBtn.setText("—");
        minBtn.setTextColor(UIFactory.C_TEXT);
        minBtn.setTextSize(18f);
        minBtn.setPadding(UIFactory.dp(ctx, 12), UIFactory.dp(ctx, 8), UIFactory.dp(ctx, 12), UIFactory.dp(ctx, 8));
        minBtn.setOnClickListener(v -> showCollapsed());
        h.addView(minBtn);
        h.setOnTouchListener(dragL);
        return h;
    }

    private View buildTabs(Context ctx) {
        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(UIFactory.C_HEADER);
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(HORIZONTAL);
        bar.setPadding(UIFactory.dp(ctx, 12), UIFactory.dp(ctx, 2), UIFactory.dp(ctx, 12), UIFactory.dp(ctx, 10));
        String[] labels = {"DASHBOARD", "VISUAL", "RADAR", "COMBAT", "ROOM"};
        tabBtns = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            tabBtns[i] = new TextView(ctx);
            tabBtns[i].setText(labels[i]);
            tabBtns[i].setTextSize(10f); 
            tabBtns[i].setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            tabBtns[i].setLetterSpacing(0.08f);
            tabBtns[i].setPadding(UIFactory.dp(ctx, 16), UIFactory.dp(ctx, 8), UIFactory.dp(ctx, 16), UIFactory.dp(ctx, 8));
            tabBtns[i].setGravity(Gravity.CENTER);
            LayoutParams tlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tlp.setMargins(0, 0, UIFactory.dp(ctx, 6), 0);
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
            tabBtns[i].setTextColor(a ? UIFactory.C_ACCENT : UIFactory.C_SUBTEXT);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(a ? Color.argb(40, 255, 215, 0) : Color.argb(15, 255, 255, 255));
            tbg.setCornerRadius(UIFactory.dp(context, 12));
            if (a) tbg.setStroke(UIFactory.dp(context, 1), Color.argb(150, 255, 215, 0));
            else tbg.setStroke(UIFactory.dp(context, 1), Color.argb(20, 255, 255, 255));
            tabBtns[i].setBackground(tbg);
        }
        if (tabDash   != null) tabDash.setVisibility(idx == 0 ? VISIBLE : GONE);
        if (tabVisual != null) tabVisual.setVisibility(idx == 1 ? VISIBLE : GONE);
        if (tabRad    != null) tabRad.setVisibility(idx == 2 ? VISIBLE : GONE);
        if (tabCombat != null) tabCombat.setVisibility(idx == 3 ? VISIBLE : GONE);
        if (tabRoom   != null) tabRoom.setVisibility(idx == 4 ? VISIBLE : GONE);
    }

    private View buildContent(Context ctx) {
        scrollView = new ScrollView(ctx);
        scrollView.setFillViewport(false);

        FrameLayout frame = new FrameLayout(ctx);
        frame.setPadding(UIFactory.dp(ctx, 10), UIFactory.dp(ctx, 8), UIFactory.dp(ctx, 10), UIFactory.dp(ctx, 10));

        roomTableContainer = new LinearLayout(ctx);
        roomTableContainer.setOrientation(VERTICAL);

        tabDash   = TabDashboard.build(ctx, prefs, authManager, radar, realScreenW, realScreenH, this::refreshAllUI, () -> applyUIScale(prefs.getFloat("ui_scale", 1.0f)));
        tabRad    = TabRadar.build(ctx, prefs, authManager, radar, realScreenW, realScreenH);
        tabCombat = TabCombat.build(ctx, prefs, authManager, radar, realScreenW, realScreenH, this::showModernDialog);
        tabRoom   = TabRoom.build(ctx, prefs, authManager, radar, realScreenW, realScreenH, roomTableContainer);
        tabVisual = TabVisual.build(ctx, prefs, authManager, radar, realScreenW, realScreenH);

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tabDash.setLayoutParams(flp);
        tabRad.setLayoutParams(flp);
        tabCombat.setLayoutParams(flp);
        tabRoom.setLayoutParams(flp);
        tabVisual.setLayoutParams(flp);

        frame.addView(tabDash);
        frame.addView(tabRad);
        frame.addView(tabCombat);
        frame.addView(tabRoom);
        frame.addView(tabVisual);
        scrollView.addView(frame);

        scrollView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (!isRoomSocketRunning) {
            isRoomSocketRunning = true;
            startRoomSocketThread();
        }

        return scrollView;
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
        
        tabDash   = TabDashboard.build(ctx, prefs, authManager, radar, realScreenW, realScreenH, this::refreshAllUI, () -> applyUIScale(prefs.getFloat("ui_scale", 1.0f)));
        tabRad    = TabRadar.build(ctx, prefs, authManager, radar, realScreenW, realScreenH);
        tabCombat = TabCombat.build(ctx, prefs, authManager, radar, realScreenW, realScreenH, this::showModernDialog);
        tabRoom   = TabRoom.build(ctx, prefs, authManager, radar, realScreenW, realScreenH, roomTableContainer);
        tabVisual = TabVisual.build(ctx, prefs, authManager, radar, realScreenW, realScreenH);

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tabDash.setLayoutParams(flp);
        tabRad.setLayoutParams(flp);
        tabCombat.setLayoutParams(flp);
        tabRoom.setLayoutParams(flp);
        tabVisual.setLayoutParams(flp);

        frame.addView(tabDash);
        frame.addView(tabRad);
        frame.addView(tabCombat);
        frame.addView(tabRoom);
        frame.addView(tabVisual);

        switchTab(0);
    }

    public void onRemoteConfigUpdated() {
        refreshAllUI();
        NativeSender.sendConfigToCpp(prefs, authManager, realScreenW, realScreenH);
    }

    private void showCollapsed() { panel.setVisibility(GONE); tvPill.setVisibility(VISIBLE); }
    private void showExpanded()  { tvPill.setVisibility(GONE); panel.setVisibility(VISIBLE); }

    private String formatTime(long seconds) {
        if (seconds <= 0) return "Expired";
        if (seconds > 86400) return (seconds / 86400) + " Day";
        if (seconds > 3600) return (seconds / 3600) + " Hour";
        return (seconds / 60) + " Minute";
    }

    private void showVipUpgradeDialog() {
        final boolean isVip = prefs.getBoolean("is_vip_user", false);
        String deviceId = authManager.getDeviceId();
        String statusText = isVip ? "🌟 VIP ACTIVE" : "🔓 FREE USER";
        
        String[] items;
        if (isVip) {
            items = new String[]{"Status: " + statusText, "Device ID: " + deviceId, "✅ Already VIP", "📋 Copy Device ID"};
        } else {
            items = new String[]{"Status: " + statusText, "Device ID: " + deviceId, "🔍 Check Upgrade", "📋 Copy Device ID"};
        }
        
        showModernDialog("VIP STATUS", items, selected -> {
            if (selected.contains("Copy Device ID")) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("DeviceID", deviceId));
                    Toast.makeText(getContext(), "Device ID copied!", Toast.LENGTH_SHORT).show();
                }
            } else if (selected.contains("Check Upgrade") && !isVip) {
                performVipCheck();
            } else if (selected.contains("Already VIP")) {
                Toast.makeText(getContext(), "You are already VIP!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performVipCheck() {
        long lastCheck = prefs.getLong("last_vip_check", 0);
        long now = System.currentTimeMillis();
        if (now - lastCheck < 30000) {
            long remaining = (30000 - (now - lastCheck)) / 1000;
            Toast.makeText(getContext(), "Please wait " + remaining + " seconds before retry", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putLong("last_vip_check", now).apply();
        
        Toast.makeText(getContext(), "Checking VIP status...", Toast.LENGTH_SHORT).show();
        authManager.fetchRemoteConfig(prefs, () -> {
            onRemoteConfigUpdated();
            boolean newVip = prefs.getBoolean("is_vip_user", false);
            if (newVip) {
                Toast.makeText(getContext(), "🎉 Congratulations! You are now VIP!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), "Your Device ID is not in VIP list. Contact admin to upgrade.", Toast.LENGTH_LONG).show();
            }
            showVipUpgradeDialog();
        });
    }

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

    public interface DialogCallback {
        void onSelect(String item);
    }

    public void showModernDialog(String title, String[] items, DialogCallback callback) {
        Context ctx = getContext();
        FrameLayout dimBg = new FrameLayout(ctx);
        dimBg.setBackgroundColor(Color.argb(180, 0, 0, 0));
        dimBg.setOnClickListener(v -> { try { wm.removeView(dimBg); } catch (Exception ignored) {} });

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(VERTICAL);
        card.setPadding(UIFactory.dp(ctx, 20), UIFactory.dp(ctx, 20), UIFactory.dp(ctx, 20), UIFactory.dp(ctx, 20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UIFactory.C_BG);
        bg.setCornerRadius(UIFactory.dp(ctx, 16));
        bg.setStroke(UIFactory.dp(ctx, 1), UIFactory.C_ACCENT);
        card.setBackground(bg);
        card.setOnClickListener(v -> {});

        TextView tvT = new TextView(ctx);
        tvT.setText(title); tvT.setTextColor(UIFactory.C_ACCENT); tvT.setTextSize(14f);
        tvT.setTypeface(null, Typeface.BOLD); tvT.setGravity(Gravity.CENTER);
        tvT.setPadding(0, 0, 0, UIFactory.dp(ctx, 16));
        card.addView(tvT);

        ScrollView sv = new ScrollScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(VERTICAL);

        for (String item : items) {
            TextView btn = new TextView(ctx);
            btn.setText(item); btn.setTextColor(UIFactory.C_TEXT); btn.setTextSize(12.5f);
            btn.setPadding(UIFactory.dp(ctx, 12), UIFactory.dp(ctx, 12), UIFactory.dp(ctx, 12), UIFactory.dp(ctx, 12));
            btn.setGravity(Gravity.CENTER);
            btn.setTypeface(null, Typeface.BOLD);

            GradientDrawable bbg = new GradientDrawable();
            bbg.setColor(UIFactory.C_BG);
            bbg.setCornerRadius(UIFactory.dp(ctx, 8));
            btn.setBackground(bbg);

            LayoutParams blp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            blp.setMargins(0, 0, 0, UIFactory.dp(ctx, 8));
            btn.setLayoutParams(blp);

            btn.setOnClickListener(v -> {
                try { wm.removeView(dimBg); } catch (Exception ignored) {}
                callback.onSelect(item);
            });
            list.addView(btn);
        }
        sv.addView(list);

        LayoutParams svLp = new LayoutParams(UIFactory.dp(ctx, 240), UIFactory.dp(ctx, 300));
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

    private static class ScrollScrollView extends ScrollView {
        public ScrollScrollView(Context context) { super(context); }
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(UIFactory.dp(getContext(), 300), MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
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
                    byte[] packetBuf = new byte[80];
                    long lastUIRefreshTime = 0;

                    while (isRoomSocketRunning) {
                        dis.readFully(countBuf);
                        int count = java.nio.ByteBuffer.wrap(countBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
                        
                        if (count < 0 || count > 20) {
                            break; 
                        }

                        final java.util.List<RoomPlayerData> players = new java.util.ArrayList<>();
                        boolean isEnabled = prefs.getBoolean("room_info_enable", false);

                        for (int i = 0; i < count; i++) {
                            dis.readFully(packetBuf);
                            if (!isEnabled) continue;

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

                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastUIRefreshTime >= 1000) {
                            lastUIRefreshTime = currentTime;

                            post(() -> {
                                if (roomTableContainer == null) return;
                                roomTableContainer.removeAllViews();
                                
                                if (!isEnabled) {
                                    android.widget.TextView tv = new android.widget.TextView(getContext());
                                    tv.setText("Room Info is Disabled. Turn on to view.");
                                    tv.setTextColor(UIFactory.C_SUBTEXT);
                                    tv.setTextSize(12f);
                                    roomTableContainer.addView(tv);
                                    return;
                                }

                                if (players.isEmpty()) {
                                    android.widget.TextView tv = new android.widget.TextView(getContext());
                                    tv.setText("Waiting data...");
                                    tv.setTextColor(UIFactory.C_ACCENT);
                                    tv.setTextSize(12f);
                                    roomTableContainer.addView(tv);
                                    return;
                                }

                                java.util.List<RoomPlayerData> allyTeam = new java.util.ArrayList<>();
                                java.util.List<RoomPlayerData> enemyTeam = new java.util.ArrayList<>();

                                int myCamp = players.get(0).camp; 
                                for (RoomPlayerData p : players) {
                                    if (p.camp == myCamp) allyTeam.add(p);
                                    else enemyTeam.add(p);
                                }

                                if (!allyTeam.isEmpty()) {
                                    roomTableContainer.addView(createTeamLabel(getContext(), "ALLY TEAM", android.graphics.Color.parseColor("#42A5F5")));
                                    for (RoomPlayerData p : allyTeam) roomTableContainer.addView(createPlayerCard(p));
                                }
                                if (!allyTeam.isEmpty() && !enemyTeam.isEmpty()) {
                                    roomTableContainer.addView(createVSDivider(getContext()));
                                }
                                if (!enemyTeam.isEmpty()) {
                                    roomTableContainer.addView(createTeamLabel(getContext(), "ENEMY TEAM", android.graphics.Color.parseColor("#EF5350")));
                                    for (RoomPlayerData p : enemyTeam) roomTableContainer.addView(createPlayerCard(p));
                                }
                            });
                        }
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
}
