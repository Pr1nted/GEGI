package net.pr1nted.gegi.fabric;

import io.github.prospector.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;
import net.pr1nted.gegi.Constants;
import net.pr1nted.gegi.client.ArcadeClient;

import java.util.function.Function;

/** Mod Menu's Config button opens the arcade. Mod Menu 1.7 names the mod and returns a plain factory. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public String getModId() {
        return Constants.MOD_ID;
    }

    @Override
    public Function<Screen, ? extends Screen> getConfigScreenFactory() {
        return ArcadeClient::screen;
    }
}
