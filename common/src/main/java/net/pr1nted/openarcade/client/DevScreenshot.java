package net.pr1nted.openarcade.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.pr1nted.openarcade.Constants;

import java.io.IOException;
import java.nio.file.Path;

/**
 * For store pages and the README: OPENARCADE_SCREENSHOT=/path/to/file.png opens the
 * arcade over the title screen, waits for itch.io's thumbnails, saves what the game
 * drew, and quits. Does nothing without the variable.
 */
final class DevScreenshot {
    private DevScreenshot() {}

    private static final String TARGET = System.getenv("OPENARCADE_SCREENSHOT");
    private static final int MIN_THUMBNAILS = 6;
    private static final int MAX_WAIT_TICKS = 400;

    private static boolean opened;
    private static boolean taken;
    private static int ticks;

    static void tick(Minecraft minecraft) {
        if (TARGET == null || TARGET.isBlank() || taken) return;
        Screen screen = minecraft.gui.screen();
        if (!opened) {
            if (screen instanceof TitleScreen && minecraft.gui.overlay() == null) {
                ArcadeClient.open(screen);
                opened = true;
            }
            return;
        }
        if (!(screen instanceof ArcadeScreen arcade)) return;
        ticks++;
        if (ticks < 60 || (arcade.thumbnailsReady() < MIN_THUMBNAILS && ticks < MAX_WAIT_TICKS)) return;
        taken = true;
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            try (NativeImage shot = image) {
                shot.writeToFile(Path.of(TARGET));
                Constants.LOG.info("[screenshot] wrote {} ({} thumbnails loaded)", TARGET, arcade.thumbnailsReady());
            } catch (IOException e) {
                Constants.LOG.error("[screenshot] could not write {}", TARGET, e);
            }
            minecraft.execute(minecraft::stop);
        });
    }
}
