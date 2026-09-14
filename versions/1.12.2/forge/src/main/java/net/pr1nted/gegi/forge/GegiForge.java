package net.pr1nted.gegi.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.pr1nted.gegi.Constants;

/**
 * Forge for Minecraft 1.12.2. The name, version and authors come from mcmod.info; the
 * Config button in Forge's mod list opens the arcade through {@link ArcadeGuiFactory}.
 */
@Mod(modid = Constants.MOD_ID, useMetadata = true, clientSideOnly = true,
        acceptedMinecraftVersions = "[1.12.2]",
        guiFactory = "net.pr1nted.gegi.forge.ArcadeGuiFactory")
public final class GegiForge {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ForgeEvents());
        Constants.LOG.info("{} loaded on Forge", Constants.MOD_NAME);
    }
}
