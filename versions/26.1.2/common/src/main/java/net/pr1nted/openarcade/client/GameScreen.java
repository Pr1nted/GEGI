package net.pr1nted.openarcade.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.pr1nted.openarcade.Constants;
import net.pr1nted.openarcade.browser.api.BrowserProtocol;
import net.pr1nted.openarcade.catalog.GameEntry;
import net.pr1nted.openarcade.runtime.BrowserRuntime;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * A web game, playing inside Minecraft. Chromium runs in the helper process; this
 * screen shows its frames as a texture and hands it every key, click and scroll.
 *
 * <p>Escape belongs to the game (plenty of web games use it), so a single press goes
 * to the page and a double press leaves. The bar at the top says so.
 */
public final class GameScreen extends Screen {

    static final int BAR = 22;
    private static final int ESCAPE = 256;
    private static final long DOUBLE_PRESS_MS = 600;
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "game_view");

    private final @Nullable Screen parent;
    private final GameEntry game;
    private final BrowserRuntime runtime = BrowserRuntime.get();

    private @Nullable DynamicTexture texture;
    private int textureWidth;
    private int textureHeight;
    private boolean opened;
    private boolean pressed;
    private long lastEscape;
    private int browserWidth;
    private int browserHeight;
    private int framesShown;

    private final BrowserRuntime.FrameTarget target = new BrowserRuntime.FrameTarget() {
        @Override
        public ByteBuffer prepare(int width, int height) {
            if (texture == null || width != textureWidth || height != textureHeight) {
                if (texture != null) minecraft.getTextureManager().release(TEXTURE_ID);
                texture = new DynamicTexture(() -> "Open Arcade game view", width, height, false);
                minecraft.getTextureManager().register(TEXTURE_ID, texture);
                textureWidth = width;
                textureHeight = height;
            }
            return MemoryUtil.memByteBuffer(texture.getPixels().getPointer(), width * height * 4);
        }

        @Override
        public void done() {
            texture.upload();
            framesShown++;
        }
    };

    public GameScreen(@Nullable Screen parent, GameEntry game) {
        super(Lang.text("openarcade.title"));
        this.parent = parent;
        this.game = game;
    }

    public GameEntry game() {
        return game;
    }

    /** Frames drawn in this screen; the self-test waits for them. */
    public int framesShown() {
        return framesShown;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Lang.text("openarcade.game.back"), b -> this.onClose())
                .bounds(2, 1, 60, 20).build());
        this.addRenderableWidget(Button.builder(Lang.text("openarcade.game.browser"), b -> {
                    ArcadeClient.openLink(game.url());
                    this.onClose();
                })
                .bounds(this.width - 112, 1, 110, 20).build());

        double scale = minecraft.getWindow().getGuiScale();
        browserWidth = Math.max(64, Math.min(BrowserProtocol.MAX_WIDTH, (int) Math.round(this.width * scale)));
        browserHeight = Math.max(64, Math.min(BrowserProtocol.MAX_HEIGHT, (int) Math.round((this.height - BAR) * scale)));
        if (!opened) {
            runtime.open(browserWidth, browserHeight, game.url());
            opened = true;
        } else {
            runtime.resize(browserWidth, browserHeight);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        runtime.pollFrame(target);
        if (texture != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, 0, BAR, 0, 0, this.width, this.height - BAR,
                    textureWidth, textureHeight, textureWidth, textureHeight);
        } else {
            graphics.fill(0, BAR, this.width, this.height, 0xFF030806);
            graphics.centeredText(this.font, statusLine(), this.width / 2, this.height / 2, 0xFF8FB9A2);
        }
        graphics.fill(0, 0, this.width, BAR, 0xE0030806);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        String heading = runtime.title().isEmpty() ? game.title() : runtime.title();
        int room = this.width - 64 - 116 - 8;
        graphics.text(this.font, this.font.plainSubstrByWidth(heading, Math.max(20, room)), 68, 7, 0xFFE8FFF1);
        String hint = Lang.string("openarcade.game.leave_hint");
        if (this.font.width(heading) + this.font.width(hint) + 12 < room) {
            graphics.text(this.font, hint, this.width - 116 - this.font.width(hint), 7, 0xFF8FB9A2);
        }
    }

    private String statusLine() {
        switch (runtime.state()) {
            case DOWNLOADING:
                return runtime.progress() >= 0
                        ? Lang.string("openarcade.game.downloading", runtime.progress())
                        : Lang.string("openarcade.game.unpacking");
            case FAILED:
                return Lang.string("openarcade.game.failed", runtime.error());
            default:
                return Lang.string("openarcade.game.starting");
        }
    }

    // ---- input: GUI coordinates to browser pixels

    private boolean inView(double x, double y) {
        return y >= BAR && x >= 0 && x < this.width && y < this.height;
    }

    private int bx(double x) {
        return (int) Math.round(x * browserWidth / Math.max(1, this.width));
    }

    private int by(double y) {
        return (int) Math.round((y - BAR) * browserHeight / Math.max(1, this.height - BAR));
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (inView(x, y)) runtime.send(BrowserProtocol.MOUSE_MOVE + " " + bx(x) + " " + by(y));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (!inView(event.x(), event.y())) return false;
        pressed = true;
        runtime.send(BrowserProtocol.MOUSE_DOWN + " " + event.button() + " " + bx(event.x()) + " " + by(event.y()) + " " + (doubleClick ? 2 : 1));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pressed) {
            pressed = false;
            runtime.send(BrowserProtocol.MOUSE_UP + " " + event.button() + " " + bx(event.x()) + " " + by(event.y()));
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pressed) {
            runtime.send(BrowserProtocol.MOUSE_MOVE + " " + bx(event.x()) + " " + by(event.y()));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!inView(x, y)) return false;
        runtime.send(BrowserProtocol.WHEEL + " " + bx(x) + " " + by(y) + " " + scrollX + " " + scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == ESCAPE) {
            long now = System.currentTimeMillis();
            if (now - lastEscape < DOUBLE_PRESS_MS) {
                this.onClose();
                return true;
            }
            lastEscape = now;
        }
        runtime.send(BrowserProtocol.KEY_DOWN + " " + event.key() + " " + event.modifiers() + " " + event.scancode());
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        runtime.send(BrowserProtocol.KEY_UP + " " + event.key() + " " + event.modifiers() + " " + event.scancode());
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        runtime.send(BrowserProtocol.CHAR + " " + event.codepoint() + " 0");
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        runtime.blank();
        if (texture != null) {
            minecraft.getTextureManager().release(TEXTURE_ID);
            texture = null;
        }
        super.removed();
    }
}
