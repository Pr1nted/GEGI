package net.pr1nted.openarcade.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.pr1nted.openarcade.Constants;
import net.pr1nted.openarcade.catalog.Http;
import net.pr1nted.openarcade.catalog.Links;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Game thumbnails from itch.io's image CDN, as textures.
 *
 * <p>Downloaded and decoded off the render thread; only the upload happens on it.
 * NativeImage reads PNG and nothing else, and itch.io serves JPEG as often as PNG,
 * so anything that is not a PNG is re-encoded as one first. Every texture made
 * here is released when the menu closes.
 */
final class Thumbnails {

    record Ready(Identifier id, int width, int height) {}

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    /** Present once a download has started; the value is empty until the texture exists. */
    private final Map<URI, Optional<Ready>> textures = new ConcurrentHashMap<>();
    private volatile boolean released;

    Optional<Ready> get(URI uri) {
        if (!Links.isAllowedImage(uri)) return Optional.empty();
        Optional<Ready> known = textures.get(uri);
        if (known != null) return known;
        textures.put(uri, Optional.empty());
        Http.get(uri).thenApply(Thumbnails::toPng).whenComplete((png, error) -> {
            if (error != null) {
                Constants.LOG.debug("No thumbnail from {}: {}", uri, error.toString());
                return;
            }
            Minecraft.getInstance().execute(() -> upload(uri, png));
        });
        return Optional.empty();
    }

    private void upload(URI uri, byte[] png) {
        if (released) return;
        try {
            NativeImage image = NativeImage.read(png);
            Identifier id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "thumbnail/" + NEXT_ID.incrementAndGet());
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Open Arcade thumbnail " + uri, image));
            textures.put(uri, Optional.of(new Ready(id, image.getWidth(), image.getHeight())));
        } catch (IOException | RuntimeException e) {
            Constants.LOG.debug("Unreadable thumbnail from {}: {}", uri, e.toString());
        }
    }

    void releaseAll() {
        released = true;
        for (Optional<Ready> ready : textures.values()) {
            ready.ifPresent(r -> Minecraft.getInstance().getTextureManager().release(r.id()));
        }
        textures.clear();
    }

    private static byte[] toPng(byte[] bytes) {
        if (bytes.length >= 4 && bytes[0] == PNG_MAGIC[0] && bytes[1] == PNG_MAGIC[1]
                && bytes[2] == PNG_MAGIC[2] && bytes[3] == PNG_MAGIC[3]) {
            return bytes;
        }
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null) throw new IOException("not an image ImageIO can read");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(decoded, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
