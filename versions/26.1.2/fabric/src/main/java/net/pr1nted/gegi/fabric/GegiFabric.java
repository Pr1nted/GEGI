package net.pr1nted.gegi.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.pr1nted.gegi.Constants;

/** Fabric and Quilt. Everything the mod does is in common mixins; this only says hello. */
public final class GegiFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Constants.LOG.info("{} loaded on Fabric", Constants.MOD_NAME);
    }
}
