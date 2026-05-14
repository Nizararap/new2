package com.google.android.ext;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Scanner;

public class SHMManager {
    private static final int SHM_SIZE = 1024 * 16;
    private MappedByteBuffer buffer;

    public boolean connect() {
        try {
            // Baca FD dari file yang dibuat Native
            File fdFile = new File("/data/data/com.google.android.ext/cache/shm_fd");
            if (!fdFile.exists()) return false;

            Scanner sc = new Scanner(fdFile);
            int fd = sc.nextInt();
            sc.close();

            // Akses SHM via /proc/self/fd/
            RandomAccessFile raf = new RandomAccessFile("/proc/self/fd/" + fd, "rw");
            buffer = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, SHM_SIZE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void writeConfig(byte[] configData) {
        if (buffer == null) return;
        // Offset config di SharedBuffer: version(4) + playerCount(4) + players(PlayerData[10] * 52)
        // PlayerData size = entityId(4) + x(4) + y(4) + z(4) + campType(4) + heroName(32) = 52 bytes
        int configOffset = 4 + 4 + (10 * 52);
        buffer.position(configOffset);
        buffer.put(configData);
    }

    public int getPlayerCount() {
        if (buffer == null) return 0;
        return buffer.getInt(4);
    }

    public void readPlayerData(int index, byte[] output) {
        if (buffer == null || index >= 10) return;
        int playerOffset = 8 + (index * 52);
        buffer.position(playerOffset);
        buffer.get(output);
    }
}
