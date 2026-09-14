package net.pr1nted.openarcade.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.sounds.SoundEvents;
import net.pr1nted.openarcade.catalog.Catalog;
import net.pr1nted.openarcade.catalog.GameEntry;
import net.pr1nted.openarcade.menu.ArcadeModel;

import java.util.List;
import java.util.Optional;

import static net.pr1nted.openarcade.menu.ArcadeModel.*;

/**
 * The arcade, drawn with 1.16.5's GUI. What it shows and what a click does is
 * {@link ArcadeModel}'s, shared with every other Minecraft version; this class is
 * only the widgets, the drawing and the input.
 */
public final class ArcadeScreen extends Screen {

    private final Screen parent;
    private final ArcadeModel model;
    private final Thumbnails thumbnails = new Thumbnails();
    private int framesDrawn;

    public ArcadeScreen(Screen parent, Catalog catalog) {
        super(Lang.text("openarcade.title"));
        this.parent = parent;
        this.model = new ArcadeModel(catalog);
    }

    public Screen parent() {
        return parent;
    }

    /** Thumbnails downloaded and on the GPU; the dev screenshot waits for a few. */
    int thumbnailsReady() {
        return thumbnails.readyCount();
    }

    /** How many frames this screen has drawn; the self-test waits for a few. */
    public int framesDrawn() {
        return framesDrawn;
    }

    @Override
    protected void init() {
        int left = model.listLeft(this.width);
        int right = model.listRight(this.width);
        for (int i = 0; i < model.tabCount(); i++) {
            final int index = i;
            Button button = this.addButton(new Button(model.tabX(this.width, i), TABS_Y, model.tabWidth(this.width), 20, new TextComponent(model.tabLabel(i)), b -> {
                        model.selectTab(index);
                        this.buttons.clear();
                        this.children.clear();
                        this.init();
                    }));
            button.active = i != model.tab();
        }

        int half = model.fieldHalf(this.width);
        EditBox search = new EditBox(this.font, left, FIELDS_Y, half, 18, Lang.text("openarcade.search"));
        search.setSuggestion(search.getValue().isEmpty() ? Lang.text("openarcade.search").withStyle(s -> s.withColor(TextColor.fromRgb(DIM & 0xFFFFFF))).getString() : "");
        search.setValue(model.query());
        search.setResponder(text -> {
            model.setQuery(text);
            search.setSuggestion(text.isEmpty() ? Lang.string("openarcade.search") : "");
        });
        this.addButton(search);

        EditBox link = new EditBox(this.font, left + half + ROW_GAP, FIELDS_Y, half - OPEN_LINK_WIDTH - ROW_GAP, 18, Lang.text("openarcade.link"));
        link.setMaxLength(512);
        link.setSuggestion(link.getValue().isEmpty() ? Lang.text("openarcade.link").withStyle(s -> s.withColor(TextColor.fromRgb(DIM & 0xFFFFFF))).getString() : "");
        link.setValue(model.pasted());
        link.setResponder(text -> {
            model.setPasted(text);
            link.setSuggestion(text.isEmpty() ? Lang.string("openarcade.link") : "");
        });
        this.addButton(link);
        this.addButton(new Button(right - OPEN_LINK_WIDTH, FIELDS_Y - 1, OPEN_LINK_WIDTH, 20, Lang.text("openarcade.link.open"), b -> model.takePasted().ifPresent(game -> ArcadeClient.play(this, game))));

        this.addButton(new Button(right - DONE_WIDTH, this.height - FOOTER + 6, DONE_WIDTH, 20, CommonComponents.GUI_DONE, b -> this.onClose()));
    }

    @Override
    public void render(PoseStack graphics, int mouseX, int mouseY, float a) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, a);
        drawCenteredString(graphics, this.font, this.title, this.width / 2, 9, GOLD);

        int left = model.listLeft(this.width);
        int right = model.listRight(this.width);
        int bottom = model.listBottom(this.height);
        List<GameEntry> games = model.visibleGames();
        model.clampScroll(this.height, games.size());
        GameEntry hovered = model.gameAt(this.width, this.height, mouseX, mouseY);

        scissor(left, LIST_TOP, right, bottom);
        for (int i = 0; i < games.size(); i++) {
            int rowY = model.rowY(i);
            if (rowY + ROW_HEIGHT < LIST_TOP || rowY > bottom) continue;
            GameEntry game = games.get(i);
            fill(graphics, left, rowY, right, rowY + ROW_HEIGHT - ROW_GAP, game.equals(hovered) ? ROW_HOVER : ROW);
            drawThumbnail(graphics, game, left + 4, rowY + 2);

            int textX = left + THUMB_WIDTH + 12;
            int textWidth = right - textX - 6;
            boolean featured = model.featured(game);
            drawString(graphics, this.font, this.font.plainSubstrByWidth(game.title(), textWidth), textX, rowY + 6, featured ? GOLD : WHITE);
            drawString(graphics, this.font, this.font.plainSubstrByWidth(game.blurb(), textWidth), textX, rowY + 20, DIM);
            drawString(graphics, this.font, this.font.plainSubstrByWidth(model.metaLine(game), textWidth), textX, rowY + 34, featured ? GOLD : DIM);
        }
        RenderSystem.disableScissor();

        String status = model.status(games);
        if (status != null) {
            drawCenteredString(graphics, this.font, status, this.width / 2, LIST_TOP + (games.isEmpty() ? 20 : -10 + (bottom - LIST_TOP)), DIM);
        }
        drawString(graphics, this.font, this.font.plainSubstrByWidth(model.footer(), right - DONE_WIDTH - 8 - left),
                left, this.height - FOOTER + 12, model.footerIsNotice() ? GOLD : DIM);
        framesDrawn++;
    }

    /** GuiComponent had no scissor yet: RenderSystem's takes window pixels, from the bottom. */
    private void scissor(int x1, int y1, int x2, int y2) {
        double scale = this.minecraft.getWindow().getGuiScale();
        int windowHeight = this.minecraft.getWindow().getHeight();
        RenderSystem.enableScissor((int) (x1 * scale), (int) (windowHeight - y2 * scale),
                (int) ((x2 - x1) * scale), (int) ((y2 - y1) * scale));
    }

    private void drawThumbnail(PoseStack graphics, GameEntry game, int x, int y) {
        Optional<Thumbnails.Ready> ready = game.bundledImage().isPresent()
                ? BundledImages.get(game.bundledImage().get())
                : game.imageUrl().flatMap(thumbnails::get);
        if (ready.isPresent()) {
            Thumbnails.Ready r = ready.get();
            this.minecraft.getTextureManager().bind(r.id());
            blit(graphics, x, y, THUMB_WIDTH, THUMB_HEIGHT, 0, 0,
                    r.width(), r.height(), r.width(), r.height());
        } else {
            fill(graphics, x, y, x + THUMB_WIDTH, y + THUMB_HEIGHT, PLACEHOLDER);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        GameEntry game = model.gameAt(this.width, this.height, mouseX, mouseY);
        if (game == null) return false;
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        ArcadeClient.play(this, game);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollY) {
        double scrollX = 0; // 1.19.4 reports one wheel, the vertical one
        if (super.mouseScrolled(x, y, scrollY)) return true;
        model.scrollNotches(scrollY);
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        thumbnails.releaseAll();
        super.removed();
    }
}
