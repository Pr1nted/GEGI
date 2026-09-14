package net.pr1nted.gegi.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.pr1nted.gegi.Constants;
import net.pr1nted.gegi.client.ArcadeClient;

/** NeoForge. The Config button in NeoForge's mod list opens the arcade. */
@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public final class GegiNeoForge {
    public GegiNeoForge(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (mod, modListScreen) -> ArcadeClient.screen(modListScreen));
        Constants.LOG.info("{} loaded on NeoForge", Constants.MOD_NAME);
    }
}
