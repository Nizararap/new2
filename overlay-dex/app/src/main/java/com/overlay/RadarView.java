package com.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.view.View;
import android.view.Choreographer;
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

import com.overlay.models.PlayerInfo;
import com.overlay.models.MonsterInfo;

public class RadarView extends View {

    // ==========================================
    // PRE-ALLOCATED PAINTS & RECT
    // ==========================================
    private Paint borderPaint, textPaint, shadowPaint, espOutlinePaint, espLinePaint;
    private Paint cdBgPaint, cdTextPaint, cdTextStrokePaint, borderBoxPaint;
    private Paint hpBgArcPaint, hpArcPaint;
    private Paint monBgBar, monFillBar, monStrokeBar, monTextBg;
    private Paint monHpTextPaint, monHpTextStrokePaint;
    private Paint fallbackPaint, borderRedPaint;
    
    // TAMBAHAN PAINT KHUSUS ALERT BOSS HP
    private Paint alertBgPaint, alertTextPaint, alertTextStrokePaint;

    private RectF tempRect = new RectF();
    private RectF arcRect = new RectF();

    private List<PlayerInfo> players = new ArrayList<>();
    private List<MonsterInfo> monsters = new ArrayList<>();
    
    // FIX: Cache list untuk merender agar tidak perlu 'new' di onDraw
    private List<PlayerInfo> renderPlayers = new ArrayList<>();
    private List<MonsterInfo> renderMonsters = new ArrayList<>();
    
    private boolean isRunning = true;
    private SharedPreferences prefs;

    private Map<String, byte[]> rawImageBytes = new HashMap<>();
    private Map<String, Bitmap> activeHeroBitmaps = new HashMap<>();
    private Map<String, Bitmap> heroIconCache = new HashMap<>();
    private float currentIconSize = 0f;
    private Map<Integer, String> stringCache = new HashMap<>();
    
    private int lastLocalPlayerHp = -1;
    private android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private Choreographer.FrameCallback frameCallback;
    
    // Cache HP monster sebelumnya untuk deteksi perubahan
    private Map<Integer, Integer> lastMonsterHp = new HashMap<>();
    private Map<Integer, Long> monsterAlertExpiry = new HashMap<>();
    
    // 🟢 TAMBAHAN TRACKER ULTIMATE MUSUH
    private Map<Integer, Integer> lastUltCdTracker = new HashMap<>();

    private static final long ALERT_DURATION_MS = 3000;
    
    // 🟢 OPTIMASI: Cache String angka 0-150 untuk mencegah GC Churn di onDraw
    private static final String[] CD_STRINGS = new String[151];
    static {
        for (int i = 0; i <= 150; i++) {
            CD_STRINGS[i] = String.valueOf(i);
        }
    }

    public RadarView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("mod_settings", Context.MODE_PRIVATE);
        initPaints();

        new Thread(() -> {
            loadAndDecryptHeroes();
        }).start();

        startSocketThread();

        frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (isRunning) {
                    invalidate(); 
                    Choreographer.getInstance().postFrameCallback(this);
                }
            }
        };
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void initPaints() {
        borderPaint = new Paint();
        borderPaint.setColor(Color.argb(120, 255, 215, 0));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        borderPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(16f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);

        shadowPaint = new Paint();
        shadowPaint.setColor(Color.argb(200, 0, 0, 0));
        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStrokeWidth(5f);
        shadowPaint.setAntiAlias(true);
                
        espOutlinePaint = new Paint();
        espOutlinePaint.setColor(Color.argb(255, 255, 50, 50));
        espOutlinePaint.setStyle(Paint.Style.STROKE);
        espOutlinePaint.setStrokeWidth(2.5f);
        espOutlinePaint.setAntiAlias(true);
        
        espLinePaint = new Paint();
        espLinePaint.setColor(Color.argb(150, 255, 255, 255)); // Putih transparan (Alpha 150)
        espLinePaint.setStyle(Paint.Style.STROKE);
        espLinePaint.setStrokeWidth(2.5f);
        espLinePaint.setAntiAlias(true);

        cdBgPaint = new Paint();
        cdBgPaint.setStyle(Paint.Style.FILL);
        cdBgPaint.setAntiAlias(true);

        borderBoxPaint = new Paint();
        borderBoxPaint.setStyle(Paint.Style.STROKE);
        borderBoxPaint.setStrokeWidth(1.5f);
        borderBoxPaint.setAntiAlias(true);

        cdTextPaint = new Paint();
        cdTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        cdTextPaint.setTextAlign(Paint.Align.CENTER);
        cdTextPaint.setAntiAlias(true);
        
        cdTextStrokePaint = new Paint(cdTextPaint);
        cdTextStrokePaint.setColor(Color.BLACK);
        cdTextStrokePaint.setStyle(Paint.Style.STROKE);
        cdTextStrokePaint.setStrokeWidth(3.0f);

        hpBgArcPaint = new Paint();
        hpBgArcPaint.setColor(Color.argb(150, 20, 20, 20));
        hpBgArcPaint.setStyle(Paint.Style.STROKE);
        hpBgArcPaint.setStrokeWidth(5.5f);
        hpBgArcPaint.setStrokeCap(Paint.Cap.ROUND);
        hpBgArcPaint.setAntiAlias(true);

        hpArcPaint = new Paint();
        hpArcPaint.setStyle(Paint.Style.STROKE);
        hpArcPaint.setStrokeWidth(5.5f);
        hpArcPaint.setStrokeCap(Paint.Cap.ROUND);
        hpArcPaint.setAntiAlias(true);

        monBgBar = new Paint();
        monBgBar.setColor(Color.argb(220, 15, 15, 20));
        monBgBar.setAntiAlias(true);

        monFillBar = new Paint();
        monFillBar.setAntiAlias(true);

        monStrokeBar = new Paint();
        monStrokeBar.setColor(Color.argb(120, 200, 200, 200));
        monStrokeBar.setStyle(Paint.Style.STROKE);
        monStrokeBar.setStrokeWidth(1.5f);
        monStrokeBar.setAntiAlias(true);

        monTextBg = new Paint();
        monTextBg.setColor(Color.argb(160, 0, 0, 0));
        monTextBg.setAntiAlias(true);

        monHpTextPaint = new Paint();
        monHpTextPaint.setColor(Color.WHITE);
        monHpTextPaint.setTextSize(20f); 
        monHpTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        monHpTextPaint.setTextAlign(Paint.Align.CENTER);
        monHpTextPaint.setShadowLayer(5f, 2f, 2f, Color.BLACK);
        monHpTextPaint.setAntiAlias(true);

        monHpTextStrokePaint = new Paint(monHpTextPaint);
        monHpTextStrokePaint.setColor(Color.BLACK);
        monHpTextStrokePaint.setStyle(Paint.Style.STROKE);
        monHpTextStrokePaint.setStrokeWidth(5.5f);

        alertBgPaint = new Paint();
        alertBgPaint.setColor(Color.argb(220, 20, 20, 25)); 
        alertBgPaint.setAntiAlias(true);

        alertTextPaint = new Paint();
        alertTextPaint.setColor(Color.WHITE);
        alertTextPaint.setTextSize(18f); 
        alertTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        alertTextPaint.setTextAlign(Paint.Align.CENTER);
        alertTextPaint.setShadowLayer(4f, 1f, 1f, Color.BLACK);
        alertTextPaint.setAntiAlias(true);

        alertTextStrokePaint = new Paint(alertTextPaint);
        alertTextStrokePaint.setColor(Color.BLACK);
        alertTextStrokePaint.setStyle(Paint.Style.STROKE);
        alertTextStrokePaint.setStrokeWidth(4.5f);

        fallbackPaint = new Paint();
        fallbackPaint.setColor(Color.argb(200, 255, 45, 85));
        fallbackPaint.setStyle(Paint.Style.FILL);
        fallbackPaint.setAntiAlias(true);

        borderRedPaint = new Paint();
        borderRedPaint.setColor(Color.argb(255, 255, 30, 30));
        borderRedPaint.setStyle(Paint.Style.STROKE);
        borderRedPaint.setStrokeWidth(2f);
        borderRedPaint.setAntiAlias(true);
    }

    private void loadAndDecryptHeroes() {
        try {
            InputStream is = getContext().getAssets().open("assets.dat");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[4096];
            while ((nRead = is.read(data, 0, data.length)) != -1) buffer.write(data, 0, nRead);
            byte[] encryptedBytes = buffer.toByteArray();
            is.close();

            byte key = 0x5B;
            for (int i = 0; i < encryptedBytes.length; i++) encryptedBytes[i] ^= key;

            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(encryptedBytes));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if (name.endsWith(".png") || name.endsWith(".webp")) {
                    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                    byte[] tmp = new byte[2048];
                    int len;
                    while ((len = zis.read(tmp)) > 0) byteOut.write(tmp, 0, len);
                    String searchKey = entry.getName().replace(".png", "").replace(".webp", "").toLowerCase().replaceAll("[^a-z0-9]", "");
                    rawImageBytes.put(searchKey, byteOut.toByteArray()); 
                }
                zis.closeEntry();
            }
            zis.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private Bitmap getOriginalBitmapLazy(String key) {
        if (activeHeroBitmaps.containsKey(key)) return activeHeroBitmaps.get(key);
        if (rawImageBytes.containsKey(key)) {
            byte[] imgData = rawImageBytes.get(key);
            Bitmap bmp = BitmapFactory.decodeByteArray(imgData, 0, imgData.length);
            if (bmp != null) { activeHeroBitmaps.put(key, bmp); return bmp; }
        }
        return null;
    }

    private Bitmap getHeroIcon(String heroName, float targetSize) {
        if (heroName == null) return null;
        int size = (int) targetSize;
        if (size <= 0) size = 50;
        
        String searchKey = heroName.toLowerCase().replaceAll("[^a-z0-9]", "");
        String cacheKey = searchKey + "_" + size; 
        
        if (heroIconCache.containsKey(cacheKey)) return heroIconCache.get(cacheKey);

        Bitmap originalBmp = getOriginalBitmapLazy(searchKey);
        if (originalBmp == null) return null;

        Bitmap scaledBmp = Bitmap.createScaledBitmap(originalBmp, size, size, true);
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float radius = size / 2f;
        
        canvas.drawCircle(radius, radius, radius, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(scaledBmp, 0, 0, paint);
        
        if (scaledBmp != originalBmp) {
            scaledBmp.recycle();
        }
        
        heroIconCache.put(cacheKey, output);
        return output;
    }

    private void startSocketThread() {
        new Thread(() -> {
            // ========================================================
            // 🟢 OBJECT POOLING: PRE-ALLOCATE MEMORY AGAR GC TIDAK NGAMUK
            // ========================================================
            PlayerInfo[] playerPool = new PlayerInfo[30];
            for (int i = 0; i < 30; i++) playerPool[i] = new PlayerInfo();

            MonsterInfo[] monsterPool = new MonsterInfo[150];
            for (int i = 0; i < 150; i++) monsterPool[i] = new MonsterInfo();

            List<PlayerInfo> tempPlayers = new ArrayList<>(30);
            List<MonsterInfo> tempMonsters = new ArrayList<>(150);

            while (isRunning) {
                LocalSocket socket = null;
                DataInputStream dis = null;
                try {
                    socket = new LocalSocket();
                    socket.connect(new LocalSocketAddress("and.sys.display.buffer", LocalSocketAddress.Namespace.ABSTRACT));
                    dis = new DataInputStream(socket.getInputStream());

                    byte[] countBuffer = new byte[4];
                    byte[] packetBuffer = new byte[84]; 
                    byte[] monPacketBuffer = new byte[20]; 

                    boolean wasLocalPlayerAlive = false;

                    while (isRunning) {
                        tempPlayers.clear();
                        tempMonsters.clear();

                        // --- 1. BACA PLAYER ---
                        dis.readFully(countBuffer);
                        int playerCount = ByteBuffer.wrap(countBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
                        if (playerCount > 30 || playerCount < 0) playerCount = 0; 

                        if (playerCount == 0) {
                            synchronized (this) {
                                if (!stringCache.isEmpty()) {
                                    stringCache.clear(); heroIconCache.clear(); activeHeroBitmaps.clear();
                                    lastUltCdTracker.clear();
                                }
                            }
                        }

                        boolean localPlayerFoundThisFrame = false;

                        for (int i = 0; i < playerCount; i++) {
                            dis.readFully(packetBuffer);
                            ByteBuffer bb = ByteBuffer.wrap(packetBuffer).order(ByteOrder.LITTLE_ENDIAN);

                            PlayerInfo p = playerPool[i]; 
                            p.entityId = bb.getInt(); 
                            p.x = bb.getFloat(); p.y = bb.getFloat(); p.z = bb.getFloat();
                            p.screenX = bb.getFloat(); p.screenY = bb.getFloat();
                            p.cd1 = bb.getInt(); p.cd2 = bb.getInt(); p.cd3 = bb.getInt(); p.spell = bb.getInt();
                            p.campType = bb.getInt();
                            p.hp = bb.getInt(); p.hpMax = bb.getInt();

                            if (stringCache.containsKey(p.entityId)) {
                                bb.position(bb.position() + 32); 
                                p.heroName = stringCache.get(p.entityId);
                            } else {
                                byte[] nameBytes = new byte[32];
                                bb.get(nameBytes);
                                String name = new String(nameBytes).trim();
                                stringCache.put(p.entityId, name);
                                p.heroName = name;
                            }
                            p.shortName = (p.heroName != null && p.heroName.length() >= 3) ? p.heroName.substring(0, 3).toUpperCase() : "???";
                            
                            tempPlayers.add(p.copy());

                            if (p.campType == 999) {
                                localPlayerFoundThisFrame = true;
                            }

                            // ==========================================
                            // 🟢 FITUR PREMIUM: ENEMY ULTI TRACKER
                            // ==========================================
                            boolean isAlertUlti = prefs.getBoolean("alert_enemy_ulti", false);
                            
                            if (isAlertUlti && p.campType != 999 && p.hp > 0) {
                                int currentUltCd = p.cd3; 
                                Integer lastUltCd = lastUltCdTracker.get(p.entityId);

                                if (lastUltCd != null && lastUltCd <= 0 && currentUltCd > 0) {
                                    String niceName = "Enemy";
                                    if (p.heroName != null && p.heroName.length() > 0) {
                                        niceName = p.heroName.substring(0, 1).toUpperCase() + p.heroName.substring(1).toLowerCase();
                                    }
                                    final String finalName = niceName;

                                    mainHandler.post(() -> {
                                        android.widget.Toast.makeText(getContext(), 
                                            "⚠️ " + finalName + " ULT!!", 
                                            android.widget.Toast.LENGTH_SHORT).show();
                                    });
                                }
                                lastUltCdTracker.put(p.entityId, currentUltCd);
                            }
                        }

                        // ==========================================
                        // 🟢 LOGIKA MEME SOUND
                        // ==========================================
                        if (playerCount > 0) {
                            if (wasLocalPlayerAlive && !localPlayerFoundThisFrame) {
                                mainHandler.post(() -> {
                                    android.widget.Toast.makeText(getContext(), "🗿🗿🗿", android.widget.Toast.LENGTH_SHORT).show();
                                    SoundManager.playMemeSound(getContext());
                                });
                            }
                            wasLocalPlayerAlive = localPlayerFoundThisFrame;
                        } else {
                            wasLocalPlayerAlive = false; 
                        }


                        // --- 2. BACA MONSTER ---
                        dis.readFully(countBuffer);
                        int monsterCount = ByteBuffer.wrap(countBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
                        if (monsterCount > 150 || monsterCount < 0) monsterCount = 0;
                        
                        for(int i = 0; i < monsterCount; i++) {
                            dis.readFully(monPacketBuffer);
                            ByteBuffer mbb = ByteBuffer.wrap(monPacketBuffer).order(ByteOrder.LITTLE_ENDIAN);
                            
                            MonsterInfo m = monsterPool[i];
                            m.id = mbb.getInt();
                            m.screenX = mbb.getFloat();
                            m.screenY = mbb.getFloat();
                            m.hp = mbb.getInt();
                            m.hpMax = mbb.getInt();
                            tempMonsters.add(m);
                        }
                        
                        synchronized (this) { 
                            players.clear();
                            players.addAll(tempPlayers); 
                            monsters.clear();
                            monsters.addAll(tempMonsters);
                        }
                    }
                } catch (Exception e) {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                } finally {
                    try { if (dis != null) dis.close(); } catch (Exception ignored) {}
                    try { if (socket != null) socket.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private float[] worldToMinimap(int campType, float wx, float wz) {
        float angleCos = campType == 2 ? (float)Math.cos(Math.toRadians(314.60)) : (float)Math.cos(Math.toRadians(134.76));
        float angleSin = campType == 2 ? (float)Math.sin(Math.toRadians(314.60)) : (float)Math.sin(Math.toRadians(134.76));
        float negWz = -wz;
        return new float[]{(angleCos * wx - angleSin * negWz) / 74.11f, (angleSin * wx + angleCos * negWz) / 74.11f};
    }

    private String getMonsterName(int id) {
        switch(id) {
            case 2002: return "Lord";
            case 2110: return "Lord"; 
            case 2003: return "Turtle";
            case 2004: return "Red Buff";
            case 2005: return "Blue Buff";
            case 2072: case 2056: return "Litho";
            default: return null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        boolean isRadarEnabled = prefs.getBoolean("radar_enable", false);
        boolean isEspCircle = prefs.getBoolean("esp_circle", false);
        boolean isEspCooldown = prefs.getBoolean("esp_cooldown", false);
        boolean isEspHealth = prefs.getBoolean("esp_health", false);
        boolean isEspMonster = prefs.getBoolean("esp_monster", false);
        boolean isAlertMonster = prefs.getBoolean("alert_monster_hp", false); 
        boolean isEspLine = prefs.getBoolean("esp_line", false); 
        
        if (!isRadarEnabled && !isEspCircle && !isEspCooldown && !isEspHealth && !isEspMonster && !isAlertMonster && !isEspLine) return;

        float size = prefs.getFloat("radar_size", 338.0f);
        float posX = prefs.getFloat("radar_pos_x", 71.0f);
        float posY = prefs.getFloat("radar_pos_y", 0.0f);
        float iconSize = prefs.getFloat("radar_icon_size", 37.0f);

        if (isRadarEnabled && prefs.getBoolean("radar_border", true)) {
            canvas.drawRect(posX, posY, posX + size, posY + size, borderPaint);
        }

        List<PlayerInfo> currentPlayers = renderPlayers;
        List<MonsterInfo> currentMonsters = renderMonsters;
        synchronized (this) { 
            currentPlayers.clear();
            currentPlayers.addAll(players); 
            currentMonsters.clear();
            currentMonsters.addAll(monsters);
        }
        
        float halfIcon = iconSize * 0.5f;

        // =======================================================
        // 1. CARI KOORDINAT ASLI HERO KITA DI LAYAR
        // =======================================================
        float myScreenX = getWidth() / 2f;  
        float myScreenY = getHeight() / 2f; 
        
        for (PlayerInfo p : currentPlayers) {
            if (p.campType == 999 && p.screenX != -1000.0f) {
                myScreenX = p.screenX * getWidth();
                myScreenY = getHeight() - (p.screenY * getHeight());
                break; 
            }
        }

        // =====================================
        // LOOP 1: DRAW ESP PLAYER
        // =====================================
        for (PlayerInfo p : currentPlayers) {
            
            if (p.campType == 999) continue; 
            
            if (isRadarEnabled) {
                float[] mmOut = worldToMinimap(p.campType, p.x, p.z);
                float drawX = (mmOut[0] * size) + posX + (size * 0.5f);
                float drawY = (mmOut[1] * size) + posY + (size * 0.5f);
                
                drawX = Math.max(posX, Math.min(drawX, posX + size));
                drawY = Math.max(posY, Math.min(drawY, posY + size));

                Bitmap heroIcon = getHeroIcon(p.heroName, iconSize);
                if (heroIcon != null) {
                    canvas.drawBitmap(heroIcon, drawX - halfIcon, drawY - halfIcon, null);
                } else {
                    canvas.drawCircle(drawX, drawY, halfIcon, fallbackPaint);
                    canvas.drawText(p.shortName, drawX, drawY + 6f, textPaint);
                }
                canvas.drawCircle(drawX, drawY, halfIcon, borderRedPaint);
            }
            
            
            float scaledX = p.screenX * getWidth();
            float scaledY = getHeight() - (p.screenY * getHeight());
            
            boolean isVisibleOnScreen = (p.screenX >= 0.0f && p.screenX <= 1.0f && p.screenY >= 0.0f && p.screenY <= 1.0f);

            if (isEspLine && p.screenX != -1000.0f) {
                if ((float) p.hp / p.hpMax < 0.3f) {
                    espLinePaint.setColor(Color.argb(180, 255, 50, 50));
                } else {
                    espLinePaint.setColor(Color.argb(150, 255, 255, 255));
                }
                
                canvas.drawLine(myScreenX, myScreenY, scaledX, scaledY, espLinePaint);
            }
            if (isVisibleOnScreen) {
                float espIconSize = 52f; 
                float espHalf = espIconSize / 2f;

                if (isEspCircle) {
                    Bitmap heroIcon = getHeroIcon(p.heroName, espIconSize); 
                    if (heroIcon != null && !heroIcon.isRecycled()) {
                        tempRect.set(scaledX - espHalf, scaledY - espHalf, scaledX + espHalf, scaledY + espHalf);
                        canvas.drawCircle(scaledX, scaledY, espHalf, shadowPaint);
                        canvas.drawBitmap(heroIcon, null, tempRect, null);
                        canvas.drawCircle(scaledX, scaledY, espHalf, espOutlinePaint);
                    } else {
                        canvas.drawCircle(scaledX, scaledY, espHalf, shadowPaint);
                        canvas.drawCircle(scaledX, scaledY, espHalf, espOutlinePaint);
                        canvas.drawText(p.shortName, scaledX, scaledY + 6f, textPaint);
                    }
                }
                
                if (isEspHealth && p.hpMax > 0) {
                    float hpPercent = Math.max(0f, Math.min(1f, (float) p.hp / p.hpMax));
                    int hpColor = (hpPercent > 0.5f) ? Color.argb(255, 0, 230, 100) :
                                  (hpPercent > 0.25f) ? Color.argb(255, 255, 200, 0) : Color.argb(255, 255, 50, 50);

                    hpArcPaint.setColor(hpColor);
                    float arcPadding = 7f; 
                    arcRect.set(scaledX - espHalf - arcPadding, scaledY - espHalf - arcPadding, 
                                scaledX + espHalf + arcPadding, scaledY + espHalf + arcPadding);

                    canvas.drawArc(arcRect, 135f, 270f, false, hpBgArcPaint);
                    canvas.drawArc(arcRect, 135f, 270f * hpPercent, false, hpArcPaint);
                }
                
                if (isEspCooldown) {
                    float baseX = scaledX + 60; 
                    float baseY = scaledY - 15; 
                    float boxSize = 46f;        
                    float gap = 49f;            
                    
                    int[] cds = {p.cd1, p.cd2, p.cd3, p.spell};
                    
                    cdTextPaint.setTextSize(22f);       
                    cdTextStrokePaint.setTextSize(22f); 

                    for (int i = 0; i < 4; i++) {
                        float x = baseX + i * gap;
                        float y = baseY;
                        float half = boxSize / 2;
                        
                        boolean isReady = (cds[i] <= 0);
                        boolean isUlt = (i == 2);
                        boolean isSpell = (i == 3);
                        
                        int bgColor;
                        String cdText;
                        
                        if (isReady) {
                            bgColor = Color.argb(100, 15, 15, 20); 
                            borderBoxPaint.setColor(Color.argb(80, 255, 255, 255)); 
                            cdText = "-";
                            cdTextPaint.setColor(Color.argb(150, 255, 255, 255));
                        } else {
                            cdText = (cds[i] >= 0 && cds[i] <= 150) ? CD_STRINGS[cds[i]] : String.valueOf(cds[i]);
                            cdTextPaint.setColor(Color.WHITE);
                            
                            if (isUlt) {
                                bgColor = Color.argb(160, 40, 0, 0);
                                borderBoxPaint.setColor(Color.parseColor("#FF1744")); 
                            } else if (isSpell) {
                                bgColor = Color.argb(160, 0, 20, 40);
                                borderBoxPaint.setColor(Color.parseColor("#00E5FF")); 
                            } else {
                                bgColor = Color.argb(160, 40, 25, 0);
                                borderBoxPaint.setColor(Color.parseColor("#FFD600")); 
                            }
                        }
                        
                        cdBgPaint.setColor(bgColor);
                        tempRect.set(x - half, y - half, x + half, y + half);
                        canvas.drawRoundRect(tempRect, 6f, 6f, cdBgPaint);
                        canvas.drawRoundRect(tempRect, 6f, 6f, borderBoxPaint);
                        
                        float textY = y + 6f; 
                        if (isReady) {
                            canvas.drawText(cdText, x, textY, cdTextPaint);
                        } else {
                            canvas.drawText(cdText, x, textY, cdTextStrokePaint);
                            canvas.drawText(cdText, x, textY, cdTextPaint);
                        }
                    }
                }
            }
        }

        // =====================================
        // LOOP 2: MONSTER ESP (DI BAWAH KAKI MONSTER)
        // =====================================
        if (isEspMonster) {
            for (MonsterInfo m : currentMonsters) {
                if (m.screenX < 0 || m.screenX > 1.0f || m.screenY < 0 || m.screenY > 1.0f) continue;
                if (m.hp <= 0 || m.hpMax <= 0) continue;
                
                String monName = getMonsterName(m.id);
                if (monName == null) continue;
                
                float mX = m.screenX * getWidth();
                float mY = getHeight() - (m.screenY * getHeight());
                
                float monHpPct = Math.max(0f, Math.min(1f, (float) m.hp / m.hpMax));
                int monHpColor = (monHpPct > 0.5f) ? Color.parseColor("#00E676") : 
                                 (monHpPct > 0.2f) ? Color.parseColor("#FFCA28") : Color.parseColor("#EF5350"); 
                
                float barW = 200f; 
                float barH = 30f;  
                
                cdTextPaint.setTextSize(18f);
                cdTextStrokePaint.setTextSize(18f);
                cdTextPaint.setColor(Color.WHITE);
                
                float textWidth = cdTextPaint.measureText(monName);
                float nameY = mY - barH - 20f; 
                
                tempRect.set(mX - (textWidth/2) - 8, nameY - 18f, mX + (textWidth/2) + 8, nameY + 6f);
                canvas.drawRoundRect(tempRect, 6f, 6f, monTextBg);

                canvas.drawText(monName, mX, nameY, cdTextStrokePaint);
                canvas.drawText(monName, mX, nameY, cdTextPaint);
                
                tempRect.set(mX - barW/2, mY, mX + barW/2, mY + barH);
                canvas.drawRoundRect(tempRect, 6f, 6f, monBgBar); 
                
                monFillBar.setColor(monHpColor);
                tempRect.set(mX - barW/2, mY, mX - barW/2 + (barW * monHpPct), mY + barH);
                canvas.drawRoundRect(tempRect, 6f, 6f, monFillBar); 
                
                tempRect.set(mX - barW/2, mY, mX + barW/2, mY + barH);
                canvas.drawRoundRect(tempRect, 6f, 6f, monStrokeBar);

                String hpString = m.hp + " / " + m.hpMax;
                float hpTextY = mY + 22f; 
                
                canvas.drawText(hpString, mX, hpTextY, monHpTextStrokePaint);
                canvas.drawText(hpString, mX, hpTextY, monHpTextPaint);
            }
        }

        // =====================================
        // LOOP 3: BOSS HP ALERT (TOP SCREEN - ONLY WHEN DAMAGED)
        // =====================================
        if (isAlertMonster) {
            long currentTime = System.currentTimeMillis();
            float alertY = 115f;
            float alertX = getWidth() / 2f;
            
            for (MonsterInfo m : currentMonsters) {
                if (m.hp <= 0 || m.hpMax <= 0) continue;
                
                if (m.id == 2002 || m.id == 2003 || m.id == 2110) {
                    
                    Integer lastHp = lastMonsterHp.get(m.id);
                    boolean isBeingAttacked = (lastHp != null && m.hp < lastHp);
                    
                    lastMonsterHp.put(m.id, m.hp);
                    
                    if (isBeingAttacked) {
                        monsterAlertExpiry.put(m.id, currentTime + ALERT_DURATION_MS);
                    }
                    
                    Long expiry = monsterAlertExpiry.get(m.id);
                    boolean shouldShowAlert = (expiry != null && currentTime < expiry);
                    
                    if (!shouldShowAlert) continue;
                    
                    boolean isTurtle = (m.id == 2003);
                    String bossName = isTurtle ? "⚔️ TURTLE" : "⚔️ LORD";
                    int hpColor = isTurtle ? Color.parseColor("#00E5FF") : Color.parseColor("#D500F9");
                    
                    String hpText = bossName + " HP: " + m.hp + " / " + m.hpMax;
                    
                    alertTextPaint.setColor(Color.WHITE);
                    float textW = alertTextPaint.measureText(hpText);
                    float boxW = Math.max(220f, textW + 40f);
                    float boxH = 46f;
                    float halfW = boxW / 2f;
                    
                    tempRect.set(alertX - halfW, alertY, alertX + halfW, alertY + boxH);
                    canvas.drawRoundRect(tempRect, 10f, 10f, alertBgPaint);
                    
                    borderBoxPaint.setColor(hpColor);
                    canvas.drawRoundRect(tempRect, 10f, 10f, borderBoxPaint);
                    
                    float textY = alertY + 24f;
                    canvas.drawText(hpText, alertX, textY, alertTextStrokePaint);
                    canvas.drawText(hpText, alertX, textY, alertTextPaint);
                    
                    float barW = boxW - 24f;
                    float barH = 6f;
                    float barY = alertY + 32f;
                    float hpPct = Math.max(0f, Math.min(1f, (float) m.hp / m.hpMax));
                    
                    tempRect.set(alertX - barW/2, barY, alertX + barW/2, barY + barH);
                    canvas.drawRoundRect(tempRect, 3f, 3f, monBgBar);
                    
                    monFillBar.setColor(hpColor);
                    tempRect.set(alertX - barW/2, barY, alertX - barW/2 + (barW * hpPct), barY + barH);
                    canvas.drawRoundRect(tempRect, 3f, 3f, monFillBar);
                    
                    alertY += boxH + 15f;
                }
            }
        }
    }

    public List<String> getActiveEnemyNames() {
        List<String> names = new ArrayList<>();
        synchronized (this) {
            for (PlayerInfo p : players) {
                if (p.campType != 999 && p.heroName != null) names.add(p.heroName);
            }
        }
        return names;
    }
    
    public void resetMonsterAlertCache() {
        lastMonsterHp.clear();
        monsterAlertExpiry.clear();
    }

    public Bitmap getRawIcon(String key) { 
        return getOriginalBitmapLazy(key); 
    }

    public void destroy() { 
        isRunning = false; 
        try {
            for (Bitmap b : heroIconCache.values()) {
                if (b != null && !b.isRecycled()) b.recycle();
            }
            heroIconCache.clear();
            
            for (Bitmap b : activeHeroBitmaps.values()) {
                if (b != null && !b.isRecycled()) b.recycle();
            }
            activeHeroBitmaps.clear();
        } catch (Exception ignored) {}
    }
}