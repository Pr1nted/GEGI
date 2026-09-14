package net.pr1nted.openarcade.forge;

import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.pr1nted.openarcade.Constants;
import net.pr1nted.openarcade.client.ArcadeClient;

/** Forge. The Config button in Forge's mod list opens the arcade. */
@Mod(Constants.MOD_ID)
public final class OpenArcadeForge {
    public OpenArcadeForge() {
        // Forge 36 registers a mod's config screen as an extension point.
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
                () -> (minecraft, parent) -> ArcadeClient.screen(parent));
        Constants.LOG.info("{} loaded on Forge", Constants.MOD_NAME);
    }
}
