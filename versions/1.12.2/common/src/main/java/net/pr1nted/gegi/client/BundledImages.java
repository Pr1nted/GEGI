package net.pr1nted.gegi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.pr1nted.gegi.Constants;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Pictures shipped inside the mod (Open Doctrines' card), uploaded from the jar
 * directly, so the card never depends on a loader's resource handling. Called on the
 * render thread; each image is uploaded once and kept.
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
            BufferedImage image = ImageIO.read(in);
            if (image == null) throw new IllegalStateException(resource + " is not an image");
            String name = location.substring(colon + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
            ResourceLocation id = new ResourceLocation(Constants.MOD_ID, "bundled/" + name);
            Minecraft.getMinecraft().getTextureManager().loadTexture(id, new DynamicTexture(image));
            return Optional.of(new Thumbnails.Ready(id, image.getWidth(), image.getHeight()));
        } catch (Exception e) {
            Constants.LOG.error("GEGI could not load {}", location, e);
            return Optional.empty();
        }
    }
}
