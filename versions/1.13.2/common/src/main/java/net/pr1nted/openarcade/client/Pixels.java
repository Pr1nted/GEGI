package net.pr1nted.openarcade.client;

import net.minecraft.client.renderer.texture.NativeImage;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Where a NativeImage keeps its pixels, by reflection: Forge 25 ships no Mixin, so no
 * accessor. A 1.13.2 NativeImage has exactly one long instance field, the address.
 */
final class Pixels {
    private Pixels() {}

    private static Field field;

    static long address(NativeImage image) {
        try {
            if (field == null) {
                for (Field f : NativeImage.class.getDeclaredFields()) {
                    if (f.getType() == long.class && !Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        field = f;
                        break;
                    }
                }
                if (field == null) throw new IllegalStateException("NativeImage has no pixel address field");
            }
            return field.getLong(image);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read NativeImage's pixel address", e);
        }
    }
}
