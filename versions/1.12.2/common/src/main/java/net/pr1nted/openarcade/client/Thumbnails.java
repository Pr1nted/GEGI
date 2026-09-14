package net.pr1nted.openarcade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.pr1nted.openarcade.Constants;
import net.pr1nted.openarcade.catalog.Http;
import net.pr1nted.openarcade.catalog.Images;
import net.pr1nted.openarcade.catalog.Links;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Game thumbnails from itch.io's image CDN, as textures.
 *
 * <p>Downloaded and converted to PNG off the render thread (see {@link Images});
 * only the upload happens on it. Every texture made here is released when the menu
 * closes.
 */
final class Thumbnails {

    static final class Ready {
        private final ResourceLocation id;
        private final int width;
        private final int height;

        Ready(ResourceLocation id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }

        ResourceLocation id() {
            return id;
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }
    }

    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    /** Present once a download has started; the value is empty until the texture exists. */
    private final Map<URI, Optional<Ready>> textures = new ConcurrentHashMap<>();
    private volatile boolean released;

    Optional<Ready> get(URI uri) {
        if (!Links.isAllowedImage(uri)) return Optional.empty();
        Optional<Ready> known = textures.get(uri);
        if (known != null) return known;
        textures.put(uri, Optional.empty());
        Http.get(uri).thenApply(Images::toPng).whenComplete((png, error) -> {
            if (error != null) {
                Constants.LOG.debug("No thumbnail from {}: {}", uri, error.toString());
                return;
            }
            Minecraft.getMinecraft().addScheduledTask(() -> upload(uri, png));
        });
        return Optional.empty();
    }

    private void upload(URI uri, byte[] png) {
        if (released) return;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) throw new IOException("not an image");
            ResourceLocation id = new ResourceLocation(Constants.MOD_ID, "thumbnail/" + NEXT_ID.incrementAndGet());
            Minecraft.getMinecraft().getTextureManager().loadTexture(id, new DynamicTexture(image));
            textures.put(uri, Optional.of(new Ready(id, image.getWidth(), image.getHeight())));
        } catch (IOException | RuntimeException e) {
            Constants.LOG.debug("Unreadable thumbnail from {}: {}", uri, e.toString());
        }
    }

    int readyCount() {
        return (int) textures.values().stream().filter(Optional::isPresent).count();
    }

    void releaseAll() {
        released = true;
        for (Optional<Ready> ready : textures.values()) {
            ready.ifPresent(r -> Minecraft.getMinecraft().getTextureManager().deleteTexture(r.id()));
        }
        textures.clear();
    }
}
