package net.pr1nted.gegi.browser.api;

/**
 * What the mod and the Chromium helper process agree on. Plain Java 8 with no
 * Minecraft types, so the same class compiles into the helper and into the mod on
 * every Minecraft version.
 *
 * <h2>Frames: a memory-mapped file</h2>
 * The helper paints each frame into a file both processes map. A 32-byte header,
 * then {@code width * height * 4} bytes of RGBA, top row first:
 * <pre>
 *   0  int  magic     OAFR
 *   4  int  version
 *   8  long sequence  odd while the helper is writing, even when a frame is whole
 *  16  int  width
 *  20  int  height
 * </pre>
 * A reader copies only when the sequence is even and unchanged across the copy.
 * One writer, so this is all the locking there is.
 *
 * <h2>Commands and events: lines on stdin and stdout</h2>
 * The mod writes commands to the helper's stdin; the helper writes events to its
 * stdout. Space-separated fields; text fields (URLs, titles) always come last.
 */
public final class BrowserProtocol {
    private BrowserProtocol() {}

    public static final int MAGIC = 0x4F414652; // "OAFR"
    public static final int VERSION = 1;
    public static final int HEADER_BYTES = 32;
    public static final int OFFSET_MAGIC = 0;
    public static final int OFFSET_VERSION = 4;
    public static final int OFFSET_SEQUENCE = 8;
    public static final int OFFSET_WIDTH = 16;
    public static final int OFFSET_HEIGHT = 20;

    public static final int MAX_WIDTH = 3840;
    public static final int MAX_HEIGHT = 2160;

    public static long fileBytes() {
        return HEADER_BYTES + (long) MAX_WIDTH * MAX_HEIGHT * 4;
    }

    // ---- commands, mod to helper

    /** {@code open <width> <height> <url>} */
    public static final String OPEN = "open";
    /** {@code resize <width> <height>} */
    public static final String RESIZE = "resize";
    /** {@code move <x> <y>} in browser pixels */
    public static final String MOUSE_MOVE = "move";
    /** {@code down <button> <x> <y> <clicks>}, button 0 left, 1 right, 2 middle */
    public static final String MOUSE_DOWN = "down";
    /** {@code up <button> <x> <y>} */
    public static final String MOUSE_UP = "up";
    /** {@code wheel <x> <y> <deltaX> <deltaY>}, deltas in notches */
    public static final String WHEEL = "wheel";
    /**
     * {@code keydown <glfwKey> <modifiers> [scancode]}, modifiers as GLFW bits, scancode as
     * GLFW reports it (the platform's own; Windows needs it to tell keys apart)
     */
    public static final String KEY_DOWN = "keydown";
    /** {@code keyup <glfwKey> <modifiers> [scancode]} */
    public static final String KEY_UP = "keyup";
    /** {@code char <codepoint> <modifiers>} */
    public static final String CHAR = "char";
    /** {@code focus <0|1>} */
    public static final String FOCUS = "focus";
    /** {@code quit} */
    public static final String QUIT = "quit";

    // ---- events, helper to mod

    /** {@code progress <state> <percent>} while Chromium downloads and unpacks */
    public static final String PROGRESS = "progress";
    /** {@code ready}: Chromium is running and commands are accepted */
    public static final String READY = "ready";
    /** {@code loading <0|1>} */
    public static final String LOADING = "loading";
    /** {@code title <text>} */
    public static final String TITLE = "title";
    /** {@code error <text>} */
    public static final String ERROR = "error";

    /** GLFW modifier bits, used on both sides. */
    public static final int MOD_SHIFT = 0x0001;
    public static final int MOD_CONTROL = 0x0002;
    public static final int MOD_ALT = 0x0004;
    public static final int MOD_SUPER = 0x0008;
}
