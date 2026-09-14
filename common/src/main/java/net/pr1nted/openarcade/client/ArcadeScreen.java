package net.pr1nted.openarcade.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.pr1nted.openarcade.catalog.Catalog;
import net.pr1nted.openarcade.catalog.GameEntry;
import net.pr1nted.openarcade.catalog.Links;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The arcade: a row of tabs (Recommended, one per itch.io shelf, Browse sites), a
 * search box, a box for pasting any itch.io or Newgrounds link, and the games as
 * cards. Clicking a card opens the game in the player's browser.
 *
 * <p>Recommended always starts with the catalog's own picks, Open Doctrines first,
 * then fills with itch.io's popular web games.
 */
public final class ArcadeScreen extends Screen {

    private static final int ROW_HEIGHT = 58;
    private static final int THUMB_WIDTH = 63;
    private static final int THUMB_HEIGHT = 50;
    private static final int LIST_TOP = 74;
    private static final int FOOTER = 32;

    private static final int WHITE = 0xFFE8FFF1;
    private static final int DIM = 0xFF8FB9A2;
    private static final int GOLD = 0xFFFFD700;
    private static final int ROW = 0x70101A16;
    private static final int ROW_HOVER = 0xA02E5A45;
    private static final int PLACEHOLDER = 0xFF1A4D33;

    private final @Nullable Screen parent;
    private final Catalog catalog;
    private final List<GameEntry> siteEntries = new ArrayList<>();
    private final Thumbnails thumbnails = new Thumbnails();

    private int tab;
    private double scroll;
    private String query = "";
    private String pasted = "";
    private @Nullable Component notice;
    private int framesDrawn;

    public ArcadeScreen(@Nullable Screen parent, Catalog catalog) {
        super(Lang.text("openarcade.title"));
        this.parent = parent;
        this.catalog = catalog;
        for (Catalog.SiteLink site : catalog.sites()) {
            siteEntries.add(new GameEntry(site.title(), site.url(), site.url().getHost(), Optional.empty(), "", "Browse", Optional.empty()));
        }
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

    private int tabCount() {
        return 2 + catalog.shelves().size();
    }

    private int listLeft() {
        return Math.max(10, this.width / 2 - 230);
    }

    private int listRight() {
        return Math.min(this.width - 10, this.width / 2 + 230);
    }

    @Override
    protected void init() {
        int left = listLeft();
        int right = listRight();
        int gap = 4;
        int count = tabCount();
        int tabWidth = Math.max(40, (right - left - gap * (count - 1)) / count);
        for (int i = 0; i < count; i++) {
            final int index = i;
            Button button = this.addRenderableWidget(Button.builder(tabLabel(i), b -> selectTab(index))
                    .bounds(left + i * (tabWidth + gap), 24, tabWidth, 20)
                    .build());
            button.active = i != tab;
        }

        int half = (right - left - gap) / 2;
        EditBox search = new EditBox(this.font, left, 50, half, 18, Lang.text("openarcade.search"));
        search.setHint(Lang.text("openarcade.search").withStyle(s -> s.withColor(DIM)));
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            scroll = 0;
        });
        this.addRenderableWidget(search);

        int openWidth = 70;
        EditBox link = new EditBox(this.font, left + half + gap, 50, half - openWidth - gap, 18, Lang.text("openarcade.link"));
        link.setMaxLength(512);
        link.setHint(Lang.text("openarcade.link").withStyle(s -> s.withColor(DIM)));
        link.setValue(pasted);
        link.setResponder(text -> {
            pasted = text;
            notice = null;
        });
        this.addRenderableWidget(link);
        this.addRenderableWidget(Button.builder(Lang.text("openarcade.link.open"), b -> openPasted())
                .bounds(right - openWidth, 49, openWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(listRight() - 120, this.height - FOOTER + 6, 120, 20)
                .build());
    }

    private Component tabLabel(int index) {
        if (index == 0) return Lang.text("openarcade.tab.recommended");
        if (index == tabCount() - 1) return Lang.text("openarcade.tab.sites");
        return Component.literal(catalog.shelves().get(index - 1).tab());
    }

    private void selectTab(int index) {
        tab = index;
        scroll = 0;
        this.rebuildWidgets();
    }

    private void openPasted() {
        Optional<URI> uri = Links.parsePasted(pasted);
        if (uri.isPresent()) {
            ArcadeClient.openLink(uri.get());
            notice = null;
        } else {
            notice = Lang.text("openarcade.link.invalid");
        }
    }

    /** The games on the current tab, after the search box. Loads shelves as a side effect. */
    private List<GameEntry> visibleGames() {
        List<GameEntry> games;
        if (tab == 0) {
            Map<URI, GameEntry> merged = new LinkedHashMap<>();
            catalog.recommended().forEach(g -> merged.put(g.url(), g));
            if (!catalog.shelves().isEmpty()) {
                ShelfLoads.get(catalog.shelves().get(0)).games.forEach(g -> merged.putIfAbsent(g.url(), g));
            }
            games = new ArrayList<>(merged.values());
        } else if (tab == tabCount() - 1) {
            games = siteEntries;
        } else {
            games = ShelfLoads.get(catalog.shelves().get(tab - 1)).games;
        }
        if (query.isBlank()) return games;
        String q = query.toLowerCase(Locale.ROOT).strip();
        return games.stream()
                .filter(g -> g.title().toLowerCase(Locale.ROOT).contains(q) || g.blurb().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    private @Nullable Component status(List<GameEntry> games) {
        ShelfLoads.Load load = tab == 0 && !catalog.shelves().isEmpty() ? ShelfLoads.get(catalog.shelves().get(0))
                : tab > 0 && tab < tabCount() - 1 ? ShelfLoads.get(catalog.shelves().get(tab - 1)) : null;
        if (load != null && !load.done) return Lang.text("openarcade.loading");
        if (load != null && !load.error.isEmpty()) return Lang.text("openarcade.failed", load.error);
        if (games.isEmpty()) return Lang.text("openarcade.empty");
        return null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.width / 2, 9, GOLD);

        int left = listLeft();
        int right = listRight();
        int top = LIST_TOP;
        int bottom = this.height - FOOTER;
        List<GameEntry> games = visibleGames();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, games.size() * ROW_HEIGHT - (bottom - top))));

        graphics.enableScissor(left, top, right, bottom);
        for (int i = 0; i < games.size(); i++) {
            int rowY = top + i * ROW_HEIGHT - (int) scroll;
            if (rowY + ROW_HEIGHT < top || rowY > bottom) continue;
            GameEntry game = games.get(i);
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= Math.max(top, rowY)
                    && mouseY < Math.min(bottom, rowY + ROW_HEIGHT - 4);
            graphics.fill(left, rowY, right, rowY + ROW_HEIGHT - 4, hovered ? ROW_HOVER : ROW);
            drawThumbnail(graphics, game, left + 4, rowY + 2);

            int textX = left + THUMB_WIDTH + 12;
            int textWidth = right - textX - 6;
            boolean featured = catalog.recommended().contains(game);
            graphics.text(this.font, this.font.plainSubstrByWidth(game.title(), textWidth), textX, rowY + 6, featured ? GOLD : WHITE);
            graphics.text(this.font, this.font.plainSubstrByWidth(game.blurb(), textWidth), textX, rowY + 20, DIM);
            String meta = String.join("  ·  ", nonEmpty(featured ? Lang.string("openarcade.featured") : "",
                    game.site(), game.price()));
            graphics.text(this.font, this.font.plainSubstrByWidth(meta, textWidth), textX, rowY + 34, featured ? GOLD : DIM);
        }
        graphics.disableScissor();

        Component status = status(games);
        if (status != null) {
            graphics.centeredText(this.font, status, this.width / 2, top + (games.isEmpty() ? 20 : -10 + (bottom - top)), DIM);
        }
        Component footer = notice != null ? notice : Lang.text("openarcade.opens_in_browser");
        graphics.text(this.font, this.font.plainSubstrByWidth(footer.getString(), right - 128 - left),
                left, this.height - FOOTER + 12, notice != null ? GOLD : DIM);
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

    private static String[] nonEmpty(String... parts) {
        return java.util.Arrays.stream(parts).filter(p -> p != null && !p.isBlank()).toArray(String[]::new);
    }

    private @Nullable GameEntry gameAt(double mouseX, double mouseY) {
        int left = listLeft();
        int right = listRight();
        int top = LIST_TOP;
        int bottom = this.height - FOOTER;
        if (mouseX < left || mouseX >= right || mouseY < top || mouseY >= bottom) return null;
        int index = (int) ((mouseY - top + scroll) / ROW_HEIGHT);
        double withinRow = (mouseY - top + scroll) - index * ROW_HEIGHT;
        List<GameEntry> games = visibleGames();
        if (index < 0 || index >= games.size() || withinRow > ROW_HEIGHT - 4) return null;
        return games.get(index);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        GameEntry game = gameAt(event.x(), event.y());
        if (game == null) return false;
        AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
        ArcadeClient.openLink(game.url());
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) return true;
        scroll -= scrollY * ROW_HEIGHT / 2.0;
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
