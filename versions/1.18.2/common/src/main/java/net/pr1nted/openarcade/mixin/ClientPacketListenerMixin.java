package net.pr1nted.openarcade.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.pr1nted.openarcade.client.ArcadeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * /arcade without a command API. Before 1.19 a typed command is sent as chat starting
 * with "/", through LocalPlayer.chat, so ours is caught there and never reaches the
 * server. (The class keeps its name so every version lists the same mixins.)
 */
@Mixin(LocalPlayer.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "chat", at = @At("HEAD"), cancellable = true)
    private void openarcade$interceptCommand(String message, CallbackInfo ci) {
        if (message.startsWith("/") && ArcadeClient.handleCommand(message.substring(1))) ci.cancel();
    }
}
