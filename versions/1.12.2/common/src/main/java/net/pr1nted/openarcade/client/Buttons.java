package net.pr1nted.openarcade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * Buttons that run a callback. 1.12.2's GuiButton has none: the screen hears about a
 * click in actionPerformed, by the button's id. Forge's ActionPerformedEvent calls
 * {@link #press} first, so these buttons work on any screen, Options included.
 */
public final class Buttons {
    private Buttons() {}

    /** Not an id Options, or any vanilla screen, gives a button of its own. */
    static final int ID = 0x4F41;

    public static final class Action extends GuiButton {
        private final Runnable onPress;

        Action(int x, int y, int width, int height, String label, Runnable onPress) {
            super(ID, x, y, width, height, label);
            this.onPress = onPress;
        }
    }

    public static GuiButton of(int x, int y, int width, int height, String label, Runnable onPress) {
        return new Action(x, y, width, height, label, onPress);
    }

    /** Runs the button's action if it is one of these, and says whether it was. */
    public static boolean press(GuiButton button) {
        if (!(button instanceof Action)) return false;
        button.playPressSound(Minecraft.getMinecraft().getSoundHandler());
        ((Action) button).onPress.run();
        return true;
    }
}
