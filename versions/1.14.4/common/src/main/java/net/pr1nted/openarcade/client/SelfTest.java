package net.pr1nted.openarcade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.pr1nted.openarcade.Constants;
import net.pr1nted.openarcade.catalog.GameEntry;

import java.util.List;

/**
 * What CI runs inside the real game, on every loader, when OPENARCADE_SELFTEST=1.
 *
 * <p>Once the player is in a world it does what a player would: types /arcade,
 * waits for the menu to draw, opens Options, clicks the Open Arcade button, waits
 * for the menu again, and closes it. Any step that does not happen, or throws,
 * ends the game with a non-zero exit code, which fails the CI job.
 *
 * <p>It runs beside MC-Runtime-Test, which joins a world and quits as soon as the
 * loading screen closes. McRuntimeTestMixin pauses that mod while this test runs.
 * A pass writes {@link #MARKER} to the game directory, and CI requires the file,
 * so a job cannot pass without the test having run.
 */
final class SelfTest {
    private SelfTest() {}

    /** OPENARCADE_SELFTEST=1 in the environment, or -Dopenarcade.selftest=true. */
    static final boolean ENABLED = "1".equals(System.getenv("OPENARCADE_SELFTEST"))
            || Boolean.getBoolean("openarcade.selftest");
    private static final int STEP_TIMEOUT_TICKS = 400;
    private static final int TOTAL_TIMEOUT_TICKS = 1200;
    private static final int WORLD_TIMEOUT_TICKS = 2400;
    static final String MARKER = "openarcade-selftest-passed";

    private enum Step { WAIT_FOR_WORLD, TYPE_COMMAND, MENU_FROM_COMMAND, OPEN_OPTIONS, CLICK_BUTTON, MENU_FROM_OPTIONS, DONE }

    private static Step step = Step.WAIT_FOR_WORLD;
    private static int worldTicks;
    private static int stepTicks;
    private static int totalTicks;
    private static boolean announced;
    private static int ticksWithPlayer;

    /**
     * While this is true the CI harness waits (see McRuntimeTestMixin). From the moment
     * a player exists, through the loading screen, until the test is done.
     */
    static boolean isRunningInWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        return ENABLED && step != Step.DONE && minecraft != null && minecraft.player != null;
    }


    /**
     * Where MC-Runtime-Test has no build (1.13 to 1.15), nothing joins a world for the
     * test: with OPENARCADE_SELFTEST_CREATE_WORLD=1 it creates a flat creative world from
     * the title screen, and quits the game once it has passed.
     */
    static final boolean CREATE_WORLD = "1".equals(System.getenv("OPENARCADE_SELFTEST_CREATE_WORLD"));
    private static boolean worldRequested;

    private static void createWorldIfAsked(Minecraft minecraft) {
        if (!CREATE_WORLD || worldRequested || minecraft.level != null) return;
        if (!(minecraft.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
        worldRequested = true;
        String id = "openarcade-selftest";
        if (minecraft.getLevelSource().levelExists(id)) minecraft.getLevelSource().deleteLevel(id);
        Constants.LOG.info("[self-test] creating a world");
        minecraft.selectLevel(id, "Open Arcade self-test", new net.minecraft.world.level.LevelSettings(
                0L, net.minecraft.world.level.GameType.CREATIVE, false, false, net.minecraft.world.level.LevelType.FLAT));
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
            Screen screen = minecraft.screen;
            switch (step) {
                case WAIT_FOR_WORLD: {
                    if (minecraft.player != null && minecraft.level != null && screen == null && ++worldTicks >= 40) {
                        List<GameEntry> recommended = ArcadeClient.catalog().recommended();
                        if (recommended.isEmpty() || !recommended.get(0).title().equals("Open Doctrines")) {
                            fail("Open Doctrines is not the first recommendation");
                        }
                        if (!Lang.string("openarcade.title").equals("Open Arcade")) {
                            fail("the mod's strings did not load: " + Lang.string("openarcade.title"));
                        }
                        Constants.LOG.info("[self-test] in a world; typing /arcade");
                        next(Step.TYPE_COMMAND);
                    }
                }
                break;
                case TYPE_COMMAND: {
                    // Through a chat screen, as a player types it: that is where Forge fires ClientChatEvent,
                    // and it reaches LocalPlayer.chat, where Fabric's mixin listens.
                    net.minecraft.client.gui.screens.ChatScreen chat = new net.minecraft.client.gui.screens.ChatScreen("");
                    chat.init(minecraft, minecraft.window.getGuiScaledWidth(), minecraft.window.getGuiScaledHeight());
                    chat.sendMessage("/arcade", false);
                    next(Step.MENU_FROM_COMMAND);
                }
                break;
                case MENU_FROM_COMMAND: {
                    // The Open Doctrines card must have its picture: without it, the bundled
                    // texture failed to load, which is what a raw magenta square looked like.
                    if (screen instanceof ArcadeScreen && ((ArcadeScreen) screen).framesDrawn() >= 5
                            && BundledImages.isReady(ArcadeClient.catalog().recommended().get(0).bundledImage().orElse(""))) {
                        ArcadeScreen arcade = (ArcadeScreen) screen;
                        Constants.LOG.info("[self-test] /arcade opened the menu with Open Doctrines' card; opening Options");
                        next(Step.OPEN_OPTIONS);
                    }
                }
                break;
                case OPEN_OPTIONS: {
                    minecraft.setScreen(new OptionsScreen(null, minecraft.options));
                    next(Step.CLICK_BUTTON);
                }
                break;
                case CLICK_BUTTON: {
                    if (screen instanceof OptionsScreen && ArcadeClient.lastOptionsButton() != null) {
                        OptionsScreen options = (OptionsScreen) screen;
                        Button button = ArcadeClient.lastOptionsButton();
                        if (!options.children().contains(button)) fail("no Open Arcade button in Options");
                        if (!button.mouseClicked(button.x + 2, button.y + 2, 0)) fail("the Open Arcade button did not take a click");
                        next(Step.MENU_FROM_OPTIONS);
                    }
                }
                break;
                case MENU_FROM_OPTIONS: {
                    if (screen instanceof ArcadeScreen && ((ArcadeScreen) screen).framesDrawn() >= 5) {
                        ArcadeScreen arcade = (ArcadeScreen) screen;
                        if (!(arcade.parent() instanceof OptionsScreen)) fail("the menu did not remember Options as its parent");
                        minecraft.setScreen(null);
                        java.nio.file.Files.write(minecraft.gameDirectory.toPath().resolve(MARKER), "passed\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        Constants.LOG.info("OPEN ARCADE SELF-TEST PASSED");
                        step = Step.DONE;
                        if (CREATE_WORLD) minecraft.stop();
                    }
                }
                break;
                case DONE:
                    break;
            }
        } catch (Throwable t) {
            Constants.LOG.error("OPEN ARCADE SELF-TEST FAILED at {}", step, t);
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
