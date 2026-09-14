package net.pr1nted.gegi.forge;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.pr1nted.gegi.Constants;
import net.pr1nted.gegi.client.ArcadeClient;

/** Forge. The Config button in Forge's mod list opens the arcade. */
@Mod(Constants.MOD_ID)
public final class GegiForge {
    public GegiForge(FMLJavaModLoadingContext context) {
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(ArcadeClient::screen));
        Constants.LOG.info("{} loaded on Forge", Constants.MOD_NAME);
    }
}
