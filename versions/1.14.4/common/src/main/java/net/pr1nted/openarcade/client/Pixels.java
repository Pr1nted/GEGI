package net.pr1nted.openarcade.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.pr1nted.openarcade.mixin.NativeImageAccessor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Where a NativeImage keeps its pixels. Through the mixin accessor where Mixin runs
 * (Fabric), and by reflection where it does not: Forge 28 ships no Mixin. A
 * NativeImage has exactly one long instance field, the address.
 */
final class Pixels {
    private Pixels() {}

    private static Field field;

    static long address(NativeImage image) {
        Object o = image;
        if (o instanceof NativeImageAccessor) return ((NativeImageAccessor) o).openarcade$pixels();
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
