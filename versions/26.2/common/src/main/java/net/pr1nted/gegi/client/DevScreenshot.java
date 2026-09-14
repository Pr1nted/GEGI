package net.pr1nted.gegi.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.pr1nted.gegi.Constants;

import java.io.IOException;
import java.nio.file.Path;

/**
 * For store pages and the README: GEGI_SCREENSHOT=/path/to/file.png opens the
 * arcade over the title screen, waits for itch.io's thumbnails, saves what the game
 * drew, and quits. Does nothing without the variable.
 *
 * <p>With GEGI_SCREENSHOT_PLAY=1 as well, it plays the first recommendation
 * (Open Doctrines) inside Minecraft instead: one shot once the page has drawn, then a
 * click (GEGI_SCREENSHOT_CLICK=x,y as fractions of the view; itch.io's "Run game"
 * by default) through the game screen's own input path, and a second shot 45 seconds
 * later, next to the first with "-clicked" in its name.
 *
 * <p>GEGI_PLAY=1 on its own does the same without screenshots and never quits: the game
 * opens over the title screen, "Run game" is clicked once, and the player takes over.
 */
final class DevScreenshot {
    private DevScreenshot() {}

    private static final String TARGET = System.getenv("GEGI_SCREENSHOT");
    private static final boolean PLAY = "1".equals(System.getenv("GEGI_SCREENSHOT_PLAY"));
    /** Play the first recommendation for a person, with no screenshots and no quitting. */
    private static final boolean PLAY_ONLY = "1".equals(System.getenv("GEGI_PLAY"));
    /** Where to click, as fractions of the game view: itch.io's "Run game" by default. */
    private static final double[] CLICK = parseClick(System.getenv("GEGI_SCREENSHOT_CLICK"));
    private static final int MIN_THUMBNAILS = 6;
    private static final int MAX_WAIT_TICKS = 400;
    private static final int MIN_GAME_FRAMES = 240;
    private static final int MAX_GAME_WAIT_TICKS = 3000;

    private static boolean opened;
    private static boolean taken;
    private static int ticks;
    private static int shotTick = -1;

    static void tick(Minecraft minecraft) {
        if (taken) return;
        boolean shots = TARGET != null && !TARGET.isBlank();
        if (!shots && !PLAY_ONLY) return;
        Screen screen = minecraft.gui.screen();
        if (!opened) {
            if (screen instanceof TitleScreen && minecraft.gui.overlay() == null) {
                ArcadeClient.open(screen);
                if (PLAY || PLAY_ONLY) minecraft.gui.setScreen(new GameScreen(minecraft.gui.screen(), ArcadeClient.catalog().recommended().get(0)));
                if (PLAY_ONLY) Constants.LOG.info("[play] opened {}", ArcadeClient.catalog().recommended().get(0).title());
                opened = true;
            }
            return;
        }
        if (PLAY || PLAY_ONLY) {
            if (screen instanceof GameScreen game) playTick(minecraft, game, shots);
            return;
        }
        if (!(screen instanceof ArcadeScreen arcade)) return;
        ticks++;
        if (ticks < 60 || (arcade.thumbnailsReady() < MIN_THUMBNAILS && ticks < MAX_WAIT_TICKS)) return;
        taken = true;
        shoot(minecraft, TARGET, "(" + arcade.thumbnailsReady() + " thumbnails loaded)", true);
    }

    private static void playTick(Minecraft minecraft, GameScreen game, boolean shots) {
        ticks++;
        if (shotTick < 0) {
            // Chromium starting, then Open Doctrines' web build loading: wait for it to draw for a while.
            if (game.framesShown() < MIN_GAME_FRAMES && ticks < MAX_GAME_WAIT_TICKS) return;
            shotTick = ticks;
            if (shots) shoot(minecraft, TARGET, "(" + game.framesShown() + " frames from Chromium)", false);
            return;
        }
        double x = game.width * CLICK[0];
        double y = GameScreen.BAR + (game.height - GameScreen.BAR) * CLICK[1];
        MouseButtonEvent click = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
        if (ticks == shotTick + 20) {
            game.mouseMoved(x, y);
        } else if (ticks == shotTick + 24) {
            game.mouseClicked(click, false);
        } else if (ticks == shotTick + 28) {
            game.mouseReleased(click);
            if (!shots) {
                taken = true;
                Constants.LOG.info("[play] clicked Run game; the game is yours");
            }
        } else if (ticks == shotTick + 900) {
            taken = true;
            shoot(minecraft, TARGET.replaceFirst("(\\.png)?$", "-clicked.png"), "(" + game.framesShown() + " frames from Chromium)", true);
        }
    }

    private static double[] parseClick(String value) {
        try {
            String[] parts = value.split(",");
            return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
        } catch (RuntimeException e) {
            return new double[]{0.5, 0.816};
        }
    }

    private static void shoot(Minecraft minecraft, String target, String detail, boolean thenStop) {
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            try (NativeImage shot = image) {
                shot.writeToFile(Path.of(target));
                Constants.LOG.info("[screenshot] wrote {} {}", target, detail);
            } catch (IOException e) {
                Constants.LOG.error("[screenshot] could not write {}", target, e);
            }
            if (thenStop) minecraft.execute(minecraft::stop);
        });
    }
}
