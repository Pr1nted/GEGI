package net.pr1nted.openarcade.client;

import net.minecraft.client.gui.GuiButton;

/**
 * Buttons that run a callback. 1.13.2's GuiButton has no press callback: a button
 * overrides onClick. Made here rather than in a mixin, because a mixin class may not
 * hold anonymous classes of its own.
 */
public final class Buttons {
    private Buttons() {}

    public static GuiButton of(int x, int y, int width, int height, String label, Runnable onPress) {
        return new GuiButton(0, x, y, width, height, label) {
            @Override
            public void onClick(double mouseX, double mouseY) {
                onPress.run();
            }
        };
    }
}
