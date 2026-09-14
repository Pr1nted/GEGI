package net.pr1nted.openarcade.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.pr1nted.openarcade.Constants;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Open Arcade's strings, read straight from its own jar.
 *
 * <p>Not Component.translatable: Fabric and Quilt load a mod's assets only through
 * Fabric API's resource loader, which this mod deliberately does not depend on, so a
 * translatable key showed up raw ("openarcade.title") on those loaders. Reading the
 * lang file from the classpath works the same on every loader.
 */
public final class Lang {
    private Lang() {}

    private static final Map<String, String> STRINGS = load();

    private static Map<String, String> load() {
        Map<String, String> strings = new HashMap<>();
        try (InputStream in = Lang.class.getResourceAsStream("/assets/openarcade/lang/en_us.json")) {
            if (in == null) throw new IllegalStateException("en_us.json is missing from the jar");
            for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().entrySet()) {
                strings.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            Constants.LOG.error("Open Arcade could not read its strings", e);
        }
        return strings;
    }

    public static String string(String key, Object... args) {
        String value = STRINGS.get(key);
        if (value == null) return key;
        return args.length == 0 ? value : String.format(Locale.ROOT, value, args);
    }

    public static MutableComponent text(String key, Object... args) {
        return Component.literal(string(key, args));
    }
}
