package net.pr1nted.gegi.client;

import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * 1.12.2 runs on LWJGL 2, whose key codes are DirectInput's; the Chromium helper speaks
 * GLFW's, as every later Minecraft does. This is the table between them.
 */
final class Keys {
    private Keys() {}

    private static final Map<Integer, Integer> GLFW = new HashMap<>();

    private static void map(int lwjgl, int glfw) {
        GLFW.put(lwjgl, glfw);
    }

    static {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int[] letterKeys = {Keyboard.KEY_A, Keyboard.KEY_B, Keyboard.KEY_C, Keyboard.KEY_D, Keyboard.KEY_E, Keyboard.KEY_F,
                Keyboard.KEY_G, Keyboard.KEY_H, Keyboard.KEY_I, Keyboard.KEY_J, Keyboard.KEY_K, Keyboard.KEY_L, Keyboard.KEY_M,
                Keyboard.KEY_N, Keyboard.KEY_O, Keyboard.KEY_P, Keyboard.KEY_Q, Keyboard.KEY_R, Keyboard.KEY_S, Keyboard.KEY_T,
                Keyboard.KEY_U, Keyboard.KEY_V, Keyboard.KEY_W, Keyboard.KEY_X, Keyboard.KEY_Y, Keyboard.KEY_Z};
        for (int i = 0; i < letterKeys.length; i++) map(letterKeys[i], letters.charAt(i));
        int[] digitKeys = {Keyboard.KEY_0, Keyboard.KEY_1, Keyboard.KEY_2, Keyboard.KEY_3, Keyboard.KEY_4,
                Keyboard.KEY_5, Keyboard.KEY_6, Keyboard.KEY_7, Keyboard.KEY_8, Keyboard.KEY_9};
        for (int i = 0; i < digitKeys.length; i++) map(digitKeys[i], '0' + i);
        int[] numpadKeys = {Keyboard.KEY_NUMPAD0, Keyboard.KEY_NUMPAD1, Keyboard.KEY_NUMPAD2, Keyboard.KEY_NUMPAD3, Keyboard.KEY_NUMPAD4,
                Keyboard.KEY_NUMPAD5, Keyboard.KEY_NUMPAD6, Keyboard.KEY_NUMPAD7, Keyboard.KEY_NUMPAD8, Keyboard.KEY_NUMPAD9};
        for (int i = 0; i < numpadKeys.length; i++) map(numpadKeys[i], 320 + i);
        int[] functionKeys = {Keyboard.KEY_F1, Keyboard.KEY_F2, Keyboard.KEY_F3, Keyboard.KEY_F4, Keyboard.KEY_F5, Keyboard.KEY_F6,
                Keyboard.KEY_F7, Keyboard.KEY_F8, Keyboard.KEY_F9, Keyboard.KEY_F10, Keyboard.KEY_F11, Keyboard.KEY_F12};
        for (int i = 0; i < functionKeys.length; i++) map(functionKeys[i], 290 + i);

        map(Keyboard.KEY_SPACE, 32);
        map(Keyboard.KEY_APOSTROPHE, 39);
        map(Keyboard.KEY_COMMA, 44);
        map(Keyboard.KEY_MINUS, 45);
        map(Keyboard.KEY_PERIOD, 46);
        map(Keyboard.KEY_SLASH, 47);
        map(Keyboard.KEY_SEMICOLON, 59);
        map(Keyboard.KEY_EQUALS, 61);
        map(Keyboard.KEY_LBRACKET, 91);
        map(Keyboard.KEY_BACKSLASH, 92);
        map(Keyboard.KEY_RBRACKET, 93);
        map(Keyboard.KEY_GRAVE, 96);
        map(Keyboard.KEY_ESCAPE, 256);
        map(Keyboard.KEY_RETURN, 257);
        map(Keyboard.KEY_TAB, 258);
        map(Keyboard.KEY_BACK, 259);
        map(Keyboard.KEY_INSERT, 260);
        map(Keyboard.KEY_DELETE, 261);
        map(Keyboard.KEY_RIGHT, 262);
        map(Keyboard.KEY_LEFT, 263);
        map(Keyboard.KEY_DOWN, 264);
        map(Keyboard.KEY_UP, 265);
        map(Keyboard.KEY_PRIOR, 266);
        map(Keyboard.KEY_NEXT, 267);
        map(Keyboard.KEY_HOME, 268);
        map(Keyboard.KEY_END, 269);
        map(Keyboard.KEY_CAPITAL, 280);
        map(Keyboard.KEY_SCROLL, 281);
        map(Keyboard.KEY_NUMLOCK, 282);
        map(Keyboard.KEY_PAUSE, 284);
        map(Keyboard.KEY_DECIMAL, 330);
        map(Keyboard.KEY_DIVIDE, 331);
        map(Keyboard.KEY_MULTIPLY, 332);
        map(Keyboard.KEY_SUBTRACT, 333);
        map(Keyboard.KEY_ADD, 334);
        map(Keyboard.KEY_NUMPADENTER, 335);
        map(Keyboard.KEY_NUMPADEQUALS, 336);
        map(Keyboard.KEY_LSHIFT, 340);
        map(Keyboard.KEY_LCONTROL, 341);
        map(Keyboard.KEY_LMENU, 342);
        map(Keyboard.KEY_LMETA, 343);
        map(Keyboard.KEY_RSHIFT, 344);
        map(Keyboard.KEY_RCONTROL, 345);
        map(Keyboard.KEY_RMENU, 346);
        map(Keyboard.KEY_RMETA, 347);
    }

    /** The GLFW key for an LWJGL 2 key, or 0 for one GLFW has no name for. */
    static int glfw(int lwjglKey) {
        Integer key = GLFW.get(lwjglKey);
        return key == null ? 0 : key;
    }

    /** The native scancode GLFW would report on Windows: a DirectInput code is one, with the extended bit as 0x80. */
    static int scancode(int lwjglKey) {
        return lwjglKey & 0x7F;
    }

    /** GLFW's modifier bits for the keys held now. */
    static int modifiers() {
        int bits = 0;
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) bits |= 1;
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) bits |= 2;
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) bits |= 4;
        if (Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA)) bits |= 8;
        return bits;
    }
}
