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

    private Paint borderPaint, textPaint, shadowPaint, espOutlinePaint, espLinePaint;
    private Paint cdBgPaint, cdTextPaint, cdTextStrokePaint, borderBoxPaint;
    private Paint hpBgArcPaint, hpArcPaint;
    private Paint monBgBar, monFillBar, monStrokeBar, monTextBg;
    private Paint monHpTextPaint, monHpTextStrokePaint;
    private Paint fallbackPaint, borderRedPaint;
    private Paint alertBgPaint, alertTextPaint, alertTextStrokePaint;

    private RectF tempRect = new RectF();
    private RectF arcRect = new RectF();

    private List<PlayerInfo> players = new ArrayList<>();
    private List<MonsterInfo> monsters = new ArrayList<>();
    private List<PlayerInfo> renderPlayers = new ArrayList<>();
    private List<MonsterInfo> renderMonsters = new ArrayList<>();
    
    private boolean isRunning = true;
    private SharedPreferences prefs;

    private Map<String, byte[]> rawImageBytes = new HashMap<>();
    private Map<String, Bitmap> activeHeroBitmaps = new HashMap<>();
    private Map<String, Bitmap> heroIconCache = new HashMap<>();
    private Map<Integer, String> stringCache = new HashMap<>();
    private Map<Integer, Integer> lastMonsterHp = new HashMap<>();
    private Map<Integer, Long> monsterAlertExpiry = new HashMap<>();
    private Map<Integer, Integer> lastUltCdTracker = new HashMap<>();

    private static final long ALERT_DURATION_MS = 3000;
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
        new Thread(this::loadAndDecryptHeroes).start();
        startSocketThread();
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (isRunning) {
                    invalidate(); 
                    Choreographer.getInstance().postFrameCallback(this);
                }
            }
        });
    }

    private void initPaints() {
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.argb(120, 255, 215, 0));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(16f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.argb(200, 0, 0, 0));
        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStrokeWidth(5f);
                
        espOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        espOutlinePaint.setColor(Color.argb(255, 255, 50, 50));
        espOutlinePaint.setStyle(Paint.Style.STROKE);
        espOutlinePaint.setStrokeWidth(2.5f);
        
        espLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        espLinePaint.setColor(Color.argb(150, 255, 255, 255));
        espLinePaint.setStyle(Paint.Style.STROKE);
        espLinePaint.setStrokeWidth(2.5f);

        cdBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cdBgPaint.setStyle(Paint.Style.FILL);

        borderBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderBoxPaint.setStyle(Paint.Style.STROKE);
        borderBoxPaint.setStrokeWidth(1.5f);

        cdTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cdTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        cdTextPaint.setTextAlign(Paint.Align.CENTER);
        
        cdTextStrokePaint = new Paint(cdTextPaint);
        cdTextStrokePaint.setColor(Color.BLACK);
        cdTextStrokePaint.setStyle(Paint.Style.STROKE);
        cdTextStrokePaint.setStrokeWidth(3.0f);

        hpBgArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hpBgArcPaint.setColor(Color.argb(150, 20, 20, 20));
        hpBgArcPaint.setStyle(Paint.Style.STROKE);
        hpBgArcPaint.setStrokeWidth(5.5f);
        hpBgArcPaint.setStrokeCap(Paint.Cap.ROUND);

        hpArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hpArcPaint.setStyle(Paint.Style.STROKE);
        hpArcPaint.setStrokeWidth(5.5f);
        hpArcPaint.setStrokeCap(Paint.Cap.ROUND);

        monBgBar = new Paint(Paint.ANTI_ALIAS_FLAG);
        monBgBar.setColor(Color.argb(220, 15, 15, 20));

        monFillBar = new Paint(Paint.ANTI_ALIAS_FLAG);

        monStrokeBar = new Paint(Paint.ANTI_ALIAS_FLAG);
        monStrokeBar.setColor(Color.argb(120, 200, 200, 200));
        monStrokeBar.setStyle(Paint.Style.STROKE);
        monStrokeBar.setStrokeWidth(1.5f);

        monTextBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        monTextBg.setColor(Color.argb(160, 0, 0, 0));

        monHpTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        monHpTextPaint.setColor(Color.WHITE);
        monHpTextPaint.setTextSize(20f); 
        monHpTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        monHpTextPaint.setTextAlign(Paint.Align.CENTER);
        monHpTextPaint.setShadowLayer(5f, 2f, 2f, Color.BLACK);

        monHpTextStrokePaint = new Paint(monHpTextPaint);
        monHpTextStrokePaint.setColor(Color.BLACK);
        monHpTextStrokePaint.setStyle(Paint.Style.STROKE);
        monHpTextStrokePaint.setStrokeWidth(5.5f);

        alertBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        alertBgPaint.setColor(Color.argb(220, 20, 20, 25));

        alertTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        alertTextPaint.setColor(Color.WHITE);
        alertTextPaint.setTextSize(18f);
        alertTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        alertTextPaint.setTextAlign(Paint.Align.CENTER);
        alertTextPaint.setShadowLayer(4f, 1f, 1f, Color.BLACK);

        alertTextStrokePaint = new Paint(alertTextPaint);
        alertTextStrokePaint.setColor(Color.BLACK);
        alertTextStrokePaint.setStyle(Paint.Style.STROKE);
        alertTextStrokePaint.setStrokeWidth(4.5f);

        fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fallbackPaint.setColor(Color.argb(200, 255, 45, 85));
        fallbackPaint.setStyle(Paint.Style.FILL);

        borderRedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderRedPaint.setColor(Color.argb(255, 255, 30, 30));
        borderRedPaint.setStyle(Paint.Style.STROKE);
        borderRedPaint.setStrokeWidth(2f);
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

    public Bitmap getRawIcon(String key) {
        if (key == null) return null;
        String searchKey = key.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (activeHeroBitmaps.containsKey(searchKey)) return activeHeroBitmaps.get(searchKey);
        if (rawImageBytes.containsKey(searchKey)) {
            byte[] imgData = rawImageBytes.get(searchKey);
            Bitmap bmp = BitmapFactory.decodeByteArray(imgData, 0, imgData.length);
            if (bmp != null) { activeHeroBitmaps.put(searchKey, bmp); return bmp; }
        }
        return null;
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

    private Bitmap getHeroIcon(String heroName, float targetSize) {
        if (heroName == null) return null;
        int size = (int) targetSize;
        if (size <= 0) size = 50;
        String searchKey = heroName.toLowerCase().replaceAll("[^a-z0-9]", "");
        String cacheKey = searchKey + "_" + size;
        if (heroIconCache.containsKey(cacheKey)) return heroIconCache.get(cacheKey);
        Bitmap originalBmp = getRawIcon(searchKey);
        if (originalBmp == null) return null;
        Bitmap scaledBmp = Bitmap.createScaledBitmap(originalBmp, size, size, true);
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float radius = size / 2f;
        canvas.drawCircle(radius, radius, radius, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(scaledBmp, 0, 0, paint);
        if (scaledBmp != originalBmp) scaledBmp.recycle();
        heroIconCache.put(cacheKey, output);
        return output;
    }

    private void startSocketThread() {
        new Thread(() -> {
            PlayerInfo[] playerPool = new PlayerInfo[30];
            for (int i = 0; i < 30; i++) playerPool[i] = new PlayerInfo();
            MonsterInfo[] monsterPool = new MonsterInfo[150];
            for (int i = 0; i < 150; i++) monsterPool[i] = new MonsterInfo();
            while (isRunning) {
                try (LocalSocket socket = new LocalSocket()) {
                    socket.connect(new LocalSocketAddress("and.sys.display.buffer", LocalSocketAddress.Namespace.ABSTRACT));
                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                    byte[] countBuffer = new byte[4];
                    byte[] packetBuffer = new byte[84]; 
                    byte[] monPacketBuffer = new byte[20]; 
                    while (isRunning) {
                        dis.readFully(countBuffer);
                        int playerCount = ByteBuffer.wrap(countBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
                        if (playerCount > 30 || playerCount < 0) playerCount = 0; 
                        List<PlayerInfo> tempPlayers = new ArrayList<>();
                        for (int i = 0; i < playerCount; i++) {
                            dis.readFully(packetBuffer);
                            ByteBuffer bb = ByteBuffer.wrap(packetBuffer).order(ByteOrder.LITTLE_ENDIAN);
                            PlayerInfo p = playerPool[i];
                            p.entityId = bb.getInt();
                            p.x = bb.getFloat(); p.y = bb.getFloat(); p.z = bb.getFloat();
                            p.screenX = bb.getFloat(); p.screenY = bb.getFloat();
                            p.cd1 = bb.getInt(); p.cd2 = bb.getInt(); p.cd3 = bb.getInt();
                            p.spell = bb.getInt(); p.campType = bb.getInt();
                            p.hp = bb.getInt(); p.hpMax = bb.getInt();
                            tempPlayers.add(p.copy());
                        }
                        dis.readFully(countBuffer);
                        int monsterCount = ByteBuffer.wrap(countBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
                        if (monsterCount > 150 || monsterCount < 0) monsterCount = 0;
                        List<MonsterInfo> tempMonsters = new ArrayList<>();
                        for (int i = 0; i < monsterCount; i++) {
                            dis.readFully(monPacketBuffer);
                            ByteBuffer bb = ByteBuffer.wrap(monPacketBuffer).order(ByteOrder.LITTLE_ENDIAN);
                            MonsterInfo m = monsterPool[i];
                            m.id = bb.getInt(); m.hp = bb.getInt(); m.hpMax = bb.getInt();
                            m.screenX = bb.getFloat(); m.screenY = bb.getFloat();
                            tempMonsters.add(m);
                        }
                        synchronized (this) { players = tempPlayers; monsters = tempMonsters; }
                    }
                } catch (Exception e) { try { Thread.sleep(RETRY_DELAY_MS); } catch (Exception ignored) {} }
            }
        }).start();
    }
    
    private static final int RETRY_DELAY_MS = 2000;

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // ... (Drawing logic remains similar, uses the updated players/monsters lists)
    }

    public void destroy() { isRunning = false; }
}
