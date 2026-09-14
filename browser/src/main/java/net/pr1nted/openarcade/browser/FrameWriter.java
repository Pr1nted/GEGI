package net.pr1nted.openarcade.browser;

import net.pr1nted.openarcade.browser.api.BrowserProtocol;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Chromium's frames into the file the game maps, as RGBA (CEF paints BGRA; the game
 * uploads RGBA, so red and blue swap here, in this process, not on the render thread).
 * The sequence number is odd while a frame is being written; see BrowserProtocol.
 */
final class FrameWriter implements FrameSink {

    private final MappedByteBuffer map;
    private final IntBuffer pixels;
    private long sequence;

    FrameWriter(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            if (raf.length() < BrowserProtocol.fileBytes()) raf.setLength(BrowserProtocol.fileBytes());
            map = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, BrowserProtocol.fileBytes());
        }
        map.order(ByteOrder.LITTLE_ENDIAN);
        map.putInt(BrowserProtocol.OFFSET_MAGIC, BrowserProtocol.MAGIC);
        map.putInt(BrowserProtocol.OFFSET_VERSION, BrowserProtocol.VERSION);
        map.putLong(BrowserProtocol.OFFSET_SEQUENCE, 0);
        ByteBuffer body = map.duplicate();
        body.position(BrowserProtocol.HEADER_BYTES);
        pixels = body.slice().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
    }

    @Override
    public synchronized void frame(ByteBuffer bgra, int width, int height) {
        if (width <= 0 || height <= 0 || width > BrowserProtocol.MAX_WIDTH || height > BrowserProtocol.MAX_HEIGHT) return;
        int count = width * height;
        IntBuffer source = bgra.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        if (source.capacity() < count) return;

        map.putLong(BrowserProtocol.OFFSET_SEQUENCE, ++sequence); // odd: writing
        for (int i = 0; i < count; i++) {
            int v = source.get(i);
            // little-endian BGRA is 0xAARRGGBB; RGBA is 0xAABBGGRR: swap red and blue.
            pixels.put(i, (v & 0xFF00FF00) | ((v >>> 16) & 0xFF) | ((v & 0xFF) << 16));
        }
        map.putInt(BrowserProtocol.OFFSET_WIDTH, width);
        map.putInt(BrowserProtocol.OFFSET_HEIGHT, height);
        map.putLong(BrowserProtocol.OFFSET_SEQUENCE, ++sequence); // even: whole
    }
}
