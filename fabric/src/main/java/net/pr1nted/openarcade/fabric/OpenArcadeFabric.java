package net.pr1nted.openarcade.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.pr1nted.openarcade.Constants;

/** Fabric and Quilt. Everything the mod does is in common mixins; this only says hello. */
public final class OpenArcadeFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Constants.LOG.info("{} loaded on Fabric", Constants.MOD_NAME);
    }
}
