package com.google.android.ext;

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
    private static final int C_ACCENT = Color.parseColor("#D4AF37"); // Muted Gold
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
    private ProgressBar loader;

    private float tx, ty;
    private int ix, iy;
    private boolean dragging;
    private boolean isAttached = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        
        // Background with Image for Login
        try {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(ctx.getAssets().open("background.jpg"));
            if (bmp != null) {
                android.graphics.Bitmap overlay = android.graphics.Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), bmp.getConfig());
                android.graphics.Canvas canvas = new android.graphics.Canvas(overlay);
                canvas.drawBitmap(bmp, 0, 0, null);
                canvas.drawColor(Color.argb(180, 0, 0, 0));
                android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), overlay);
                loginCard.setBackground(bd);
            } else {
                loginCard.setBackgroundColor(C_BG);
            }
        } catch (Exception e) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(C_BG);
            gd.setCornerRadius(dp(24));
            gd.setStroke(dp(1), C_ACCENT);
            loginCard.setBackground(gd);
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            loginCard.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(24));
                }
            });
            loginCard.setClipToOutline(true);
        }

        LayoutParams cardLp = new LayoutParams(dp(300), LayoutParams.WRAP_CONTENT);
        loginCard.setLayoutParams(cardLp);

        // Header
        // Ubah header menjadi horizontal
LinearLayout header = new LinearLayout(ctx);
header.setOrientation(HORIZONTAL);           // ← HORIZONTAL
header.setGravity(Gravity.CENTER_VERTICAL);  // vertikal center
header.setPadding(0, 0, dp(10), dp(20));     // padding kanan untuk tombol

// Panel kiri (berisi title dan sub secara vertikal)
LinearLayout leftPanel = new LinearLayout(ctx);
leftPanel.setOrientation(VERTICAL);
leftPanel.setGravity(Gravity.START);
LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
leftPanel.setLayoutParams(leftLp);

TextView title = new TextView(ctx);
title.setText("MONDEV");
title.setTextColor(C_TEXT);
title.setTextSize(22f);
title.setLetterSpacing(0.1f);
title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
leftPanel.addView(title);

TextView sub = new TextView(ctx);
sub.setText("BETA ACCESS");
sub.setTextColor(C_ACCENT);
sub.setTextSize(10f);
sub.setLetterSpacing(0.2f);
leftPanel.addView(sub);

header.addView(leftPanel);

// Tombol minimasi di pojok kanan
TextView minBtn = new TextView(ctx);
minBtn.setText("─");
minBtn.setTextColor(C_SUBTEXT);
minBtn.setPadding(dp(10), dp(8), dp(10), dp(8));
minBtn.setOnClickListener(v -> showCollapsed());
header.addView(minBtn);
        
        loginCard.addView(header);

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
        // Don't set focusable false globally; focus is requested temporarily during paste
        etKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        // Not focused on init — focus will be requested when needed
        
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(C_CARD);
        inputBg.setCornerRadius(dp(8));
        inputBg.setStroke(dp(1), Color.parseColor("#1E1E28"));
        etKey.setBackground(inputBg);
        
        LayoutParams etLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        etKey.setLayoutParams(etLp);
        inputArea.addView(etKey);

        // Clear Button (✕)
        TextView btnClear = new TextView(ctx);
        btnClear.setText("✕");
        btnClear.setTextColor(Color.WHITE);
        btnClear.setTextSize(14f);
        btnClear.setGravity(Gravity.CENTER);
        btnClear.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnClear.setOnClickListener(vv -> {
            etKey.setText("");
        });
        inputArea.addView(btnClear);

        // Paste Button
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

        // Special touch handler so dragging doesn't interfere with paste tap
        btnPaste.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return true;
        });

        // PASTE action: temporarily request focus to access clipboard
        btnPaste.setOnClickListener(v -> doPasteWithFocus(ctx));

        inputArea.addView(btnPaste);
        loginCard.addView(inputArea);

        // Loader
        loader = new ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall);
        loader.setVisibility(GONE);
        loginCard.addView(loader);

        // Login Button
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
        btnLogin.setTextColor(Color.BLACK);
        btnLogin.setLetterSpacing(0.1f);
        
        btnLogin.setOnClickListener(v -> attemptLogin());
        loginCard.addView(btnLogin);

        // Footer
        LinearLayout footer = new LinearLayout(ctx);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(15), 0, 0);

        TextView tvGet = new TextView(ctx);
        tvGet.setText("Request Access via Telegram");
        tvGet.setTextColor(C_ACCENT);
        tvGet.setTextSize(11f);
        tvGet.setPadding(dp(10), dp(5), dp(10), dp(5));
        tvGet.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/modfreew"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        });
        footer.addView(tvGet);
        
        loginCard.addView(footer);
        
        loginCard.setOnTouchListener(dragL);
        addView(loginCard);
    }

    /**
     * Temporarily take focus, read clipboard, paste text, then restore focus.
     */
    private void doPasteWithFocus(Context ctx) {
        // 1. Save original flags and remove FLAG_NOT_FOCUSABLE
        final int originalFlags = lp.flags;
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        // Optional: FLAG_ALT_FOCUSABLE_IM can suppress keyboard — not needed here
        // lp.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        try {
            wm.updateViewLayout(this, lp);
        } catch (Exception ignored) {}

        // 2. Focus on EditText (no keyboard since FLAG_ALT_FOCUSABLE_IM is not set)
        etKey.setFocusableInTouchMode(true);
        etKey.requestFocus();

        // 3. Short delay to let the system process focus
        mainHandler.postDelayed(() -> {
            try {
                ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip()) {
                    ClipData clip = cm.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence text = clip.getItemAt(0).getText();
                        if (text != null) {
                            etKey.setText(text.toString().trim());
                            Toast.makeText(ctx, "Pasted!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ctx, "Clipboard contains empty text", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(ctx, "Clipboard is empty", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // No clipboard access (rare)
                    Toast.makeText(ctx, "Cannot read clipboard. Try copying the key again.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(ctx, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            } finally {
                // 4. Restore flags and clear focus
                etKey.clearFocus();
                etKey.setFocusableInTouchMode(false);
                lp.flags = originalFlags;
                try {
                    wm.updateViewLayout(LoginView.this, lp);
                } catch (Exception ignored) {}
            }
        }, 150); // 150ms is enough for focus to register
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
                setLoading(false);
                Toast.makeText(getContext(), "Success!", Toast.LENGTH_SHORT).show();
                onLoginSuccess.run();
            }

            @Override
            public void onFailure(String reason) {
                setLoading(false);
                Toast.makeText(getContext(), reason, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        btnLogin.setVisibility(loading ? GONE : VISIBLE);
        loader.setVisibility(loading ? VISIBLE : GONE);
    }

    private void showCollapsed() {
        if (!isAttached) return;
        loginCard.setVisibility(GONE);
        tvPill.setVisibility(VISIBLE);
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        try { wm.updateViewLayout(this, lp); } catch (Exception ignored) {}
    }

    private void showExpanded() {
        if (!isAttached) return;
        tvPill.setVisibility(GONE);
        loginCard.setVisibility(VISIBLE);
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        try { wm.updateViewLayout(this, lp); } catch (Exception ignored) {}
    }

    private int dp(int v) {
        return (int) (v * getContext().getResources().getDisplayMetrics().density);
    }

    private final OnTouchListener dragL = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (!isAttached) return false;
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    tx = e.getRawX(); ty = e.getRawY();
                    ix = lp.x; iy = lp.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int)(e.getRawX() - tx);
                    int dy = (int)(e.getRawY() - ty);
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        dragging = true;
                        lp.x = ix + dx;
                        lp.y = iy + dy;
                        try { wm.updateViewLayout(LoginView.this, lp); } catch (Exception ignored) {}
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
}