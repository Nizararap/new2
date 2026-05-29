package com.overlay;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SoundManager {
    private static MediaPlayer mediaPlayer;

    public static void playMemeSound(Context context) {
        try {
            // Hentikan suara jika sebelumnya masih berbunyi
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }

            // 1. Tentukan path file di folder cache internal
            File tempFile = new File(context.getCacheDir(), "fahh.mp3");

            // 2. Jika file belum ada di cache, copy dari assets
            if (!tempFile.exists()) {
                InputStream is = context.getAssets().open("fahh.mp3");
                FileOutputStream fos = new FileOutputStream(tempFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fos.flush();
                fos.close();
                is.close();
            }

            // 3. Putar langsung dari path file fisik (Bebas dari masalah kompresi/Virtual App)
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(tempFile.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
            });

        } catch (Exception e) {
            e.printStackTrace();
            new Handler(Looper.getMainLooper()).post(() -> 
                Toast.makeText(context, "Gagal putar MP3: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        }
    }
}