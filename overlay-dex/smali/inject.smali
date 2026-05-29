# =============================================================
# inject.smali
# Entry point - tempel ke Application.onCreate() target APK
# =============================================================
#
# Langkah:
# 1. apktool d DualSpace.apk -o out
# 2. Buka smali Application target
# 3. Tempel snippet ini setelah invoke-super onCreate
# 4. Pastikan register v0/v1 tidak bentrok, ganti jika perlu
# =============================================================

    new-instance v0, Landroid/content/Intent;

    const-class v1, Lcom/overlay/OverlayService;

    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {p0, v0}, Landroid/content/Context;->startService(Landroid/content/Intent;)Landroid/content/ComponentName;

    move-result-object v0

# =============================================================
# Tambahkan di AndroidManifest.xml target (setelah apktool decode):
#
# Di dalam <application>:
#   <service
#       android:name="com.overlay.OverlayService"
#       android:exported="false"/>
#
# Di luar <application> (top level):
#   <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
#
# (Dual Space biasanya sudah punya SYSTEM_ALERT_WINDOW, cek dulu)
# =============================================================
