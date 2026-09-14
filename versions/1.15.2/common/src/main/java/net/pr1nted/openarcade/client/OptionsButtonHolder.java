package net.pr1nted.openarcade.client;

import net.minecraft.client.gui.components.Button;

/** Implemented by OptionsScreen through a mixin, so the self-test can find and press the button. */
public interface OptionsButtonHolder {
    Button openarcade$optionsButton();
}
