package net.pr1nted.gegi.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.pr1nted.gegi.client.ArcadeClient;

/**
 * Forge 25 ships no Mixin, so the Options button, /arcade and the tick come from
 * Forge's own events. Nothing extra to install.
 */
public final class ForgeEvents {

    @SubscribeEvent
    public void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen screen = event.getGui();
        if (screen instanceof GuiOptions) {
            event.addButton(ArcadeClient.optionsButton(screen, screen.width - 108, 8));
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
