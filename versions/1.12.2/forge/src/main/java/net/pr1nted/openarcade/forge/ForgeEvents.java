package net.pr1nted.openarcade.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.pr1nted.openarcade.client.ArcadeClient;
import net.pr1nted.openarcade.client.Buttons;

/**
 * Forge 1.12.2 ships no Mixin, so the Options button, its click, /arcade and the tick
 * come from Forge's own events. Nothing extra to install.
 */
public final class ForgeEvents {

    @SubscribeEvent
    public void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen screen = event.getGui();
        if (screen instanceof GuiOptions) {
            event.getButtonList().add(ArcadeClient.optionsButton(screen, screen.width - 108, 8));
        }
    }

    /**
     * A 1.12.2 button has no callback: the screen gets the click in actionPerformed, by
     * id. Open Arcade's buttons run their own action here instead, on every screen, and
     * the click stops: Options never sees a button it does not know.
     */
    @SubscribeEvent
    public void onButton(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (Buttons.press(event.getButton())) event.setCanceled(true);
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
        if (event.phase == TickEvent.Phase.END) ArcadeClient.tick(Minecraft.getMinecraft());
    }
}
