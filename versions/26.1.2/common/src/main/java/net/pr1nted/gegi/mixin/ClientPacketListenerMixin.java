package net.pr1nted.gegi.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.pr1nted.gegi.client.ArcadeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * /arcade without a command API: every command the chat screen sends goes through
 * sendCommand, so ours is caught here and never reaches the server. No loader's
 * client-command API is needed, which is what keeps the mod dependency-free.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void gegi$interceptCommand(String command, CallbackInfo ci) {
        if (ArcadeClient.handleCommand(command)) ci.cancel();
    }
}
