package net.pr1nted.gegi.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.pr1nted.gegi.catalog.Catalog;
import net.pr1nted.gegi.catalog.GameEntry;
import net.pr1nted.gegi.menu.ArcadeModel;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Optional;

import static net.pr1nted.gegi.menu.ArcadeModel.*;

/**
 * The arcade, drawn with 1.13.2's GUI (Forge's names: GuiScreen, GuiButton,
 * GuiTextField). What it shows and what a click does is {@link ArcadeModel}'s, shared
 * with every other Minecraft version; this class is only the widgets, the drawing
 * and the input.
 */
public final class ArcadeScreen extends GuiScreen {

    private final GuiScreen parent;
    private final ArcadeModel model;
    private final Thumbnails thumbnails = new Thumbnails();
    private GuiTextField search;
    private GuiTextField link;
    private int framesDrawn;
    private double lastMouseX;
    private double lastMouseY;

    public ArcadeScreen(GuiScreen parent, Catalog catalog) {
        this.parent = parent;
        this.model = new ArcadeModel(catalog);
    }

    public GuiScreen parent() {
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
    protected void initGui() {
        this.buttons.clear();
        this.children.clear();
        int left = model.listLeft(this.width);
        int right = model.listRight(this.width);
        for (int i = 0; i < model.tabCount(); i++) {
            final int index = i;
            GuiButton tab = this.addButton(Buttons.of(model.tabX(this.width, i), TABS_Y, model.tabWidth(this.width), 20, model.tabLabel(i), () -> {
                model.selectTab(index);
                this.initGui();
            }));
            tab.enabled = i != model.tab();
        }

        int half = model.fieldHalf(this.width);
        search = new GuiTextField(0, this.fontRenderer, left, FIELDS_Y, half, 18);
        search.setText(model.query());
        search.setSuggestion(model.query().isEmpty() ? Lang.string("gegi.search") : "");
        search.setTextAcceptHandler((id, text) -> {
            model.setQuery(text);
            search.setSuggestion(text.isEmpty() ? Lang.string("gegi.search") : "");
        });
        this.children.add(search);

        link = new GuiTextField(1, this.fontRenderer, left + half + ROW_GAP, FIELDS_Y, half - OPEN_LINK_WIDTH - ROW_GAP, 18);
        link.setMaxStringLength(512);
        link.setText(model.pasted());
        link.setSuggestion(model.pasted().isEmpty() ? Lang.string("gegi.link") : "");
        link.setTextAcceptHandler((id, text) -> {
            model.setPasted(text);
            link.setSuggestion(text.isEmpty() ? Lang.string("gegi.link") : "");
        });
        this.children.add(link);
        this.addButton(Buttons.of(right - OPEN_LINK_WIDTH, FIELDS_Y - 1, OPEN_LINK_WIDTH, 20, Lang.string("gegi.link.open"),
                () -> model.takePasted().ifPresent(game -> ArcadeClient.play(this, game))));

        this.addButton(Buttons.of(right - DONE_WIDTH, this.height - FOOTER + 6, DONE_WIDTH, 20, I18n.format("gui.done"), this::close));
    }

    @Override
    public void tick() {
        if (search != null) search.tick();
        if (link != null) link.tick();
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        this.drawDefaultBackground();
        super.render(mouseX, mouseY, partialTicks);
        search.drawTextField(mouseX, mouseY, partialTicks);
        link.drawTextField(mouseX, mouseY, partialTicks);
        this.drawCenteredString(this.fontRenderer, Lang.string("gegi.title"), this.width / 2, 9, GOLD);

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
            drawRect(left, rowY, right, rowY + ROW_HEIGHT - ROW_GAP, game.equals(hovered) ? ROW_HOVER : ROW);
            drawThumbnail(game, left + 4, rowY + 2);

            int textX = left + THUMB_WIDTH + 12;
            int textWidth = right - textX - 6;
            boolean featured = model.featured(game);
            this.drawString(this.fontRenderer, this.fontRenderer.trimStringToWidth(game.title(), textWidth), textX, rowY + 6, featured ? GOLD : WHITE);
            this.drawString(this.fontRenderer, this.fontRenderer.trimStringToWidth(game.blurb(), textWidth), textX, rowY + 20, DIM);
            this.drawString(this.fontRenderer, this.fontRenderer.trimStringToWidth(model.metaLine(game), textWidth), textX, rowY + 34, featured ? GOLD : DIM);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        String status = model.status(games);
        if (status != null) {
            this.drawCenteredString(this.fontRenderer, status, this.width / 2, LIST_TOP + (games.isEmpty() ? 20 : -10 + (bottom - LIST_TOP)), DIM);
        }
        this.drawString(this.fontRenderer, this.fontRenderer.trimStringToWidth(model.footer(), right - DONE_WIDTH - 8 - left),
                left, this.height - FOOTER + 12, model.footerIsNotice() ? GOLD : DIM);
        framesDrawn++;
    }

    /** OpenGL's scissor takes window pixels, from the bottom. */
    private void scissor(int x1, int y1, int x2, int y2) {
        double scale = this.mc.mainWindow.getGuiScaleFactor();
        int windowHeight = this.mc.mainWindow.getFramebufferHeight();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x1 * scale), (int) (windowHeight - y2 * scale), (int) ((x2 - x1) * scale), (int) ((y2 - y1) * scale));
    }

    private void drawThumbnail(GameEntry game, int x, int y) {
        Optional<Thumbnails.Ready> ready = game.bundledImage().isPresent()
                ? BundledImages.get(game.bundledImage().get())
                : game.imageUrl().flatMap(thumbnails::get);
        if (ready.isPresent()) {
            this.mc.getTextureManager().bindTexture(ready.get().id());
            // The whole texture, scaled into the card's rectangle.
            drawModalRectWithCustomSizedTexture(x, y, 0, 0, THUMB_WIDTH, THUMB_HEIGHT, THUMB_WIDTH, THUMB_HEIGHT);
        } else {
            drawRect(x, y, x + THUMB_WIDTH, y + THUMB_HEIGHT, PLACEHOLDER);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        GameEntry game = model.gameAt(this.width, this.height, mouseX, mouseY);
        if (game == null) return false;
        ArcadeClient.play(this, game);
        return true;
    }

    @Override
    public boolean mouseScrolled(double scrollY) {
        if (super.mouseScrolled(scrollY)) return true;
        model.scrollNotches(scrollY);
        return true;
    }

    @Override
    public void close() {
        this.mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        thumbnails.releaseAll();
        super.onGuiClosed();
    }
}
