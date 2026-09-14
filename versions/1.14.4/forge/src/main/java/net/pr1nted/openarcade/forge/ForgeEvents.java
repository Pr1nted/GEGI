package net.pr1nted.openarcade.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.pr1nted.openarcade.client.ArcadeClient;

/**
 * Forge 28 ships no Mixin, so on Forge the Options button, /arcade and the tick come
 * from Forge's own events instead of the mixins Fabric uses. Nothing extra to install.
 */
public final class ForgeEvents {

    @SubscribeEvent
    public void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof OptionsScreen) {
            event.addWidget(ArcadeClient.optionsButtonFor(event.getGui()));
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message.startsWith("/") && ArcadeClient.handleCommand(message.substring(1))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ArcadeClient.tick(Minecraft.getInstance());
    }
}
