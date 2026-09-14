package net.pr1nted.openarcade.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.pr1nted.openarcade.client.ArcadeClient;

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
