package net.pr1nted.gegi.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.pr1nted.gegi.Constants;
import net.pr1nted.gegi.catalog.Http;
import net.pr1nted.gegi.catalog.Images;
import net.pr1nted.gegi.catalog.Links;

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

    record Ready(ResourceLocation id, int width, int height) {}

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
            Minecraft.getInstance().execute(() -> upload(uri, png));
        });
        return Optional.empty();
    }

    private void upload(URI uri, byte[] png) {
        if (released) return;
        try {
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(png));
            ResourceLocation id = new ResourceLocation(Constants.MOD_ID, "thumbnail/" + NEXT_ID.incrementAndGet());
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
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
            ready.ifPresent(r -> Minecraft.getInstance().getTextureManager().release(r.id()));
        }
        textures.clear();
    }
}
