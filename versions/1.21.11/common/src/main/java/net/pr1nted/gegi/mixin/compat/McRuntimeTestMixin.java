package net.pr1nted.gegi.mixin.compat;

import net.pr1nted.gegi.client.SelfTestHold;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only for CI. MC-Runtime-Test joins a world and quits as soon as the loading screen
 * closes, which is before GEGI's in-game self-test has had a single tick. It
 * offers {@code McRuntimeTest.tickHook()} for exactly this: returning false pauses it.
 * While the self-test is running, it is paused.
 *
 * <p>{@code @Pseudo} and an optional config: without MC-Runtime-Test installed, which
 * is every player's game, the target does not exist and nothing is applied.
 */
@Pseudo
@Mixin(targets = "me.earth.mc_runtime_test.McRuntimeTest", remap = false)
public abstract class McRuntimeTestMixin {

    @Inject(method = "tickHook", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gegi$waitForSelfTest(CallbackInfoReturnable<Boolean> cir) {
        if (SelfTestHold.holdRuntimeTest()) cir.setReturnValue(false);
    }
}
