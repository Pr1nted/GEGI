package net.pr1nted.openarcade.catalog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Thumbnail bytes as PNG: Minecraft's NativeImage reads nothing else, and itch.io serves JPEG too. */
public final class Images {
    private Images() {}

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};

    public static boolean isPng(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == PNG_MAGIC[0] && bytes[1] == PNG_MAGIC[1]
                && bytes[2] == PNG_MAGIC[2] && bytes[3] == PNG_MAGIC[3];
    }

    /** The bytes unchanged if they are a PNG, re-encoded as one if ImageIO can read them. */
    public static byte[] toPng(byte[] bytes) {
        if (isPng(bytes)) return bytes;
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null) throw new IOException("not an image ImageIO can read");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(decoded, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
