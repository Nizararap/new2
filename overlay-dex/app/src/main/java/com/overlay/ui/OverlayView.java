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
    // ==========================================
    // KUMPULAN FUNGSI ROOM INFO YANG DIHAPUS AI
    // ==========================================

    private View createTeamLabel(Context ctx, String text, int color) {
        android.widget.TextView tv = new android.widget.TextView(ctx);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(11f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setLetterSpacing(0.1f);
        
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(UIFactory.dp(ctx, 4), UIFactory.dp(ctx, 4), 0, UIFactory.dp(ctx, 8));
        tv.setLayoutParams(lp);
        
        return tv;
    }

    private View createVSDivider(Context ctx) {
        android.widget.LinearLayout container = new android.widget.LinearLayout(ctx);
        container.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER);
        
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIFactory.dp(ctx, 4), 0, UIFactory.dp(ctx, 12));
        container.setLayoutParams(lp);

        View lineLeft = new View(ctx);
        lineLeft.setBackgroundColor(android.graphics.Color.argb(80, 255, 255, 255));
        lineLeft.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, UIFactory.dp(ctx, 1), 1f));

        android.widget.TextView tvVs = new android.widget.TextView(ctx);
        tvVs.setText(" VS ");
        tvVs.setTextColor(UIFactory.C_SUBTEXT);
        tvVs.setTextSize(10f);
        tvVs.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
        tvVs.setPadding(UIFactory.dp(ctx, 8), 0, UIFactory.dp(ctx, 8), 0);

        View lineRight = new View(ctx);
        lineRight.setBackgroundColor(android.graphics.Color.argb(80, 255, 255, 255));
        lineRight.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, UIFactory.dp(ctx, 1), 1f));

        container.addView(lineLeft);
        container.addView(tvVs);
        container.addView(lineRight);

        return container;
    }

    private View createPlayerCard(RoomPlayerData p) {
        Context ctx = getContext();
        android.widget.LinearLayout card = new android.widget.LinearLayout(ctx);
        card.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(UIFactory.dp(ctx, 8), UIFactory.dp(ctx, 8), UIFactory.dp(ctx, 8), UIFactory.dp(ctx, 8));
        
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        if (p.camp == 1) {
            bg.setColor(android.graphics.Color.parseColor("#102030"));
            bg.setStroke(UIFactory.dp(ctx, 1), android.graphics.Color.parseColor("#1565C0")); 
        } else {
            bg.setColor(android.graphics.Color.parseColor("#301010"));
            bg.setStroke(UIFactory.dp(ctx, 1), android.graphics.Color.parseColor("#C62828")); 
        }
        bg.setCornerRadius(UIFactory.dp(ctx, 8));
        card.setBackground(bg);
        
        android.widget.LinearLayout.LayoutParams cardLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, UIFactory.dp(ctx, 6));
        card.setLayoutParams(cardLp);

        android.widget.LinearLayout colLeft = new android.widget.LinearLayout(ctx);
        colLeft.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        colLeft.setGravity(android.view.Gravity.CENTER_VERTICAL);
        colLeft.setLayoutParams(new android.widget.LinearLayout.LayoutParams(UIFactory.dp(ctx, 80), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        
        android.widget.FrameLayout heroContainer = new android.widget.FrameLayout(ctx);
        heroContainer.setLayoutParams(new android.widget.LinearLayout.LayoutParams(UIFactory.dp(ctx, 36), UIFactory.dp(ctx, 36)));

        android.widget.ImageView ivHero = new android.widget.ImageView(ctx);
        ivHero.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, 
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        ivHero.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        
        String heroFileName = getHeroNameStr(p.heroId); 
        android.graphics.Bitmap heroBmp = radar.getRawIcon(heroFileName);

        if (heroBmp == null && p.name != null) {
            heroBmp = radar.getRawIcon(p.name.toLowerCase().replaceAll("[^a-z0-9]", ""));
        }

        if (heroBmp != null) {
            ivHero.setImageBitmap(heroBmp);
            heroContainer.addView(ivHero);
        } else {
            ivHero.setBackgroundColor(android.graphics.Color.parseColor("#444444"));
            heroContainer.addView(ivHero);
            
            android.widget.TextView tvHeroId = new android.widget.TextView(ctx);
            tvHeroId.setText(String.valueOf(p.heroId));
            tvHeroId.setTextColor(android.graphics.Color.WHITE);
            tvHeroId.setTextSize(9f);
            tvHeroId.setGravity(android.view.Gravity.CENTER);
            tvHeroId.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT, 
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
            heroContainer.addView(tvHeroId);
        }
        
        android.widget.FrameLayout spellContainer = new android.widget.FrameLayout(ctx);
        android.widget.LinearLayout.LayoutParams spellLp = new android.widget.LinearLayout.LayoutParams(UIFactory.dp(ctx, 22), UIFactory.dp(ctx, 22));
        spellLp.setMargins(UIFactory.dp(ctx, 6), 0, 0, 0); 
        spellContainer.setLayoutParams(spellLp);

        android.widget.ImageView ivSpell = new android.widget.ImageView(ctx);
        ivSpell.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, 
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        ivSpell.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        
        String spellFileName = getSpellNameStr(p.spellId);
        android.graphics.Bitmap spellBmp = radar.getRawIcon(spellFileName);
        
        if (spellBmp != null) {
            ivSpell.setImageBitmap(spellBmp);
            spellContainer.addView(ivSpell);
        } else {
            ivSpell.setBackgroundColor(android.graphics.Color.parseColor("#444444"));
            spellContainer.addView(ivSpell);
            
            android.widget.TextView tvSpellId = new android.widget.TextView(ctx);
            tvSpellId.setText(String.valueOf(p.spellId)); 
            tvSpellId.setTextColor(android.graphics.Color.WHITE);
            tvSpellId.setTextSize(6f);
            tvSpellId.setGravity(android.view.Gravity.CENTER);
            tvSpellId.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT, 
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
            spellContainer.addView(tvSpellId);
        }

        colLeft.addView(heroContainer);
        colLeft.addView(spellContainer);

        android.widget.LinearLayout colMid = new android.widget.LinearLayout(ctx);
        colMid.setOrientation(android.widget.LinearLayout.VERTICAL);
        colMid.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        
        android.widget.TextView tvName = new android.widget.TextView(ctx);
        String leaderIcon = p.isLeader ? "👑 " : "";
        tvName.setText(leaderIcon + p.name);
        tvName.setTextColor(UIFactory.C_TEXT);
        tvName.setTextSize(12f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setSingleLine(true);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END); 

        android.widget.TextView tvUid = new android.widget.TextView(ctx);
        tvUid.setText("UID: " + p.uid + " | Lv." + p.accLv);
        tvUid.setTextColor(UIFactory.C_SUBTEXT);
        tvUid.setTextSize(9.5f);

        colMid.addView(tvName);
        colMid.addView(tvUid);

        android.widget.LinearLayout colRight = new android.widget.LinearLayout(ctx);
        colRight.setOrientation(android.widget.LinearLayout.VERTICAL);
        colRight.setGravity(android.view.Gravity.END);
        
        android.widget.LinearLayout.LayoutParams rightLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        rightLp.setMargins(UIFactory.dp(ctx, 10), 0, UIFactory.dp(ctx, 5), 0); 
        colRight.setLayoutParams(rightLp);
        
        android.widget.TextView tvRank = new android.widget.TextView(ctx);
        tvRank.setText(getRankName(p.rank, p.mythPt));
        tvRank.setTextColor(android.graphics.Color.parseColor("#FFD700"));
        tvRank.setTextSize(10f);
        tvRank.setTypeface(null, android.graphics.Typeface.BOLD);

        float wr = (p.matches > 0) ? ((float)p.wins / p.matches * 100f) : 0f;
        android.widget.TextView tvWr = new android.widget.TextView(ctx);
        tvWr.setText(String.format("%.1f%% (%d M)", wr, p.matches));
        tvWr.setTextSize(10.5f);
        
        if (wr >= 65.0f && p.matches >= 20) tvWr.setTextColor(android.graphics.Color.parseColor("#00E676")); 
        else if (wr >= 50.0f) tvWr.setTextColor(android.graphics.Color.parseColor("#FFA726")); 
        else if (wr > 0f && p.matches >= 10) tvWr.setTextColor(android.graphics.Color.parseColor("#EF5350")); 
        else tvWr.setTextColor(android.graphics.Color.LTGRAY); 

        colRight.addView(tvRank);
        colRight.addView(tvWr);

        card.addView(colLeft);
        card.addView(colMid);
        card.addView(colRight);

        return card;
    }

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
            default: return "unknown_spell"; 
        }
    }

    private String getHeroNameStr(int heroId) {
        switch(heroId) {
            case 1: return "miya"; case 2: return "balmond"; case 3: return "saber";
            case 4: return "alice"; case 5: return "nana"; case 6: return "tigreal";
            case 7: return "alucard"; case 8: return "karina"; case 9: return "akai";
            case 10: return "franco"; case 11: return "bane"; case 12: return "bruno";
            case 13: return "clint"; case 14: return "rafaela"; case 15: return "eudora";
            case 16: return "zilong"; case 17: return "fanny"; case 18: return "layla";
            case 19: return "minotaur"; case 20: return "lolita"; case 21: return "hayabusa";
            case 22: return "freya"; case 23: return "gord"; case 24: return "natalia";
            case 25: return "kagura"; case 26: return "chou"; case 27: return "sun";
            case 28: return "alpha"; case 29: return "ruby"; case 30: return "yisunshin";
            case 31: return "moskov"; case 32: return "johnson"; case 33: return "cyclops";
            case 34: return "estes"; case 35: return "hilda"; case 36: return "aurora";
            case 37: return "lapulapu"; case 38: return "vexana"; case 39: return "roger";
            case 40: return "karrie"; case 41: return "gatotkaca"; case 42: return "harley";
            case 43: return "irithel"; case 44: return "grock"; case 45: return "argus";
            case 46: return "odette"; case 47: return "lancelot"; case 48: return "diggie";
            case 49: return "hylos"; case 50: return "zhask"; case 51: return "helcurt";
            case 52: return "pharsa"; case 53: return "lesley"; case 54: return "jawhead";
            case 55: return "angela"; case 56: return "gusion"; case 57: return "valir";
            case 58: return "martis"; case 59: return "uranus"; case 60: return "hanabi";
            case 61: return "change"; case 62: return "kaja"; case 63: return "selena";
            case 64: return "aldous"; case 65: return "claude"; case 66: return "vale";
            case 67: return "leomord"; case 68: return "lunox"; case 69: return "hanzo";
            case 70: return "belerick"; case 71: return "kimmy"; case 72: return "thamuz";
            case 73: return "harith"; case 74: return "minsitthar"; case 75: return "kadita";
            case 76: return "faramis"; case 77: return "badang"; case 78: return "khufra";
            case 79: return "granger"; case 80: return "guinevere"; case 81: return "esmeralda";
            case 82: return "terizla"; case 83: return "xborg"; case 84: return "ling";
            case 85: return "dyrroth"; case 86: return "lylia"; case 87: return "baxia";
            case 88: return "masha"; case 89: return "wanwan"; case 90: return "silvanna";
            case 91: return "cecilion"; case 92: return "carmilla"; case 93: return "atlas";
            case 94: return "popolandkupa"; case 95: return "yuzhong"; case 96: return "luoyi";
            case 97: return "benedetta"; case 98: return "khaleed"; case 99: return "barats";
            case 100: return "brody"; case 101: return "yve"; case 102: return "mathilda";
            case 103: return "paquito"; case 104: return "gloo"; case 105: return "beatrix";
            case 106: return "phoveus"; case 107: return "natan"; case 108: return "aulus";
            case 109: return "aamon"; case 110: return "valentina"; case 111: return "edith";
            case 112: return "floryn"; case 113: return "yin"; case 114: return "melissa";
            case 115: return "xavier"; case 116: return "julian"; case 117: return "fredrinn";
            case 118: return "joy"; case 119: return "novaria"; case 120: return "arlott";
            case 121: return "ixia"; case 122: return "nolan"; case 123: return "cici";
            case 124: return "chip"; case 125: return "zhuxin"; case 126: return "suyou";
            case 127: return "lukas"; case 128: return "kalea"; case 129: return "zetian";
            case 130: return "obsidia"; case 131: return "sora"; case 132: return "marcel";
            default: return "unknown_hero";
        }
    }
}
