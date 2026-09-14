package net.pr1nted.gegi.fabric;

import io.github.prospector.modmenu.api.ConfigScreenFactory;
import io.github.prospector.modmenu.api.ModMenuApi;
import net.pr1nted.gegi.client.ArcadeClient;

/**
 * Mod Menu's config button opens the arcade. Loaded only when Mod Menu is installed,
 * through the "modmenu" entrypoint; Mod Menu is compile-only, never required.
 */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ArcadeClient::screen;
    }
}
