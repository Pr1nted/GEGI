package net.pr1nted.gegi.client;

import net.minecraft.client.gui.components.Button;
import org.jspecify.annotations.Nullable;

/** Implemented by OptionsScreen through a mixin, so the self-test can find and press the button. */
public interface OptionsButtonHolder {
    @Nullable Button gegi$optionsButton();
}
