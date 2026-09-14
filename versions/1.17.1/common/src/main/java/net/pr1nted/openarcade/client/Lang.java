package net.pr1nted.openarcade.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.MutableComponent;
import net.pr1nted.openarcade.Strings;

/** The core's {@link Strings}, as this version's chat components. */
public final class Lang {
    private Lang() {}

    public static boolean loaded() {
        return Strings.loaded();
    }

    public static String string(String key, Object... args) {
        return Strings.string(key, args);
    }

    public static MutableComponent text(String key, Object... args) {
        return new TextComponent(Strings.string(key, args));
    }
}
