package net.pr1nted.openarcade.browser;

import net.pr1nted.openarcade.browser.api.BrowserProtocol;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * The helper, driven the way the mod drives it, with nothing of Minecraft around it:
 * start the jar, load a page, and check that every part of the protocol does what it
 * says. The page reports what it received through its title (an event) and its
 * colour (a frame), so a pass needs both directions to work.
 *
 * <pre>java HelperSmoke &lt;openarcade-browser.jar&gt; &lt;install dir&gt;</pre>
 *
 * Exits 0 on a pass; anything else prints what did not happen and exits 1.
 */
public final class HelperSmoke {
    private HelperSmoke() {}

    private static final String PAGE = "<!doctype html><html><body style='margin:0;height:100vh;background:#ff0000'>"
            + "<input id='box' style='position:absolute;left:0;top:0;width:10px'>"
            + "<script>"
            + "document.title='loaded';"
            + "document.body.addEventListener('click',function(e){document.body.style.background='#00ff00';"
            + "document.title='clicked '+e.clientX+' '+e.clientY;document.getElementById('box').focus();});"
            + "document.addEventListener('keydown',function(e){document.title='key '+e.keyCode;if(e.keyCode===65){document.body.style.background='#0000ff';}});"
            + "document.getElementById('box').addEventListener('input',function(e){document.title='typed '+e.target.value;});"
            + "window.addEventListener('resize',function(){document.title='size '+innerWidth+'x'+innerHeight;});"
            + "</script></body></html>";

    private static final List<String> events = new CopyOnWriteArrayList<>();
    private static MappedByteBuffer frames;

    public static void main(String[] args) throws Exception {
        File jar = new File(args[0]);
        File install = new File(args[1]);
        File frameFile = File.createTempFile("openarcade-smoke", ".bin");
        frameFile.deleteOnExit();
        try (RandomAccessFile raf = new RandomAccessFile(frameFile, "rw")) {
            raf.setLength(BrowserProtocol.fileBytes());
            frames = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, BrowserProtocol.fileBytes());
        }
        frames.order(ByteOrder.LITTLE_ENDIAN);

        // The same command line BrowserRuntime builds.
        List<String> cmd = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        cmd.add(new File(System.getProperty("java.home"), windows ? "bin/java.exe" : "bin/java").getAbsolutePath());
        if (!System.getProperty("java.specification.version").startsWith("1.")) {
            for (String pkg : new String[]{"sun.awt", "sun.lwawt", "sun.lwawt.macosx", "java.awt", "java.awt.peer", "java.awt.event"}) {
                cmd.add("--add-opens");
                cmd.add("java.desktop/" + pkg + "=ALL-UNNAMED");
            }
        }
        cmd.add("-Xmx256m");
        cmd.add("-jar");
        cmd.add(jar.getAbsolutePath());
        cmd.add("--install");
        cmd.add(install.getAbsolutePath());
        cmd.add("--frames");
        cmd.add(frameFile.getAbsolutePath());
        System.out.println("[smoke] Java " + System.getProperty("java.version") + " on " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        final Process helper = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.INHERIT).start();

        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(helper.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = in.readLine()) != null) {
                    events.add(line);
                    if (!line.startsWith(BrowserProtocol.PROGRESS + " ") || line.endsWith("0")) System.out.println("[event] " + line);
                }
            } catch (Exception ignored) {
                // the helper exited; checked below
            }
        });
        reader.setDaemon(true);
        reader.start();
        Writer commands = new OutputStreamWriter(helper.getOutputStream(), "UTF-8");

        try {
            expect("the helper is ready (Chromium downloaded and started)", 15 * 60, () -> events.contains(BrowserProtocol.READY), helper);

            String url = "data:text/html;charset=utf-8," + URLEncoder.encode(PAGE, "UTF-8").replace("+", "%20");
            send(commands, BrowserProtocol.OPEN + " 800 600 " + url);
            expect("the page loads and says so in its title", 60, () -> events.contains(BrowserProtocol.TITLE + " loaded"), helper);
            expect("an 800x600 red frame arrives in shared memory", 60, () -> frameIs(800, 600, 255, 0, 0), helper);

            send(commands, BrowserProtocol.MOUSE_MOVE + " 400 300");
            send(commands, BrowserProtocol.MOUSE_DOWN + " 0 400 300 1");
            Thread.sleep(80);
            send(commands, BrowserProtocol.MOUSE_UP + " 0 400 300");
            expect("a click at 400,300 reaches the page there", 20, () -> events.contains(BrowserProtocol.TITLE + " clicked 400 300"), helper);
            expect("the frame turns green", 20, () -> frameIs(800, 600, 0, 255, 0), helper);

            send(commands, BrowserProtocol.CHAR + " 120 0");
            expect("a typed character lands in the focused text box", 20, () -> events.contains(BrowserProtocol.TITLE + " typed x"), helper);

            // GLFW key, modifiers, and the native scancode GLFW reports on Windows (A is 0x1E).
            send(commands, BrowserProtocol.KEY_DOWN + " 65 0 30");
            send(commands, BrowserProtocol.KEY_UP + " 65 0 30");
            expect("a key press (GLFW A) reaches the page as keyCode 65", 20, () -> events.contains(BrowserProtocol.TITLE + " key 65"), helper);
            expect("the frame turns blue", 20, () -> frameIs(800, 600, 0, 0, 255), helper);

            // Left arrow: GLFW 263, Windows scancode 0x14B (extended).
            send(commands, BrowserProtocol.KEY_DOWN + " 263 0 331");
            send(commands, BrowserProtocol.KEY_UP + " 263 0 331");
            expect("an arrow key reaches the page as keyCode 37", 20, () -> events.contains(BrowserProtocol.TITLE + " key 37"), helper);

            send(commands, BrowserProtocol.RESIZE + " 640 480");
            expect("a resize reaches the page", 20, () -> events.contains(BrowserProtocol.TITLE + " size 640x480"), helper);
            expect("frames arrive at the new size", 20, () -> frameIs(640, 480, 0, 0, 255), helper);

            send(commands, BrowserProtocol.QUIT);
            if (!helper.waitFor(60, TimeUnit.SECONDS)) fail("the helper did not exit within 60 s of quit");
            System.out.println("[smoke] the helper exited with " + helper.exitValue());
            if (helper.exitValue() != 0) fail("the helper exited with " + helper.exitValue());
            System.out.println("[smoke] PASS");
        } finally {
            helper.destroyForcibly();
        }
        System.exit(0);
    }

    interface Check {
        boolean ok() throws Exception;
    }

    private static void expect(String what, int seconds, Check check, Process helper) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (check.ok()) {
                System.out.println("[smoke] ok: " + what);
                return;
            }
            if (!helper.isAlive()) fail(what + ": the helper exited with " + helper.exitValue());
            Thread.sleep(50);
        }
        fail(what + ": not within " + seconds + " s");
    }

    /** The newest whole frame is this size and this colour in the middle. */
    private static boolean frameIs(int width, int height, int r, int g, int b) {
        if (frames.getInt(BrowserProtocol.OFFSET_MAGIC) != BrowserProtocol.MAGIC) return false;
        long sequence = frames.getLong(BrowserProtocol.OFFSET_SEQUENCE);
        if ((sequence & 1) != 0) return false;
        if (frames.getInt(BrowserProtocol.OFFSET_WIDTH) != width || frames.getInt(BrowserProtocol.OFFSET_HEIGHT) != height) return false;
        int at = BrowserProtocol.HEADER_BYTES + ((height / 2) * width + width / 2) * 4;
        int pr = frames.get(at) & 0xFF, pg = frames.get(at + 1) & 0xFF, pb = frames.get(at + 2) & 0xFF;
        boolean match = Math.abs(pr - r) < 8 && Math.abs(pg - g) < 8 && Math.abs(pb - b) < 8;
        return match && frames.getLong(BrowserProtocol.OFFSET_SEQUENCE) == sequence;
    }

    private static void send(Writer commands, String line) throws Exception {
        commands.write(line);
        commands.write('\n');
        commands.flush();
    }

    private static void fail(String message) {
        System.out.println("[smoke] FAIL: " + message);
        System.out.println("[smoke] events so far: " + String.join(" | ", events).toLowerCase(Locale.ROOT).replaceAll("progress [^|]*\\| ", ""));
        System.exit(1);
    }
}
