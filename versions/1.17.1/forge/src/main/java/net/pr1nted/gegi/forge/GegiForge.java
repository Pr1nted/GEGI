package net.pr1nted.gegi.forge;

import net.minecraftforge.fmlclient.ConfigGuiHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.pr1nted.gegi.Constants;
import net.pr1nted.gegi.client.ArcadeClient;

/** Forge. The Config button in Forge's mod list opens the arcade. */
@Mod(Constants.MOD_ID)
public final class GegiForge {
    public GegiForge() {
        // Forge 37 has no constructor injection, and its config screen factory also takes the game.
        ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory((minecraft, parent) -> ArcadeClient.screen(parent)));
        Constants.LOG.info("{} loaded on Forge", Constants.MOD_NAME);
    }
}
