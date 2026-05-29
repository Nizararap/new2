package com.overlay;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class AdBlocker {

    private static final String[] AD_HOSTS = {
        "doubleclick.net", "googlesyndication.com", "adnxs.com",
        "pubmatic.com", "rubiconproject.com", "openx.net",
        "taboola.com", "outbrain.com", "applovin.com",
        "unityads.unity3d.com", "vungle.com", "chartboost.com",
        "ironsrc.com", "admob.googleapis.com", "googleadservices.com",
        "ads.facebook.com", "an.facebook.com", "mopub.com",
        "advertising.com", "adservice.google.com"
    };

    private static boolean enabled = false;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable scanRunnable;

    public static void setEnabled(boolean enable) {
        enabled = enable;
        if (enable) startScanning();
        else stopScanning();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void startScanning() {
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (!enabled) return;
                scanAndBlock();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(scanRunnable);
    }

    private static void stopScanning() {
        if (scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
            scanRunnable = null;
        }
    }

    private static void scanAndBlock() {
        try {
            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
            Method getInstance = wmg.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object instance = getInstance.invoke(null);

            Field mViewsField = wmg.getDeclaredField("mViews");
            mViewsField.setAccessible(true);
            ArrayList<?> views = (ArrayList<?>) mViewsField.get(instance);

            if (views == null) return;
            for (Object v : new ArrayList<>(views)) {
                if (v instanceof View) traverseAndBlock((View) v);
            }
        } catch (Exception ignored) {}
    }

    private static void traverseAndBlock(View view) {
        if (view instanceof WebView) {
            ((WebView) view).setWebViewClient(new BlockingWebViewClient());
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                traverseAndBlock(group.getChildAt(i));
            }
        }
    }

    static boolean isAdUrl(String url) {
        if (url == null || !enabled) return false;
        String lower = url.toLowerCase();
        for (String host : AD_HOSTS) {
            if (lower.contains(host)) return true;
        }
        return false;
    }

    static class BlockingWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (isAdUrl(url)) {
                return new WebResourceResponse("text/plain", "utf-8",
                        new ByteArrayInputStream(new byte[0]));
            }
            return super.shouldInterceptRequest(view, url);
        }
    }
}
