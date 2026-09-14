package net.pr1nted.openarcade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;
import net.pr1nted.openarcade.Constants;
import net.pr1nted.openarcade.catalog.Catalog;
import net.pr1nted.openarcade.catalog.Links;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * The client side of Open Arcade, in the vanilla codebase only, so every loader
 * shares it. The loader modules just call {@link #screen(Screen)} for their own
 * "config" buttons; the Options button, the command and the tick hook are mixins.
 */
public final class ArcadeClient {
    private ArcadeClient() {}

    /** Typed in chat as /arcade or /openarcade. Handled on the client; the server never sees it. */
    public static final Set<String> COMMANDS = Set.of("arcade", "openarcade");

    private static @Nullable Catalog catalog;
    private static boolean openRequested;

    public static Catalog catalog() {
        if (catalog == null) catalog = Catalog.bundled();
        return catalog;
    }

    public static Screen screen(@Nullable Screen parent) {
        return new ArcadeScreen(parent, catalog());
    }

    public static void open(@Nullable Screen parent) {
        Minecraft.getInstance().gui.setScreen(screen(parent));
    }

    /**
     * Called for every command the player sends. Returns true when it was ours, and
     * then nothing is sent to the server.
     *
     * <p>The menu opens on the next tick rather than here: this runs from inside the
     * chat screen's own handling, which closes the chat screen right after, and would
     * close a menu opened now along with it.
     */
    public static boolean handleCommand(String command) {
        String name = command.strip();
        int space = name.indexOf(' ');
        if (space >= 0) name = name.substring(0, space);
        if (!COMMANDS.contains(name.toLowerCase(Locale.ROOT))) return false;
        openRequested = true;
        return true;
    }

    public static void tick(Minecraft minecraft) {
        if (openRequested) {
            openRequested = false;
            open(null);
        }
        SelfTest.tick(minecraft);
        DevScreenshot.tick(minecraft);
    }

    /** Opens a game in the player's browser. Anything outside itch.io and Newgrounds is refused. */
    public static boolean openLink(URI uri) {
        if (!Links.isAllowed(uri)) {
            Constants.LOG.warn("Refused to open {}: not an itch.io or Newgrounds link", uri);
            return false;
        }
        Util.getPlatform().openUri(uri);
        return true;
    }
}
