# =========================================================
# SUPER AGGRESSIVE OBFUSCATION CONFIG
# =========================================================

# 1. Menghilangkan semua metadata yang bisa membantu reverse engineering
-renamesourcefileattribute SourceFile
-keepattributes !*Annotation*,!Signature,!Exceptions,!InnerClasses,!EnclosingMethod,SourceFile,LineNumberTable

# 2. Repackaging: Pindahkan semua class ke root package (menghilangkan struktur folder com/overlay)
-repackageclasses ''
-allowaccessmodification

# 3. Obfuscation Nama: Gunakan kamus karakter yang membingungkan (opsional, default a,b,c)
-useuniqueclassmembernames
-dontusemixedcaseclassnames

# 4. Optimasi Maksimal
-optimizationpasses 5
-overloadaggressively

# 5. Entry Points (Hanya keep yang benar-benar perlu dipanggil dari luar/smali)
# Kita perlu keep nama class Service agar bisa dipanggil via Intent di inject.smali
-keep class com.overlay.OverlayService {
    public <init>();
    public void onCreate();
    public int onStartCommand(android.content.Intent, int, int);
    public void onDestroy();
}

# Keep method yang dipanggil secara refleksi atau dari native jika ada
-keepclassmembers class com.overlay.KeyAuthManager {
    public boolean isKeyValid();
    public void validateKey(java.lang.String, com.overlay.KeyAuthManager$AuthCallback);
}

# Keep interface callback agar tidak hilang
-keep interface com.overlay.KeyAuthManager$AuthCallback { *; }

# Buang semua log untuk keamanan
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Jangan obfuscate library standar jika ada (tapi di sini tidak ada external deps)
-keep class org.json.** { *; }
