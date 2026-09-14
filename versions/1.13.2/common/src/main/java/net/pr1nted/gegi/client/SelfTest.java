package net.pr1nted.gegi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.pr1nted.gegi.Constants;
import net.pr1nted.gegi.catalog.GameEntry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * What CI runs inside the real game when GEGI_SELFTEST=1.
 *
 * <p>Once the player is in a world it does what a player would: types /arcade,
 * waits for the menu to draw, opens Options, clicks the GEGI button, waits
 * for the menu again, and closes it. Any step that does not happen, or throws,
 * ends the game with a non-zero exit code, which fails the CI job.
 *
 * <p>MC-Runtime-Test has no build for 1.13.2, so nothing joins a world for the test:
 * with GEGI_SELFTEST_CREATE_WORLD=1 it creates a flat creative world from the
 * title screen, and quits the game once it has passed. A pass writes {@link #MARKER}
 * to the game directory, and CI requires the file.
 */
final class SelfTest {
    private SelfTest() {}

    /** GEGI_SELFTEST=1 in the environment, or -Dgegi.selftest=true. */
    static final boolean ENABLED = "1".equals(System.getenv("GEGI_SELFTEST"))
            || Boolean.getBoolean("gegi.selftest");
    static final boolean CREATE_WORLD = "1".equals(System.getenv("GEGI_SELFTEST_CREATE_WORLD"));
    private static final int STEP_TIMEOUT_TICKS = 400;
    private static final int TOTAL_TIMEOUT_TICKS = 1200;
    private static final int WORLD_TIMEOUT_TICKS = 2400;
    static final String MARKER = "gegi-selftest-passed";

    private enum Step { WAIT_FOR_WORLD, TYPE_COMMAND, MENU_FROM_COMMAND, OPEN_OPTIONS, CLICK_BUTTON, MENU_FROM_OPTIONS, DONE }

    private static Step step = Step.WAIT_FOR_WORLD;
    private static int worldTicks;
    private static int stepTicks;
    private static int totalTicks;
    private static boolean announced;
    private static int ticksWithPlayer;
    private static boolean worldRequested;

    private static void createWorldIfAsked(Minecraft minecraft) {
        if (!CREATE_WORLD || worldRequested || minecraft.world != null) return;
        if (!(minecraft.currentScreen instanceof GuiMainMenu)) return;
        worldRequested = true;
        String id = "gegi-selftest";
        if (minecraft.getSaveLoader().canLoadWorld(id)) minecraft.getSaveLoader().deleteWorldDirectory(id);
        Constants.LOG.info("[self-test] creating a world");
        minecraft.launchIntegratedServer(id, "GEGI self-test",
                new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.FLAT));
    }

    static void tick(Minecraft minecraft) {
        if (!ENABLED || step == Step.DONE) return;
        createWorldIfAsked(minecraft);
        if (!announced) {
            announced = true;
            Constants.LOG.info("[self-test] enabled; waiting for a world");
        }
        try {
            if (minecraft.player != null && step == Step.WAIT_FOR_WORLD && ++ticksWithPlayer > WORLD_TIMEOUT_TICKS) {
                fail("the world never finished loading");
            }
            if (step != Step.WAIT_FOR_WORLD) {
                if (++totalTicks > TOTAL_TIMEOUT_TICKS) fail("timed out at " + step);
                if (++stepTicks > STEP_TIMEOUT_TICKS) fail("stuck at " + step);
            }
            GuiScreen screen = minecraft.currentScreen;
            switch (step) {
                case WAIT_FOR_WORLD:
                    if (minecraft.player != null && minecraft.world != null && screen == null && ++worldTicks >= 40) {
                        List<GameEntry> recommended = ArcadeClient.catalog().recommended();
                        if (recommended.isEmpty() || !recommended.get(0).title().equals("Open Doctrines")) {
                            fail("Open Doctrines is not the first recommendation");
                        }
                        if (!Lang.string("gegi.title").equals("Good Enough Game Integration")) {
                            fail("the mod's strings did not load: " + Lang.string("gegi.title"));
                        }
                        Constants.LOG.info("[self-test] in a world; typing /gegi");
                        next(Step.TYPE_COMMAND);
                    }
                    break;
                case TYPE_COMMAND:
                    // Through a chat screen, as a player types it: that is where Forge fires ClientChatEvent.
                    net.minecraft.client.gui.GuiChat chat = new net.minecraft.client.gui.GuiChat();
                    chat.setWorldAndResolution(minecraft, minecraft.mainWindow.getScaledWidth(), minecraft.mainWindow.getScaledHeight());
                    chat.sendChatMessage("/gegi", false);
                    next(Step.MENU_FROM_COMMAND);
                    break;
                case MENU_FROM_COMMAND:
                    // The Open Doctrines card must have its picture: without it, the bundled texture failed to load.
                    if (screen instanceof ArcadeScreen && ((ArcadeScreen) screen).framesDrawn() >= 5
                            && BundledImages.isReady(ArcadeClient.catalog().recommended().get(0).bundledImage().orElse(""))) {
                        Constants.LOG.info("[self-test] /gegi opened the menu with Open Doctrines' card; opening Options");
                        next(Step.OPEN_OPTIONS);
                    }
                    break;
                case OPEN_OPTIONS:
                    minecraft.displayGuiScreen(new GuiOptions(null, minecraft.gameSettings));
                    next(Step.CLICK_BUTTON);
                    break;
                case CLICK_BUTTON:
                    if (screen instanceof GuiOptions && ArcadeClient.lastOptionsButton() != null) {
                        GuiButton button = ArcadeClient.lastOptionsButton();
                        if (!button.mouseClicked(button.x + 2, button.y + 2, 0)) fail("the GEGI button did not take a click");
                        next(Step.MENU_FROM_OPTIONS);
                    }
                    break;
                case MENU_FROM_OPTIONS:
                    if (screen instanceof ArcadeScreen && ((ArcadeScreen) screen).framesDrawn() >= 5) {
                        if (!(((ArcadeScreen) screen).parent() instanceof GuiOptions)) fail("the menu did not remember Options as its parent");
                        minecraft.displayGuiScreen(null);
                        Files.write(minecraft.gameDir.toPath().resolve(MARKER), "passed\n".getBytes(StandardCharsets.UTF_8));
                        Constants.LOG.info("GEGI SELF-TEST PASSED");
                        step = Step.DONE;
                        if (CREATE_WORLD) minecraft.shutdown();
                    }
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            Constants.LOG.error("GEGI SELF-TEST FAILED at {}", step, t);
            System.exit(3);
        }
    }

    private static void next(Step nextStep) {
        step = nextStep;
        stepTicks = 0;
    }

    private static void fail(String why) {
        throw new IllegalStateException(why);
    }
}
