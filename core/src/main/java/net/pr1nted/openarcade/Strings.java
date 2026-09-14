package net.pr1nted.openarcade;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Open Arcade's strings, read straight from its own jar.
 *
 * <p>Not the game's translation system: Fabric and Quilt load a mod's assets only
 * through Fabric API's resource loader, which this mod deliberately does not depend
 * on, so a translatable key showed up raw ("openarcade.title") there. Reading the
 * lang file from the classpath works the same on every loader and every version.
 */
public final class Strings {
    private Strings() {}

    public static final String RESOURCE = "/assets/openarcade/lang/en_us.json";

    private static final Map<String, String> STRINGS = load();

    @SuppressWarnings("deprecation") // JsonParser.parseReader is Gson 2.8.6+; 1.12.2 ships 2.8.0
    private static Map<String, String> load() {
        Map<String, String> strings = new HashMap<>();
        try (InputStream in = Strings.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " is missing from the jar");
            for (Map.Entry<String, JsonElement> e : new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().entrySet()) {
                strings.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            Log.error("Open Arcade could not read its strings", e);
        }
        return strings;
    }

    public static boolean loaded() {
        return !STRINGS.isEmpty();
    }

    public static String string(String key, Object... args) {
        String value = STRINGS.get(key);
        if (value == null) return key;
        return args.length == 0 ? value : String.format(Locale.ROOT, value, args);
    }
}
