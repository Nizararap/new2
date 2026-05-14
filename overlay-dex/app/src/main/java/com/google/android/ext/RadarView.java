package com.google.android.ext;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.view.View;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RadarView extends View {
    private Paint borderPaint, enemyPaint, textPaint;
    private List<PlayerInfo> players = new ArrayList<>();
    private boolean isRunning = true;
    private SharedPreferences prefs;
    private SHMManager shmManager;

    // 1. DATABASE MENTAH: Menyimpan file PNG/WEBP dalam bentuk byte (Sangat ringan di RAM, memuat 129 hero)
    private Map<String, byte[]> rawImageBytes = new HashMap<>();

    // 2. CACHE AKTIF: Hanya menyimpan Bitmap hero yang SEDANG dipakai di match ini (Maksimal 10 Hero)
    private Map<String, Bitmap> activeHeroBitmaps = new HashMap<>();

    // 3. CACHE RADAR: Menyimpan Bitmap yang sudah di-resize & dibulatkan
    private Map<String, Bitmap> heroIconCache = new HashMap<>();
    private float currentIconSize = 0f;

    // 4. CACHE STRING: Agar tidak membuat object String terus menerus (Anti Patah-Patah)
    private Map<Integer, String> stringCache = new HashMap<>();

    private PlayerInfo[] playerPool = new PlayerInfo[20]; 

    private static class PlayerInfo {
        int entityId;     
        float x, y, z;
        int campType;
        String heroName;
    }
    
    private Bitmap getCircularBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        
        float radius = bitmap.getWidth() / 2f;
        canvas.drawCircle(radius, radius, radius, paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return output;
    }

    public RadarView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("mod_settings", Context.MODE_PRIVATE);
        shmManager = new SHMManager();
        
        for (int i = 0; i < 20; i++) {
            playerPool[i] = new PlayerInfo();
        }
        
        loadAndDecryptHeroes(); 
        initPaints();
        startSHMThread();
    }

    // =========================================================================
    // PERBAIKAN: HANYA SIMPAN BYTE[], JANGAN DI-DECODE JADI BITMAP DULU!
    // =========================================================================
    private void loadAndDecryptHeroes() {
        try {
            InputStream is = getContext().getAssets().open("assets.dat");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[4096];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            byte[] encryptedBytes = buffer.toByteArray();
            is.close();

            byte key = 0x5B;
            for (int i = 0; i < encryptedBytes.length; i++) {
                encryptedBytes[i] ^= key;
            }

            ByteArrayInputStream bais = new ByteArrayInputStream(encryptedBytes);
            ZipInputStream zis = new ZipInputStream(bais);
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if (name.endsWith(".png") || name.endsWith(".webp")) {
                    
                    // Baca isi file menjadi byte array mentah
                    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                    byte[] tmp = new byte[2048];
                    int len;
                    while ((len = zis.read(tmp)) > 0) {
                        byteOut.write(tmp, 0, len);
                    }
                    
                    String searchKey = entry.getName()
                            .replace(".png", "")
                            .replace(".webp", "")
                            .toLowerCase().replaceAll("[^a-z0-9]", "");
                    
                    // Simpan byte mentah ke RAM (Bukan Bitmap!)
                    rawImageBytes.put(searchKey, byteOut.toByteArray()); 
                }
                zis.closeEntry();
            }
            zis.close();
            bais.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initPaints() {
        borderPaint = new Paint();
        borderPaint.setColor(Color.argb(120, 255, 215, 0));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        borderPaint.setAntiAlias(true);

        enemyPaint = new Paint();
        enemyPaint.setColor(Color.argb(200, 255, 45, 85));
        enemyPaint.setStyle(Paint.Style.FILL);
        enemyPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(20f);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
    }

    // =========================================================================
    // LAZY LOADING (Hanya merubah byte -> Bitmap jika hero muncul di match)
    // =========================================================================
    private Bitmap getOriginalBitmapLazy(String key) {
        // Jika sudah ada di cache RAM aktif (sedang dipakai di match ini), langsung return
        if (activeHeroBitmaps.containsKey(key)) {
            return activeHeroBitmaps.get(key);
        }
        
        // Jika belum ada, tapi byte[] nya kita punya, DECODE SEKARANG
        if (rawImageBytes.containsKey(key)) {
            byte[] imgData = rawImageBytes.get(key);
            Bitmap bmp = BitmapFactory.decodeByteArray(imgData, 0, imgData.length);
            if (bmp != null) {
                activeHeroBitmaps.put(key, bmp); // Simpan ke cache aktif agar tidak di-decode ulang
                return bmp;
            }
        }
        return null; // Gambar tidak ditemukan
    }

    private Bitmap getHeroIcon(String heroName, float targetSize) {
        String searchKey = heroName.toLowerCase().replaceAll("[^a-z0-9]", "");
        
        if (currentIconSize != targetSize) {
            heroIconCache.clear();
            currentIconSize = targetSize;
        }

        if (heroIconCache.containsKey(searchKey)) {
            return heroIconCache.get(searchKey);
        }

        // Ambil gambar original lewat metode Lazy Loading
        Bitmap originalBmp = getOriginalBitmapLazy(searchKey);
        if (originalBmp == null) {
            heroIconCache.put(searchKey, null);
            return null;
        }

        int size = (int) targetSize;
        if (size <= 0) size = 50;
        Bitmap scaledBmp = Bitmap.createScaledBitmap(originalBmp, size, size, true);
        Bitmap circularBmp = getCircularBitmap(scaledBmp); 
        
        heroIconCache.put(searchKey, circularBmp);
        return circularBmp;
    }

    private void startSHMThread() {
        new Thread(() -> {
            byte[] packetBuffer = new byte[52];
            while (isRunning) {
                if (shmManager.connect()) {
                    while (isRunning) {
                        int playerCount = shmManager.getPlayerCount();
                        if (playerCount > 10 || playerCount < 0) playerCount = 0;

                        if (playerCount == 0) {
                            if (!stringCache.isEmpty()) {
                                stringCache.clear();
                                heroIconCache.clear();
                                activeHeroBitmaps.clear();
                            }
                        }

                        List<PlayerInfo> newPlayers = new ArrayList<>(playerCount);
                        for (int i = 0; i < playerCount; i++) {
                            shmManager.readPlayerData(i, packetBuffer);
                            ByteBuffer bb = ByteBuffer.wrap(packetBuffer).order(ByteOrder.LITTLE_ENDIAN);

                            PlayerInfo p = playerPool[i];
                            p.entityId = bb.getInt();
                            p.x = bb.getFloat();
                            p.y = bb.getFloat();
                            p.z = bb.getFloat();
                            p.campType = bb.getInt();

                            if (stringCache.containsKey(p.entityId)) {
                                p.heroName = stringCache.get(p.entityId);
                            } else {
                                byte[] nameBytes = new byte[32];
                                bb.position(20); // HeroName starts at offset 20
                                bb.get(nameBytes);
                                String name = new String(nameBytes).trim();
                                stringCache.put(p.entityId, name);
                                p.heroName = name;
                            }
                            newPlayers.add(p);
                        }

                        synchronized (RadarView.this) {
                            players = newPlayers;
                        }
                        postInvalidate();
                        try { Thread.sleep(33); } catch (InterruptedException ignored) {}
                    }
                } else {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }

    private float[] worldToMinimap(int campType, float wx, float wz) {
        float angleCos = campType == 2 ? (float)Math.cos(Math.toRadians(314.60)) : (float)Math.cos(Math.toRadians(134.76));
        float angleSin = campType == 2 ? (float)Math.sin(Math.toRadians(314.60)) : (float)Math.sin(Math.toRadians(134.76));

        float negWz = -wz;
        float outX = (angleCos * wx - angleSin * negWz) / 74.11f;
        float outY = (angleSin * wx + angleCos * negWz) / 74.11f;
        return new float[]{outX, outY};
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        boolean isEnabled = prefs.getBoolean("radar_enable", false);
        if (!isEnabled) return;

        boolean drawBorder = prefs.getBoolean("radar_border", true);
        float size = prefs.getFloat("radar_size", 338.0f);
        float posX = prefs.getFloat("radar_pos_x", 71.0f);
        float posY = prefs.getFloat("radar_pos_y", 0.0f);
        float iconSize = prefs.getFloat("radar_icon_size", 37.0f);

        if (drawBorder) {
            canvas.drawRect(posX, posY, posX + size, posY + size, borderPaint);
        }

        float halfIcon = iconSize * 0.5f;
        List<PlayerInfo> currentPlayers;
        synchronized (this) {
            currentPlayers = new ArrayList<>(players);
        }
        
        for (PlayerInfo p : currentPlayers) {
            float[] mmOut = worldToMinimap(p.campType, p.x, p.z);
            float drawX = (mmOut[0] * size) + posX + (size * 0.5f);
            float drawY = (mmOut[1] * size) + posY + (size * 0.5f);

            drawX = Math.max(posX, Math.min(drawX, posX + size));
            drawY = Math.max(posY, Math.min(drawY, posY + size));

            Bitmap heroIcon = getHeroIcon(p.heroName, iconSize);

            if (heroIcon != null) {
                canvas.drawBitmap(heroIcon, drawX - halfIcon, drawY - halfIcon, null);
            } else {
                canvas.drawCircle(drawX, drawY, halfIcon, enemyPaint);
                String init = p.heroName != null && p.heroName.length() >= 3 ? p.heroName.substring(0, 3) : "???";
                canvas.drawText(init.toUpperCase(), drawX, drawY + 8f, textPaint);
            }
            
            borderPaint.setColor(Color.argb(255, 255, 30, 30));
            canvas.drawCircle(drawX, drawY, halfIcon, borderPaint);
            borderPaint.setColor(Color.argb(200, 255, 255, 255));
        }
    }

    public List<String> getActiveEnemyNames() {
        List<String> names = new ArrayList<>();
        synchronized (this) {
            for (PlayerInfo p : players) {
                if (p.heroName != null && !p.heroName.isEmpty()) {
                    if (!names.contains(p.heroName)) {
                        names.add(p.heroName);
                    }
                }
            }
        }
        return names;
    }

    // Fungsi dipanggil oleh OverlayView (Menu Room Info / Draft Pick)
    public Bitmap getRawIcon(String key) {
        return getOriginalBitmapLazy(key);
    }

    public void destroy() { isRunning = false; }
}