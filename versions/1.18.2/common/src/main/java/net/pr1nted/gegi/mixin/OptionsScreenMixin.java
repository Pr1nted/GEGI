package net.pr1nted.gegi.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.pr1nted.gegi.client.ArcadeClient;
import net.pr1nted.gegi.client.Lang;
import net.pr1nted.gegi.client.OptionsButtonHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The GEGI button in Options. Top right, outside the layout grid, so it can
 * never push a vanilla button out of place or collide with another mod's row.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen implements OptionsButtonHolder {

    @Unique
    private Button gegi$button;

    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void gegi$addButton(CallbackInfo ci) {
        Screen self = this;
        gegi$button = this.addRenderableWidget(new Button(this.width - 108, 8, 100, 20, 
                        Lang.text("gegi.button"), button -> ArcadeClient.open(self)));
    }


    @Override
    public Button gegi$optionsButton() {
        return gegi$button;
    }
}
