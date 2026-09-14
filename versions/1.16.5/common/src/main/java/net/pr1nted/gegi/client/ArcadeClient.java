package net.pr1nted.gegi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.Util;
import net.pr1nted.gegi.Constants;
import net.pr1nted.gegi.Log;
import net.pr1nted.gegi.catalog.Catalog;
import net.pr1nted.gegi.catalog.GameEntry;
import net.pr1nted.gegi.catalog.Links;
import net.pr1nted.gegi.runtime.BrowserRuntime;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * The client side of GEGI, in the vanilla codebase only, so every loader
 * shares it. The loader modules just call {@link #screen(Screen)} for their own
 * "config" buttons; the Options button, the command and the tick hook are mixins.
 */
public final class ArcadeClient {
    private ArcadeClient() {}

    static {
        // The core logs through this game's logger.
        Log.use(new Log.Sink() {
            @Override
            public void info(String message) {
                Constants.LOG.info(message);
            }

            @Override
            public void warn(String message, Throwable error) {
                Constants.LOG.warn(message, error);
            }

            @Override
            public void error(String message, Throwable error) {
                Constants.LOG.error(message, error);
            }
        });
    }

    /** Typed in chat as /arcade or /gegi. Handled on the client; the server never sees it. */
    public static final Set<String> COMMANDS = java.util.Collections.unmodifiableSet(new java.util.HashSet<>(java.util.Arrays.asList("arcade", "gegi")));

    private static Catalog catalog;
    private static boolean openRequested;

    public static Catalog catalog() {
        if (catalog == null) catalog = Catalog.bundled();
        return catalog;
    }

    public static Screen screen(Screen parent) {
        return new ArcadeScreen(parent, catalog());
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(screen(parent));
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
        String name = command.trim();
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

    /**
     * Plays a game inside Minecraft. The first time, before Chromium has been
     * downloaded, the player is asked; declining opens the game in their own browser.
     */
    public static void play(Screen parent, GameEntry game) {
        if (!Links.isAllowed(game.url())) {
            Constants.LOG.warn("Refused to open {}: not an itch.io or Newgrounds link", game.url());
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (BrowserRuntime.chromiumInstalled()) {
            minecraft.setScreen(new GameScreen(parent, game));
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                minecraft.setScreen(new GameScreen(parent, game));
            } else {
                openLink(game.url());
                minecraft.setScreen(parent);
            }
        }, Lang.text("gegi.consent.title"),
                Lang.text("gegi.consent.message", BrowserRuntime.dataDir()),
                Lang.text("gegi.consent.download"),
                Lang.text("gegi.consent.browser")));
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
