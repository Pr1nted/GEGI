package net.pr1nted.gegi.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.pr1nted.gegi.catalog.Catalog;
import net.pr1nted.gegi.catalog.GameEntry;
import net.pr1nted.gegi.menu.ArcadeModel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static net.pr1nted.gegi.menu.ArcadeModel.*;

/**
 * The arcade, drawn with 26.2's GUI. What it shows and what a click does is
 * {@link ArcadeModel}'s, shared with every other Minecraft version; this class is
 * only the widgets, the drawing and the input.
 */
public final class ArcadeScreen extends Screen {

    private final @Nullable Screen parent;
    private final ArcadeModel model;
    private final Thumbnails thumbnails = new Thumbnails();
    private int framesDrawn;

    public ArcadeScreen(@Nullable Screen parent, Catalog catalog) {
        super(Lang.text("gegi.title"));
        this.parent = parent;
        this.model = new ArcadeModel(catalog);
    }

    public @Nullable Screen parent() {
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
            Button button = this.addRenderableWidget(Button.builder(Component.literal(model.tabLabel(i)), b -> {
                        model.selectTab(index);
                        this.rebuildWidgets();
                    })
                    .bounds(model.tabX(this.width, i), TABS_Y, model.tabWidth(this.width), 20)
                    .build());
            button.active = i != model.tab();
        }

        int half = model.fieldHalf(this.width);
        EditBox search = new EditBox(this.font, left, FIELDS_Y, half, 18, Lang.text("gegi.search"));
        search.setHint(Lang.text("gegi.search").withStyle(s -> s.withColor(DIM)));
        search.setValue(model.query());
        search.setResponder(model::setQuery);
        this.addRenderableWidget(search);

        EditBox link = new EditBox(this.font, left + half + ROW_GAP, FIELDS_Y, half - OPEN_LINK_WIDTH - ROW_GAP, 18, Lang.text("gegi.link"));
        link.setMaxLength(512);
        link.setHint(Lang.text("gegi.link").withStyle(s -> s.withColor(DIM)));
        link.setValue(model.pasted());
        link.setResponder(model::setPasted);
        this.addRenderableWidget(link);
        this.addRenderableWidget(Button.builder(Lang.text("gegi.link.open"),
                        b -> model.takePasted().ifPresent(game -> ArcadeClient.play(this, game)))
                .bounds(right - OPEN_LINK_WIDTH, FIELDS_Y - 1, OPEN_LINK_WIDTH, 20)
                .build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(right - DONE_WIDTH, this.height - FOOTER + 6, DONE_WIDTH, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.width / 2, 9, GOLD);

        int left = model.listLeft(this.width);
        int right = model.listRight(this.width);
        int bottom = model.listBottom(this.height);
        List<GameEntry> games = model.visibleGames();
        model.clampScroll(this.height, games.size());
        GameEntry hovered = model.gameAt(this.width, this.height, mouseX, mouseY);

        graphics.enableScissor(left, LIST_TOP, right, bottom);
        for (int i = 0; i < games.size(); i++) {
            int rowY = model.rowY(i);
            if (rowY + ROW_HEIGHT < LIST_TOP || rowY > bottom) continue;
            GameEntry game = games.get(i);
            graphics.fill(left, rowY, right, rowY + ROW_HEIGHT - ROW_GAP, game.equals(hovered) ? ROW_HOVER : ROW);
            drawThumbnail(graphics, game, left + 4, rowY + 2);

            int textX = left + THUMB_WIDTH + 12;
            int textWidth = right - textX - 6;
            boolean featured = model.featured(game);
            graphics.text(this.font, this.font.plainSubstrByWidth(game.title(), textWidth), textX, rowY + 6, featured ? GOLD : WHITE);
            graphics.text(this.font, this.font.plainSubstrByWidth(game.blurb(), textWidth), textX, rowY + 20, DIM);
            graphics.text(this.font, this.font.plainSubstrByWidth(model.metaLine(game), textWidth), textX, rowY + 34, featured ? GOLD : DIM);
        }
        graphics.disableScissor();

        String status = model.status(games);
        if (status != null) {
            graphics.centeredText(this.font, status, this.width / 2, LIST_TOP + (games.isEmpty() ? 20 : -10 + (bottom - LIST_TOP)), DIM);
        }
        graphics.text(this.font, this.font.plainSubstrByWidth(model.footer(), right - DONE_WIDTH - 8 - left),
                left, this.height - FOOTER + 12, model.footerIsNotice() ? GOLD : DIM);
        framesDrawn++;
    }

    private void drawThumbnail(GuiGraphicsExtractor graphics, GameEntry game, int x, int y) {
        Optional<Thumbnails.Ready> ready = game.bundledImage().isPresent()
                ? BundledImages.get(game.bundledImage().get())
                : game.imageUrl().flatMap(thumbnails::get);
        if (ready.isPresent()) {
            Thumbnails.Ready r = ready.get();
            graphics.blit(RenderPipelines.GUI_TEXTURED, r.id(), x, y, 0, 0, THUMB_WIDTH, THUMB_HEIGHT,
                    r.width(), r.height(), r.width(), r.height());
        } else {
            graphics.fill(x, y, x + THUMB_WIDTH, y + THUMB_HEIGHT, PLACEHOLDER);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        GameEntry game = model.gameAt(this.width, this.height, event.x(), event.y());
        if (game == null) return false;
        AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
        ArcadeClient.play(this, game);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) return true;
        model.scrollNotches(scrollY);
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        thumbnails.releaseAll();
        super.removed();
    }
}
