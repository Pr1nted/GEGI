package net.pr1nted.openarcade.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Where a NativeImage keeps its pixels. Chromium's frames are copied straight into
 * the game screen's texture; newer Minecraft offers getPointer() for that, this one
 * keeps the address private.
 */
@Mixin(NativeImage.class)
public interface NativeImageAccessor {
    @Accessor("pixels")
    long openarcade$pixels();
}
