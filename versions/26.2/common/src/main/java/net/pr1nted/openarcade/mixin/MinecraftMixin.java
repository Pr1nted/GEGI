package net.pr1nted.openarcade.mixin;

import net.minecraft.client.Minecraft;
import net.pr1nted.openarcade.client.ArcadeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One client tick hook, instead of a loader's tick event. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void openarcade$tick(CallbackInfo ci) {
        ArcadeClient.tick((Minecraft) (Object) this);
    }
}
