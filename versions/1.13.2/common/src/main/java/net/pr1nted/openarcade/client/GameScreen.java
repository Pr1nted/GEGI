package net.pr1nted.openarcade.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.pr1nted.openarcade.Constants;
import net.pr1nted.openarcade.browser.api.BrowserProtocol;
import net.pr1nted.openarcade.catalog.GameEntry;
import net.pr1nted.openarcade.runtime.BrowserRuntime;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * A web game, playing inside Minecraft 1.13.2. Chromium runs in the helper process;
 * this screen shows its frames as a texture and hands it every key, click and scroll.
 *
 * <p>Escape belongs to the game (plenty of web games use it), so a single press goes
 * to the page and a double press leaves. 1.13.2 tells a screen neither where the
 * mouse moved nor where a scroll happened, so both come from where render last saw
 * the pointer.
 */
public final class GameScreen extends GuiScreen {

    static final int BAR = 22;
    private static final int ESCAPE = 256;
    private static final long DOUBLE_PRESS_MS = 600;
    private static final ResourceLocation TEXTURE_ID = new ResourceLocation(Constants.MOD_ID, "game_view");

    private final GuiScreen parent;
    private final GameEntry game;
    private final BrowserRuntime runtime = BrowserRuntime.get();

    private DynamicTexture texture;
    private int textureWidth;
    private int textureHeight;
    private boolean opened;
    private boolean pressed;
    private long lastEscape;
    private long lastClick;
    private int browserWidth;
    private int browserHeight;
    private int framesShown;
    private double pointerX = -1;
    private double pointerY = -1;

    private final BrowserRuntime.FrameTarget target = new BrowserRuntime.FrameTarget() {
        @Override
        public ByteBuffer prepare(int width, int height) {
            if (texture == null || width != textureWidth || height != textureHeight) {
                if (texture != null) mc.getTextureManager().deleteTexture(TEXTURE_ID);
                texture = new DynamicTexture(width, height, false);
                mc.getTextureManager().loadTexture(TEXTURE_ID, texture);
                textureWidth = width;
                textureHeight = height;
            }
            return MemoryUtil.memByteBuffer(Pixels.address(texture.getTextureData()), width * height * 4);
        }

        @Override
        public void done() {
            texture.updateDynamicTexture();
            framesShown++;
        }
    };

    public GameScreen(GuiScreen parent, GameEntry game) {
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
    protected void initGui() {
        this.buttons.clear();
        this.children.clear();
        this.addButton(Buttons.of(2, 1, 60, 20, Lang.string("openarcade.game.back"), this::close));
        this.addButton(Buttons.of(this.width - 112, 1, 110, 20, Lang.string("openarcade.game.browser"), () -> {
            ArcadeClient.openLink(game.url());
            this.close();
        }));

        double scale = mc.mainWindow.getGuiScaleFactor();
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
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (mouseX != pointerX || mouseY != pointerY) {
            pointerX = mouseX;
            pointerY = mouseY;
            if (inView(mouseX, mouseY)) runtime.send(BrowserProtocol.MOUSE_MOVE + " " + bx(mouseX) + " " + by(mouseY));
        }
        runtime.pollFrame(target);
        if (texture != null) {
            this.mc.getTextureManager().bindTexture(TEXTURE_ID);
            drawModalRectWithCustomSizedTexture(0, BAR, 0, 0, this.width, this.height - BAR, this.width, this.height - BAR);
        } else {
            drawRect(0, BAR, this.width, this.height, 0xFF030806);
            this.drawCenteredString(this.fontRenderer, statusLine(), this.width / 2, this.height / 2, 0xFF8FB9A2);
        }
        drawRect(0, 0, this.width, BAR, 0xE0030806);
        super.render(mouseX, mouseY, partialTicks);
        String heading = runtime.title().isEmpty() ? game.title() : runtime.title();
        int room = this.width - 64 - 116 - 8;
        this.drawString(this.fontRenderer, this.fontRenderer.trimStringToWidth(heading, Math.max(20, room)), 68, 7, 0xFFE8FFF1);
        String hint = Lang.string("openarcade.game.leave_hint");
        if (this.fontRenderer.getStringWidth(heading) + this.fontRenderer.getStringWidth(hint) + 12 < room) {
            this.drawString(this.fontRenderer, hint, this.width - 116 - this.fontRenderer.getStringWidth(hint), 7, 0xFF8FB9A2);
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
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (!inView(x, y)) return false;
        pressed = true;
        // This Minecraft does not report double clicks; count them here, as the game's own widgets do.
        long now = System.currentTimeMillis();
        boolean doubleClick = now - lastClick < 250;
        lastClick = now;
        runtime.send(BrowserProtocol.MOUSE_DOWN + " " + button + " " + bx(x) + " " + by(y) + " " + (doubleClick ? 2 : 1));
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (pressed) {
            pressed = false;
            runtime.send(BrowserProtocol.MOUSE_UP + " " + button + " " + bx(x) + " " + by(y));
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (pressed) {
            runtime.send(BrowserProtocol.MOUSE_MOVE + " " + bx(x) + " " + by(y));
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double scrollY) {
        if (!inView(pointerX, pointerY)) return false;
        runtime.send(BrowserProtocol.WHEEL + " " + bx(pointerX) + " " + by(pointerY) + " 0 " + scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (key == ESCAPE) {
            long now = System.currentTimeMillis();
            if (now - lastEscape < DOUBLE_PRESS_MS) {
                this.close();
                return true;
            }
            lastEscape = now;
        }
        runtime.send(BrowserProtocol.KEY_DOWN + " " + key + " " + modifiers + " " + scancode);
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scancode, int modifiers) {
        runtime.send(BrowserProtocol.KEY_UP + " " + key + " " + modifiers + " " + scancode);
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        runtime.send(BrowserProtocol.CHAR + " " + (int) character + " 0");
        return true;
    }

    @Override
    public boolean allowCloseWithEscape() {
        return false;
    }

    @Override
    public void close() {
        this.mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        runtime.blank();
        if (texture != null) {
            mc.getTextureManager().deleteTexture(TEXTURE_ID);
            texture = null;
        }
        super.onGuiClosed();
    }
}
