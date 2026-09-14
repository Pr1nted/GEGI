package net.pr1nted.openarcade.client;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
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

    public static ITextComponent text(String key, Object... args) {
        return new TextComponentString(Strings.string(key, args));
    }
}
