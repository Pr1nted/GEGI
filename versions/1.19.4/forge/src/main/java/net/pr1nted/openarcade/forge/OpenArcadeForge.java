package net.pr1nted.openarcade.forge;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.pr1nted.openarcade.Constants;
import net.pr1nted.openarcade.client.ArcadeClient;

/** Forge. The Config button in Forge's mod list opens the arcade. */
@Mod(Constants.MOD_ID)
public final class OpenArcadeForge {
    public OpenArcadeForge() {
        // Forge 45 has no constructor injection, and its config screen factory also takes the game.
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> ArcadeClient.screen(parent)));
        Constants.LOG.info("{} loaded on Forge", Constants.MOD_NAME);
    }
}
