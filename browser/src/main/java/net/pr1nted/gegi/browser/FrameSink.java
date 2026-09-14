package net.pr1nted.gegi.browser;

import java.nio.ByteBuffer;

/** Where Chromium's painted frames go: BGRA, {@code width * height * 4} bytes. */
public interface FrameSink {
    void frame(ByteBuffer bgra, int width, int height);
}
