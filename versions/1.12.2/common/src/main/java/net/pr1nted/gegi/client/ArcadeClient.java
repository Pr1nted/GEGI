package net.pr1nted.gegi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.pr1nted.gegi.Constants;
import net.pr1nted.gegi.Log;
import net.pr1nted.gegi.catalog.Catalog;
import net.pr1nted.gegi.catalog.GameEntry;
import net.pr1nted.gegi.catalog.Links;
import net.pr1nted.gegi.runtime.BrowserRuntime;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The client side of GEGI on Minecraft 1.12.2 (Forge). The Forge module calls
 * {@link #screen(GuiScreen)} for its "config" button, and adds the Options button,
 * the command and the tick hook through Forge's events (Forge 1.12.2 ships no Mixin).
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
    public static final Set<String> COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("arcade", "gegi")));

    private static Catalog catalog;
    private static GuiButton optionsButton;
    private static boolean openRequested;

    public static Catalog catalog() {
        if (catalog == null) catalog = Catalog.bundled();
        return catalog;
    }

    public static GuiScreen screen(GuiScreen parent) {
        return new ArcadeScreen(parent, catalog());
    }

    public static void open(GuiScreen parent) {
        Minecraft.getMinecraft().displayGuiScreen(screen(parent));
    }

    /** The GEGI button for an Options screen, added by the Forge event and remembered for the self-test. */
    public static GuiButton optionsButton(GuiScreen options, int x, int y) {
        optionsButton = Buttons.of(x, y, 100, 20, Lang.string("gegi.button"), () -> open(options));
        return optionsButton;
    }

    /** The button optionsButton made last, or null. */
    public static GuiButton lastOptionsButton() {
        return optionsButton;
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
    public static void play(GuiScreen parent, GameEntry game) {
        if (!Links.isAllowed(game.url())) {
            Constants.LOG.warn("Refused to open {}: not an itch.io or Newgrounds link", game.url());
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (BrowserRuntime.chromiumInstalled()) {
            minecraft.displayGuiScreen(new GameScreen(parent, game));
            return;
        }
        minecraft.displayGuiScreen(new GuiYesNo((yes, id) -> {
            if (yes) {
                minecraft.displayGuiScreen(new GameScreen(parent, game));
            } else {
                openLink(game.url());
                minecraft.displayGuiScreen(parent);
            }
        }, Lang.string("gegi.consent.title"),
                Lang.string("gegi.consent.message", BrowserRuntime.dataDir()),
                Lang.string("gegi.consent.download"),
                Lang.string("gegi.consent.browser"), 0));
    }

    /** Opens a game in the player's browser. Anything outside itch.io and Newgrounds is refused. */
    public static boolean openLink(URI uri) {
        if (!Links.isAllowed(uri)) {
            Constants.LOG.warn("Refused to open {}: not an itch.io or Newgrounds link", uri);
            return false;
        }
        // 1.12.2 has no public way to open a link; this is what its own chat links do.
        try {
            Class<?> desktop = Class.forName("java.awt.Desktop");
            Object instance = desktop.getMethod("getDesktop").invoke(null);
            desktop.getMethod("browse", URI.class).invoke(instance, uri);
            return true;
        } catch (Throwable desktopFailed) {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String[] command = os.contains("win") ? new String[]{"rundll32", "url.dll,FileProtocolHandler", uri.toString()}
                    : os.contains("mac") ? new String[]{"open", uri.toString()}
                    : new String[]{"xdg-open", uri.toString()};
            try {
                Runtime.getRuntime().exec(command);
                return true;
            } catch (Exception e) {
                Constants.LOG.error("Could not open {}", uri, e);
                return false;
            }
        }
    }
}
