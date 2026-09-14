package net.pr1nted.openarcade.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.pr1nted.openarcade.Constants;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Pictures shipped inside the mod (Open Doctrines' card), uploaded from the jar
 * directly, for the same reason as {@link Lang}: without Fabric API, Fabric and
 * Quilt never load the mod's textures, and the card drew as a magenta checkerboard.
 * Called on the render thread; each image is uploaded once and kept.
 */
final class BundledImages {
    private BundledImages() {}

    private static final Map<String, Optional<Thumbnails.Ready>> IMAGES = new HashMap<>();

    /** {@code location} is "namespace:path", read from /assets/namespace/path in the jar. */
    static Optional<Thumbnails.Ready> get(String location) {
        return IMAGES.computeIfAbsent(location, BundledImages::upload);
    }

    static boolean isReady(String location) {
        return IMAGES.getOrDefault(location, Optional.empty()).isPresent();
    }

    private static Optional<Thumbnails.Ready> upload(String location) {
        int colon = location.indexOf(':');
        String resource = "/assets/" + location.substring(0, colon) + "/" + location.substring(colon + 1);
        try (InputStream in = BundledImages.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException(resource + " is missing from the jar");
            NativeImage image = NativeImage.read(in);
            String name = location.substring(colon + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
            ResourceLocation id = new ResourceLocation(Constants.MOD_ID, "bundled/" + name);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            return Optional.of(new Thumbnails.Ready(id, image.getWidth(), image.getHeight()));
        } catch (Exception e) {
            Constants.LOG.error("Open Arcade could not load {}", location, e);
            return Optional.empty();
        }
    }
}
