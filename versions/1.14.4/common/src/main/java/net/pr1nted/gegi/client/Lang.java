package net.pr1nted.gegi.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.pr1nted.gegi.Strings;

/** The core's {@link Strings}, as this version's chat components. */
public final class Lang {
    private Lang() {}

    public static boolean loaded() {
        return Strings.loaded();
    }

    public static String string(String key, Object... args) {
        return Strings.string(key, args);
    }

    public static Component text(String key, Object... args) {
        return new TextComponent(Strings.string(key, args));
    }
}
