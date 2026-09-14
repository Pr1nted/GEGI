package net.pr1nted.openarcade.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.pr1nted.openarcade.Constants;
import net.pr1nted.openarcade.browser.api.BrowserProtocol;
import net.pr1nted.openarcade.catalog.GameEntry;
import net.pr1nted.openarcade.runtime.BrowserRuntime;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.nio.ByteBuffer;

/**
 * A web game, playing inside Minecraft 1.12.2. Chromium runs in the helper process;
 * this screen shows its frames as a texture and hands it every key, click and scroll.
 *
 * <p>Escape belongs to the game (plenty of web games use it), so a single press goes
 * to the page and a double press leaves. 1.12.2 runs on LWJGL 2: keys are translated
 * to GLFW's codes by {@link Keys}, and a texture's pixels are ARGB ints, so each frame
 * is converted from the helper's RGBA bytes before it is uploaded.
 */
public final class GameScreen extends GuiScreen {

    static final int BAR = 22;
    private static final long DOUBLE_PRESS_MS = 600;
    private static final ResourceLocation TEXTURE_ID = new ResourceLocation(Constants.MOD_ID, "game_view");

    private final GuiScreen parent;
    private final GameEntry game;
    private final BrowserRuntime runtime = BrowserRuntime.get();

    private DynamicTexture texture;
    private ByteBuffer frame;
    private int textureWidth;
    private int textureHeight;
    private boolean opened;
    private boolean pressed;
    private long lastEscape;
    private long lastClick;
    private int browserWidth;
    private int browserHeight;
    private int framesShown;
    private int pointerX = -1;
    private int pointerY = -1;

    private final BrowserRuntime.FrameTarget target = new BrowserRuntime.FrameTarget() {
        @Override
        public ByteBuffer prepare(int width, int height) {
            if (texture == null || width != textureWidth || height != textureHeight) {
                if (texture != null) mc.getTextureManager().deleteTexture(TEXTURE_ID);
                texture = new DynamicTexture(width, height);
                mc.getTextureManager().loadTexture(TEXTURE_ID, texture);
                frame = ByteBuffer.allocate(width * height * 4);
                textureWidth = width;
                textureHeight = height;
            }
            return frame;
        }

        @Override
        public void done() {
            byte[] rgba = frame.array();
            int[] argb = texture.getTextureData();
            for (int i = 0, p = 0; i < argb.length; i++, p += 4) {
                argb[i] = (rgba[p + 3] & 0xFF) << 24 | (rgba[p] & 0xFF) << 16 | (rgba[p + 1] & 0xFF) << 8 | (rgba[p + 2] & 0xFF);
            }
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
    public void initGui() {
        this.buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        this.addButton(Buttons.of(2, 1, 60, 20, Lang.string("openarcade.game.back"), this::close));
        this.addButton(Buttons.of(this.width - 112, 1, 110, 20, Lang.string("openarcade.game.browser"), () -> {
            ArcadeClient.openLink(game.url());
            this.close();
        }));

        int scale = new ScaledResolution(this.mc).getScaleFactor();
        browserWidth = Math.max(64, Math.min(BrowserProtocol.MAX_WIDTH, this.width * scale));
        browserHeight = Math.max(64, Math.min(BrowserProtocol.MAX_HEIGHT, (this.height - BAR) * scale));
        if (!opened) {
            runtime.open(browserWidth, browserHeight, game.url());
            opened = true;
        } else {
            runtime.resize(browserWidth, browserHeight);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (mouseX != pointerX || mouseY != pointerY) {
            pointerX = mouseX;
            pointerY = mouseY;
            if (inView(mouseX, mouseY) && !pressed) runtime.send(BrowserProtocol.MOUSE_MOVE + " " + bx(mouseX) + " " + by(mouseY));
        }
        runtime.pollFrame(target);
        if (texture != null) {
            this.mc.getTextureManager().bindTexture(TEXTURE_ID);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            drawModalRectWithCustomSizedTexture(0, BAR, 0, 0, this.width, this.height - BAR, this.width, this.height - BAR);
        } else {
            drawRect(0, BAR, this.width, this.height, 0xFF030806);
            this.drawCenteredString(this.fontRenderer, statusLine(), this.width / 2, this.height / 2, 0xFF8FB9A2);
        }
        drawRect(0, 0, this.width, BAR, 0xE0030806);
        super.drawScreen(mouseX, mouseY, partialTicks);
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

    /** A click in the view; the dev screenshot calls it too. */
    void click(double x, double y, int button) {
        pressed = true;
        // 1.12.2 does not report double clicks; count them here, as the game's own widgets do.
        long now = System.currentTimeMillis();
        boolean doubleClick = now - lastClick < 250;
        lastClick = now;
        runtime.send(BrowserProtocol.MOUSE_DOWN + " " + button + " " + bx(x) + " " + by(y) + " " + (doubleClick ? 2 : 1));
    }

    void release(double x, double y, int button) {
        if (!pressed) return;
        pressed = false;
        runtime.send(BrowserProtocol.MOUSE_UP + " " + button + " " + bx(x) + " " + by(y));
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (y < BAR) {
            super.mouseClicked(x, y, button);
        } else if (inView(x, y)) {
            click(x, y, button);
        }
    }

    @Override
    protected void mouseReleased(int x, int y, int button) {
        if (pressed) {
            release(x, y, button);
        } else {
            super.mouseReleased(x, y, button);
        }
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long heldMs) {
        if (pressed) runtime.send(BrowserProtocol.MOUSE_MOVE + " " + bx(x) + " " + by(y));
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        if (inView(x, y)) runtime.send(BrowserProtocol.WHEEL + " " + bx(x) + " " + by(y) + " 0 " + Math.signum(wheel));
    }

    /**
     * Every key down and up goes to the page, not only the presses that type something,
     * which is all GuiScreen.keyTyped would see.
     */
    @Override
    public void handleKeyboardInput() {
        int key = Keyboard.getEventKey();
        char character = Keyboard.getEventCharacter();
        boolean down = Keyboard.getEventKeyState();
        if (down && key == Keyboard.KEY_ESCAPE && !Keyboard.isRepeatEvent()) {
            long now = System.currentTimeMillis();
            if (now - lastEscape < DOUBLE_PRESS_MS) {
                this.close();
                return;
            }
            lastEscape = now;
        }
        int glfw = Keys.glfw(key);
        if (glfw != 0) {
            runtime.send((down ? BrowserProtocol.KEY_DOWN : BrowserProtocol.KEY_UP) + " " + glfw + " " + Keys.modifiers() + " " + Keys.scancode(key));
        }
        if (character >= ' ' && character != 127 && (down || key == Keyboard.KEY_NONE)) {
            runtime.send(BrowserProtocol.CHAR + " " + (int) character + " 0");
        }
        this.mc.dispatchKeypresses();
    }

    public void close() {
        this.mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        runtime.blank();
        if (texture != null) {
            mc.getTextureManager().deleteTexture(TEXTURE_ID);
            texture = null;
        }
        super.onGuiClosed();
    }
}
