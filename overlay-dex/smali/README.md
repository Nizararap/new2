# Cara Inject classes2.dex ke APK Dual Space

## Output build
- `classes2.dex` — inject ke APK target
- `inject.smali` — snippet entry point

---

## Langkah-langkah

### 1. Decode APK target
```
apktool d DualSpace.apk -o dual_decoded
```

### 2. Copy classes2.dex
```
cp classes2.dex dual_decoded/
```

### 3. Patch AndroidManifest.xml
Tambahkan di dalam `<application>`:
```xml
<service
    android:name="com.overlay.OverlayService"
    android:exported="false"/>
```
Cek juga permission `SYSTEM_ALERT_WINDOW` sudah ada.

### 4. Tambah entry point di smali
- Buka folder `dual_decoded/smali/`
- Cari file Application kelas target (biasanya `ApplicationImpl.smali` atau sejenisnya)
- Temukan method `.method public onCreate()V`
- Tempel isi `inject.smali` **setelah** baris `invoke-super`

### 5. Build + sign
```bash
apktool b dual_decoded -o DualSpace_mod.apk
zipalign -v 4 DualSpace_mod.apk DualSpace_mod_aligned.apk
apksigner sign --ks debug.keystore --ks-pass pass:android DualSpace_mod_aligned.apk
```

> Buat debug.keystore jika belum ada:
> ```
> keytool -genkey -v -keystore debug.keystore -alias androiddebugkey \
>   -keyalg RSA -keysize 2048 -validity 10000 \
>   -storepass android -keypass android \
>   -dname "CN=Android Debug,O=Android,C=US"
> ```
