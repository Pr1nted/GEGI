package net.pr1nted.openarcade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
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
 * <p>It runs beside MC-Runtime-Test, which joins the world and quits: that mod
 * waits while a screen is open, so it finishes only after this has closed the menu.
 */
final class SelfTest {
    private SelfTest() {}

    private static final boolean ENABLED = "1".equals(System.getenv("OPENARCADE_SELFTEST"));
    private static final int STEP_TIMEOUT_TICKS = 400;
    private static final int TOTAL_TIMEOUT_TICKS = 1200;

    private enum Step { WAIT_FOR_WORLD, TYPE_COMMAND, MENU_FROM_COMMAND, OPEN_OPTIONS, CLICK_BUTTON, MENU_FROM_OPTIONS, DONE }

    private static Step step = Step.WAIT_FOR_WORLD;
    private static int worldTicks;
    private static int stepTicks;
    private static int totalTicks;

    static void tick(Minecraft minecraft) {
        if (!ENABLED || step == Step.DONE) return;
        try {
            if (step != Step.WAIT_FOR_WORLD) {
                if (++totalTicks > TOTAL_TIMEOUT_TICKS) fail("timed out at " + step);
                if (++stepTicks > STEP_TIMEOUT_TICKS) fail("stuck at " + step);
            }
            Screen screen = minecraft.gui.screen();
            switch (step) {
                case WAIT_FOR_WORLD -> {
                    if (minecraft.player != null && minecraft.level != null && screen == null && ++worldTicks >= 40) {
                        List<GameEntry> recommended = ArcadeClient.catalog().recommended();
                        if (recommended.isEmpty() || !recommended.get(0).title().equals("Open Doctrines")) {
                            fail("Open Doctrines is not the first recommendation");
                        }
                        Constants.LOG.info("[self-test] in a world; typing /arcade");
                        next(Step.TYPE_COMMAND);
                    }
                }
                case TYPE_COMMAND -> {
                    minecraft.player.connection.sendCommand("arcade");
                    next(Step.MENU_FROM_COMMAND);
                }
                case MENU_FROM_COMMAND -> {
                    if (screen instanceof ArcadeScreen arcade && arcade.framesDrawn() >= 5) {
                        Constants.LOG.info("[self-test] /arcade opened the menu; opening Options");
                        next(Step.OPEN_OPTIONS);
                    }
                }
                case OPEN_OPTIONS -> {
                    minecraft.gui.setScreen(new OptionsScreen(null, minecraft.options, true));
                    next(Step.CLICK_BUTTON);
                }
                case CLICK_BUTTON -> {
                    if (screen instanceof OptionsScreen options && options instanceof OptionsButtonHolder holder) {
                        Button button = holder.openarcade$optionsButton();
                        if (button == null || !options.children().contains(button)) fail("no Open Arcade button in Options");
                        MouseButtonEvent click = new MouseButtonEvent(button.getX() + 2, button.getY() + 2, new MouseButtonInfo(0, 0));
                        if (!button.mouseClicked(click, false)) fail("the Open Arcade button did not take a click");
                        next(Step.MENU_FROM_OPTIONS);
                    }
                }
                case MENU_FROM_OPTIONS -> {
                    if (screen instanceof ArcadeScreen arcade && arcade.framesDrawn() >= 5) {
                        if (!(arcade.parent() instanceof OptionsScreen)) fail("the menu did not remember Options as its parent");
                        minecraft.gui.setScreen(null);
                        Constants.LOG.info("OPEN ARCADE SELF-TEST PASSED");
                        step = Step.DONE;
                    }
                }
                case DONE -> { }
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
