package net.pr1nted.gegi.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.pr1nted.gegi.Constants;
import net.pr1nted.gegi.client.ArcadeClient;

/** Forge. The Config button in Forge's mod list opens the arcade. */
@Mod(Constants.MOD_ID)
public final class GegiForge {
    public GegiForge() {
        // Forge 25 registers a mod's config screen as an extension point (a GuiScreen factory).
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
                () -> (minecraft, parent) -> ArcadeClient.screen(parent));
        MinecraftForge.EVENT_BUS.register(new ForgeEvents());
        Constants.LOG.info("{} loaded on Forge", Constants.MOD_NAME);
    }
}
