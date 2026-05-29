package com.overlay;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class LoginView extends LinearLayout {
    private static final int C_BG = Color.argb(220, 10, 10, 15);
    private static final int C_ACCENT = Color.parseColor("#D4AF37");
    private static final int C_TEXT = Color.parseColor("#FFFFFF");
    private static final int C_SUBTEXT = Color.parseColor("#A0A0A0");
    private static final int C_CARD = Color.argb(170, 25, 25, 35);

    private final WindowManager wm;
    private final WindowManager.LayoutParams lp;
    private final KeyAuthManager authManager;
    private final Runnable onLoginSuccess;

    private LinearLayout loginCard;
    private TextView tvPill;
    private EditText etKey;
    private TextView btnLogin;
    private TextView tvInfo;
    private ProgressBar loader;

    private float tx, ty;
    private int ix, iy;
    private boolean dragging;
    private boolean isAttached = false;

    public LoginView(Context context, WindowManager wm, WindowManager.LayoutParams lp, Runnable onLoginSuccess) {
        super(context);
        this.wm = wm;
        this.lp = lp;
        this.onLoginSuccess = onLoginSuccess;
        this.authManager = new KeyAuthManager(context);

        setOrientation(VERTICAL);
        buildPill(context);
        buildUI(context);

        tvPill.setVisibility(GONE);
        loginCard.setVisibility(VISIBLE);

        // Tampilkan info tersimpan jika ada
        String savedInfo = authManager.getSavedInfo();
        if (!savedInfo.isEmpty()) {
            tvInfo.setText(savedInfo);
            tvInfo.setVisibility(VISIBLE);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        isAttached = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isAttached = false;
    }

    private void buildPill(Context ctx) {
        tvPill = new TextView(ctx);
        tvPill.setText("🔑 MONDEV");
        tvPill.setTextColor(C_ACCENT);
        tvPill.setTextSize(14f);
        tvPill.setTypeface(null, Typeface.BOLD);
        tvPill.setGravity(Gravity.CENTER);
        tvPill.setPadding(dp(15), dp(10), dp(15), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_BG);
        bg.setCornerRadius(dp(50));
        bg.setStroke(dp(1), C_ACCENT);
        tvPill.setBackground(bg);

        tvPill.setOnTouchListener(dragL);
        addView(tvPill);
    }

    private void buildUI(Context ctx) {
        loginCard = new LinearLayout(ctx);
        loginCard.setOrientation(VERTICAL);
        loginCard.setPadding(dp(25), dp(25), dp(25), dp(25));
        loginCard.setGravity(Gravity.CENTER_HORIZONTAL);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(C_BG);
        gd.setCornerRadius(dp(24));
        gd.setStroke(dp(1), C_ACCENT);
        loginCard.setBackground(gd);

        LayoutParams cardLp = new LayoutParams(dp(300), LayoutParams.WRAP_CONTENT);
        loginCard.setLayoutParams(cardLp);

        // Header
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, dp(10), dp(20));

        LinearLayout leftPanel = new LinearLayout(ctx);
        leftPanel.setOrientation(VERTICAL);
        leftPanel.setGravity(Gravity.START);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        leftPanel.setLayoutParams(leftLp);

        TextView title = new TextView(ctx);
        title.setText("MONDEV LOGIN");
        title.setTextColor(C_TEXT);
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        leftPanel.addView(title);

        TextView sub = new TextView(ctx);
        sub.setText("Device ID: " + authManager.getDeviceId());
        sub.setTextColor(C_ACCENT);
        sub.setTextSize(9f);
        // ✅ Tap Device ID untuk langsung copy ke clipboard
        sub.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("DeviceID", authManager.getDeviceId()));
                Toast.makeText(ctx, "Device ID disalin!", Toast.LENGTH_SHORT).show();
            }
        });
        leftPanel.addView(sub);
        header.addView(leftPanel);

        TextView minBtn = new TextView(ctx);
        minBtn.setText("─");
        minBtn.setTextColor(C_SUBTEXT);
        minBtn.setPadding(dp(10), dp(8), dp(10), dp(8));
        minBtn.setOnClickListener(v -> showCollapsed());
        header.addView(minBtn);
        loginCard.addView(header);

        // Info / Update Message Box
        tvInfo = new TextView(ctx);
        tvInfo.setTextColor(C_TEXT);
        tvInfo.setTextSize(11f);
        tvInfo.setGravity(Gravity.CENTER);
        tvInfo.setPadding(dp(10), dp(10), dp(10), dp(10));
        tvInfo.setVisibility(GONE);
        GradientDrawable infoBg = new GradientDrawable();
        infoBg.setColor(Color.argb(50, 212, 175, 55));
        infoBg.setCornerRadius(dp(8));
        tvInfo.setBackground(infoBg);

        LayoutParams infoLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        infoLp.setMargins(0, 0, 0, dp(15));
        tvInfo.setLayoutParams(infoLp);
        loginCard.addView(tvInfo);

        // Input Area
        LinearLayout inputArea = new LinearLayout(ctx);
        inputArea.setOrientation(HORIZONTAL);
        inputArea.setGravity(Gravity.CENTER_VERTICAL);
        inputArea.setPadding(0, 0, 0, dp(15));

        etKey = new EditText(ctx);
        etKey.setHint("Enter License Key");
        etKey.setHintTextColor(Color.GRAY);
        etKey.setTextColor(Color.WHITE);
        etKey.setTextSize(14f);
        etKey.setSingleLine(true);
        etKey.setPadding(dp(12), dp(10), dp(12), dp(10));
        etKey.setInputType(InputType.TYPE_CLASS_TEXT);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(C_CARD);
        inputBg.setCornerRadius(dp(8));
        etKey.setBackground(inputBg);

        LayoutParams etLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        etKey.setLayoutParams(etLp);
        inputArea.addView(etKey);

        TextView btnPaste = new TextView(ctx);
        btnPaste.setText("PASTE");
        btnPaste.setTextColor(Color.BLACK);
        btnPaste.setTextSize(11f);
        btnPaste.setTypeface(null, Typeface.BOLD);
        btnPaste.setGravity(Gravity.CENTER);
        btnPaste.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable pBg = new GradientDrawable();
        pBg.setColor(C_ACCENT);
        pBg.setCornerRadius(dp(5));
        btnPaste.setBackground(pBg);

        LayoutParams pLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        pLp.setMargins(dp(8), 0, 0, 0);
        btnPaste.setLayoutParams(pLp);
        btnPaste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                etKey.setText(cm.getPrimaryClip().getItemAt(0).getText().toString().trim());
            }
        });
        inputArea.addView(btnPaste);
        loginCard.addView(inputArea);

        loader = new ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall);
        loader.setVisibility(GONE);
        loginCard.addView(loader);

        btnLogin = new TextView(ctx);
        btnLogin.setText("LOGIN");
        btnLogin.setTextColor(Color.BLACK);
        btnLogin.setTypeface(null, Typeface.BOLD);
        btnLogin.setGravity(Gravity.CENTER);
        btnLogin.setPadding(0, dp(12), 0, dp(12));
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(C_ACCENT);
        btnBg.setCornerRadius(dp(14));
        btnLogin.setBackground(btnBg);
        btnLogin.setOnClickListener(v -> attemptLogin());

        LayoutParams btnLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        btnLogin.setLayoutParams(btnLp);
        loginCard.addView(btnLogin);

        TextView tvTelegram = new TextView(ctx);
        tvTelegram.setText("Get Key via Telegram");
        tvTelegram.setTextColor(C_ACCENT);
        tvTelegram.setTextSize(11f);
        tvTelegram.setPadding(0, dp(15), 0, 0);
        tvTelegram.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/modfreew"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        });
        loginCard.addView(tvTelegram);

        loginCard.setOnTouchListener(dragL);
        addView(loginCard);
    }

    private void attemptLogin() {
        String key = etKey.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(getContext(), "Key required!", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        authManager.validateKey(key, new KeyAuthManager.AuthCallback() {
            @Override
            public void onSuccess() {
                // Callback sudah di-post ke main thread oleh KeyAuthManager
                setLoading(false);
                Toast.makeText(getContext(), "✅ Login successful!", Toast.LENGTH_SHORT).show();
                onLoginSuccess.run();
            }

            @Override
            public void onFailure(String reason) {
                // Callback sudah di-post ke main thread oleh KeyAuthManager
                setLoading(false);
                Toast.makeText(getContext(), reason, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onUpdateInfo(String info) {
                // ✅ FIX: Tidak perlu mainHandler.post() lagi karena KeyAuthManager
                // sudah post ke main thread — double-post dihapus
                tvInfo.setText(info);
                tvInfo.setVisibility(VISIBLE);
            }
        });
    }

    private void setLoading(boolean loading) {
        btnLogin.setVisibility(loading ? GONE : VISIBLE);
        loader.setVisibility(loading ? VISIBLE : GONE);
    }

    private void showCollapsed() {
        loginCard.setVisibility(GONE);
        tvPill.setVisibility(VISIBLE);
        updateLayout();
    }

    private void showExpanded() {
        tvPill.setVisibility(GONE);
        loginCard.setVisibility(VISIBLE);
        updateLayout();
    }

    private void updateLayout() {
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        try { wm.updateViewLayout(this, lp); } catch (Exception ignored) {}
    }

    private int dp(int px) {
        return (int) (px * getContext().getResources().getDisplayMetrics().density);
    }

    private final OnTouchListener dragL = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    ix = lp.x; iy = lp.y;
                    tx = event.getRawX(); ty = event.getRawY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - tx);
                    int dy = (int) (event.getRawY() - ty);
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        lp.x = ix + dx;
                        lp.y = iy + dy;
                        if (isAttached) {
                            try { wm.updateViewLayout(LoginView.this, lp); } catch (Exception ignored) {}
                        }
                        dragging = true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging && v == tvPill) showExpanded();
                    return true;
            }
            return false;
        }
    };
}
