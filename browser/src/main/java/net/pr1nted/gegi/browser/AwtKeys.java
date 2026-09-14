package net.pr1nted.gegi.browser;

import net.pr1nted.gegi.browser.api.BrowserProtocol;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * GLFW key codes (what every Minecraft since 1.13 reports) to AWT key codes (what
 * java-cef turns into Chromium key events). Printable keys share codes in both; the
 * rest are listed. The 1.12.2 build, on LWJGL 2, converts its own codes to GLFW first.
 */
public final class AwtKeys {
    private AwtKeys() {}

    public static int modifiers(int glfw) {
        int awt = 0;
        if ((glfw & BrowserProtocol.MOD_SHIFT) != 0) awt |= InputEvent.SHIFT_DOWN_MASK;
        if ((glfw & BrowserProtocol.MOD_CONTROL) != 0) awt |= InputEvent.CTRL_DOWN_MASK;
        if ((glfw & BrowserProtocol.MOD_ALT) != 0) awt |= InputEvent.ALT_DOWN_MASK;
        if ((glfw & BrowserProtocol.MOD_SUPER) != 0) awt |= InputEvent.META_DOWN_MASK;
        return awt;
    }

    public static int location(int glfwKey) {
        if (glfwKey >= 320 && glfwKey <= 336) return KeyEvent.KEY_LOCATION_NUMPAD;
        if (glfwKey == 340 || glfwKey == 341 || glfwKey == 342 || glfwKey == 343) return KeyEvent.KEY_LOCATION_LEFT;
        if (glfwKey == 344 || glfwKey == 345 || glfwKey == 346 || glfwKey == 347) return KeyEvent.KEY_LOCATION_RIGHT;
        return KeyEvent.KEY_LOCATION_STANDARD;
    }

    /** The character a key types that the game does not report as typed text. */
    public static char controlChar(int glfwKey) {
        switch (glfwKey) {
            case 257: case 335: return '\r';
            case 258: return '\t';
            case 259: return '\b';
            default: return KeyEvent.CHAR_UNDEFINED;
        }
    }

    /**
     * The character a key types without Shift, on a US layout: java-cef on macOS and
     * Linux works out which key was pressed from it, and drops a key event without one.
     * Keys that type nothing (F1, Home...) have none, and are not seen there.
     */
    public static char keyChar(int glfwKey) {
        if (glfwKey >= 65 && glfwKey <= 90) return (char) (glfwKey + ('a' - 'A'));
        if (glfwKey == 32 || (glfwKey >= 48 && glfwKey <= 57)) return (char) glfwKey;
        if (glfwKey >= 320 && glfwKey <= 329) return (char) ('0' + glfwKey - 320);
        switch (glfwKey) {
            case 39: case 44: case 45: case 46: case 47: case 59: case 61: case 91: case 92: case 93: case 96:
                return (char) glfwKey;
            case 257: case 335: return '\r';
            case 258: return '\t';
            case 259: return '\b';
            case 256: return (char) 27;
            default: return KeyEvent.CHAR_UNDEFINED;
        }
    }

    public static int keyCode(int glfwKey) {
        if (glfwKey == 32 || (glfwKey >= 48 && glfwKey <= 57) || (glfwKey >= 65 && glfwKey <= 90)) return glfwKey;
        if (glfwKey >= 290 && glfwKey <= 301) return KeyEvent.VK_F1 + (glfwKey - 290);
        if (glfwKey >= 320 && glfwKey <= 329) return KeyEvent.VK_NUMPAD0 + (glfwKey - 320);
        switch (glfwKey) {
            case 39: return KeyEvent.VK_QUOTE;
            case 44: return KeyEvent.VK_COMMA;
            case 45: return KeyEvent.VK_MINUS;
            case 46: return KeyEvent.VK_PERIOD;
            case 47: return KeyEvent.VK_SLASH;
            case 59: return KeyEvent.VK_SEMICOLON;
            case 61: return KeyEvent.VK_EQUALS;
            case 91: return KeyEvent.VK_OPEN_BRACKET;
            case 92: return KeyEvent.VK_BACK_SLASH;
            case 93: return KeyEvent.VK_CLOSE_BRACKET;
            case 96: return KeyEvent.VK_BACK_QUOTE;
            case 256: return KeyEvent.VK_ESCAPE;
            case 257: case 335: return KeyEvent.VK_ENTER;
            case 258: return KeyEvent.VK_TAB;
            case 259: return KeyEvent.VK_BACK_SPACE;
            case 260: return KeyEvent.VK_INSERT;
            case 261: return KeyEvent.VK_DELETE;
            case 262: return KeyEvent.VK_RIGHT;
            case 263: return KeyEvent.VK_LEFT;
            case 264: return KeyEvent.VK_DOWN;
            case 265: return KeyEvent.VK_UP;
            case 266: return KeyEvent.VK_PAGE_UP;
            case 267: return KeyEvent.VK_PAGE_DOWN;
            case 268: return KeyEvent.VK_HOME;
            case 269: return KeyEvent.VK_END;
            case 280: return KeyEvent.VK_CAPS_LOCK;
            case 281: return KeyEvent.VK_SCROLL_LOCK;
            case 282: return KeyEvent.VK_NUM_LOCK;
            case 283: return KeyEvent.VK_PRINTSCREEN;
            case 284: return KeyEvent.VK_PAUSE;
            case 330: return KeyEvent.VK_DECIMAL;
            case 331: return KeyEvent.VK_DIVIDE;
            case 332: return KeyEvent.VK_MULTIPLY;
            case 333: return KeyEvent.VK_SUBTRACT;
            case 334: return KeyEvent.VK_ADD;
            case 336: return KeyEvent.VK_EQUALS;
            case 340: case 344: return KeyEvent.VK_SHIFT;
            case 341: case 345: return KeyEvent.VK_CONTROL;
            case 342: case 346: return KeyEvent.VK_ALT;
            case 343: case 347: return KeyEvent.VK_META;
            default: return KeyEvent.VK_UNDEFINED;
        }
    }
}
