package org.cef.browser;

import net.pr1nted.gegi.browser.AwtKeys;
import net.pr1nted.gegi.browser.FrameSink;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A windowless Chromium browser with no AWT window and no JOGL. CEF paints BGRA
 * frames into {@link #onPaint}, which go straight to a {@link FrameSink}; input
 * arrives as plain numbers and is turned into the AWT events java-cef expects.
 *
 * <p>java-cef's own CefBrowserOsr draws into a JOGL GLCanvas, which cannot live
 * inside a game's window. This lives in package org.cef.browser because
 * CefBrowser_N is package-private; that split package is only legal on a plain
 * classpath, which is why it runs in the helper process and never inside the game.
 */
public final class ArcadeOsrBrowser extends CefBrowser_N implements CefRenderHandler {

    /** java-cef wants an event source; nothing is ever shown. */
    private static final Component EVENT_SOURCE = new Canvas();

    private final boolean transparent;
    private final FrameSink sink;
    private volatile int width;
    private volatile int height;
    private int buttonsDown;

    public ArcadeOsrBrowser(CefClient client, String url, boolean transparent, CefRequestContext context,
                            CefBrowserSettings settings, int width, int height, FrameSink sink) {
        super(client, url, context, null, null, settings);
        this.transparent = transparent;
        this.width = width;
        this.height = height;
        this.sink = sink;
    }

    @Override
    public void createImmediately() {
        // Window handle 0: a windowless browser has no parent window.
        createBrowser(getClient(), 0, getUrl(), true, transparent, null, getRequestContext());
    }

    @Override
    public Component getUIComponent() {
        return null;
    }

    @Override
    public CefRenderHandler getRenderHandler() {
        return this;
    }

    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url, CefRequestContext context,
                                                 CefBrowser_N parent, Point inspectAt) {
        return null;
    }

    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
        CompletableFuture<BufferedImage> none = new CompletableFuture<>();
        none.completeExceptionally(new UnsupportedOperationException("frames go to the game, not to images"));
        return none;
    }

    public void resize(int newWidth, int newHeight) {
        width = newWidth;
        height = newHeight;
        wasResized(newWidth, newHeight);
    }

    // ---- input

    public void mouseMove(int x, int y) {
        int id = buttonsDown != 0 ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_MOVED;
        sendMouseEvent(new MouseEvent(EVENT_SOURCE, id, System.currentTimeMillis(), buttonsDown, x, y, 0, false, MouseEvent.NOBUTTON));
    }

    public void mouseButton(boolean down, int button, int x, int y, int clicks, int glfwModifiers) {
        int awtButton = button == 1 ? MouseEvent.BUTTON3 : button == 2 ? MouseEvent.BUTTON2 : MouseEvent.BUTTON1;
        int mask = button == 1 ? InputEvent.BUTTON3_DOWN_MASK : button == 2 ? InputEvent.BUTTON2_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
        if (down) buttonsDown |= mask;
        int modifiers = AwtKeys.modifiers(glfwModifiers) | buttonsDown;
        if (!down) buttonsDown &= ~mask;
        long now = System.currentTimeMillis();
        sendMouseEvent(new MouseEvent(EVENT_SOURCE, down ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED,
                now, modifiers, x, y, Math.max(1, clicks), button == 1 && down, awtButton));
        if (!down) {
            sendMouseEvent(new MouseEvent(EVENT_SOURCE, MouseEvent.MOUSE_CLICKED, now, modifiers, x, y, Math.max(1, clicks), false, awtButton));
        }
    }

    /**
     * Minecraft reports scrolling up as positive, and AWT's rotation is negative for up.
     * java-cef's macOS side flips the rotation once more on its way to Chromium, so there
     * the sign goes through unchanged (HelperSmoke checks the direction on every OS).
     */
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");

    /** Scroll left over from fractions of a notch, which trackpads send many of. */
    private double pendingWheel;

    public void wheel(int x, int y, double deltaY, int glfwModifiers) {
        pendingWheel += MAC ? deltaY : -deltaY;
        int rotation = (int) pendingWheel;
        if (rotation == 0) return;
        pendingWheel -= rotation;
        sendMouseWheelEvent(new MouseWheelEvent(EVENT_SOURCE, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                AwtKeys.modifiers(glfwModifiers), x, y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rotation));
    }

    /**
     * KeyEvent's private scancode. java-cef reads it on Windows to tell keys apart (and on
     * macOS and Linux reads the character instead). Null elsewhere, or if Java refuses.
     */
    private static final java.lang.reflect.Field SCANCODE = scancodeField();

    private static java.lang.reflect.Field scancodeField() {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) return null;
        try {
            java.lang.reflect.Field field = KeyEvent.class.getDeclaredField("scancode");
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            System.err.println("GEGI: cannot set KeyEvent.scancode (" + e + "); key presses will not reach pages");
            return null;
        }
    }

    /** A key going down or up. {@code scancode} is GLFW's native one (0 if unknown). */
    public void key(boolean down, int glfwKey, int glfwModifiers, int scancode) {
        int code = AwtKeys.keyCode(glfwKey);
        if (code == KeyEvent.VK_UNDEFINED) return;
        long now = System.currentTimeMillis();
        KeyEvent event = new KeyEvent(EVENT_SOURCE, down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED, now,
                AwtKeys.modifiers(glfwModifiers), code, AwtKeys.keyChar(glfwKey), AwtKeys.location(glfwKey));
        if (SCANCODE != null && scancode > 0) {
            try {
                // MapVirtualKey, which java-cef calls with it, wants the scancode without GLFW's extended-key bit.
                SCANCODE.setLong(event, scancode & 0xFF);
            } catch (IllegalAccessException ignored) {
                // scancodeField made it accessible
            }
        }
        sendKeyEvent(event);
        // Enter, Tab and Backspace also type a character in a text field; the game only
        // reports printable characters as typed, so these are typed here.
        char typed = AwtKeys.controlChar(glfwKey);
        if (down && typed != KeyEvent.CHAR_UNDEFINED) {
            sendKeyEvent(new KeyEvent(EVENT_SOURCE, KeyEvent.KEY_TYPED, now, AwtKeys.modifiers(glfwModifiers),
                    KeyEvent.VK_UNDEFINED, typed, KeyEvent.KEY_LOCATION_UNKNOWN));
        }
    }

    public void character(int codepoint, int glfwModifiers) {
        long now = System.currentTimeMillis();
        for (char c : Character.toChars(codepoint)) {
            sendKeyEvent(new KeyEvent(EVENT_SOURCE, KeyEvent.KEY_TYPED, now, AwtKeys.modifiers(glfwModifiers),
                    KeyEvent.VK_UNDEFINED, c, KeyEvent.KEY_LOCATION_UNKNOWN));
        }
    }

    // ---- CefRenderHandler

    @Override
    public Rectangle getViewRect(CefBrowser browser) {
        return new Rectangle(0, 0, width, height);
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        Rectangle bounds = new Rectangle(0, 0, width, height);
        screenInfo.Set(1.0, 32, 8, false, bounds, bounds);
        return true;
    }

    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
        return new Point(viewPoint);
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer,
                        int paintWidth, int paintHeight) {
        if (!popup) sink.frame(buffer, paintWidth, paintHeight);
    }

    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> listener) {
    }

    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> listener) {
    }

    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> listener) {
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        return false;
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {
    }
}
