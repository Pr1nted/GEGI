package net.pr1nted.gegi.runtime;

import net.pr1nted.gegi.Log;
import net.pr1nted.gegi.browser.api.BrowserProtocol;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * The Chromium helper, from the game's side: starts it, talks to it, reads its frames.
 *
 * <p>One helper for the whole game session, started the first time a game is played
 * and kept, so the second game does not wait for Chromium again. It runs on the
 * game's own Java, from a jar this mod carries, and exits when the game does: its
 * stdin is the game, and it quits when that closes.
 *
 * <p>Chromium itself (about 100 MB, 300 MB unpacked) is downloaded by the helper into
 * {@link #dataDir()}, once per player, shared by every Minecraft version and instance.
 *
 * <p>Plain Java 8 and nothing of Minecraft, so every version of the mod uses this one.
 */
public final class BrowserRuntime {

    public enum State { STOPPED, STARTING, DOWNLOADING, READY, FAILED }

    /** Receives a frame. */
    public interface FrameTarget {
        /** Room for width*height*4 RGBA bytes, from position 0. The frame is copied into it. */
        ByteBuffer prepare(int width, int height);

        /** The copy in the buffer is a whole frame: upload it. */
        void done();
    }

    private static BrowserRuntime instance;

    public static synchronized BrowserRuntime get() {
        if (instance == null) instance = new BrowserRuntime();
        return instance;
    }

    private volatile State state = State.STOPPED;
    private volatile int progress = -1;
    private volatile String title = "";
    private volatile boolean loading;
    private volatile String error = "";
    private volatile boolean gameStopping;

    private Process process;
    private Writer commands;
    private volatile MappedByteBuffer frames;
    private long lastSequence;
    private final List<String> pending = new ArrayList<>();

    private BrowserRuntime() {}

    public State state() {
        return state;
    }

    public int progress() {
        return progress;
    }

    public String title() {
        return title;
    }

    public boolean loading() {
        return loading;
    }

    public String error() {
        return error;
    }

    /** Where Chromium and its profile live: per player, not per game instance. */
    public static Path dataDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            return Paths.get(local != null ? local : home, "Gegi");
        }
        if (os.contains("mac")) return Paths.get(home, "Library", "Application Support", "Gegi");
        String xdg = System.getenv("XDG_DATA_HOME");
        return xdg != null && !xdg.isEmpty() ? Paths.get(xdg, "gegi") : Paths.get(home, ".local", "share", "gegi");
    }

    /** True once Chromium has been downloaded and has started at least once. */
    public static boolean chromiumInstalled() {
        return Files.exists(dataDir().resolve("chromium-ready"));
    }

    public synchronized void open(int width, int height, URI url) {
        sendOrQueue(BrowserProtocol.OPEN + " " + width + " " + height + " " + url);
    }

    public synchronized void resize(int width, int height) {
        sendOrQueue(BrowserProtocol.RESIZE + " " + width + " " + height);
    }

    /** Stops whatever is playing, keeping Chromium running for the next game. */
    public synchronized void blank() {
        if (state == State.READY) send(BrowserProtocol.OPEN + " 16 16 about:blank");
        pending.clear();
    }

    public synchronized void send(String line) {
        if (commands == null || state != State.READY) return;
        try {
            commands.write(line);
            commands.write('\n');
            commands.flush();
        } catch (IOException e) {
            fail("the Chromium helper stopped listening: " + e.getMessage());
        }
    }

    private void sendOrQueue(String line) {
        if (state == State.READY) {
            send(line);
            return;
        }
        String verb = line.substring(0, line.indexOf(' '));
        for (Iterator<String> it = pending.iterator(); it.hasNext(); ) {
            if (it.next().startsWith(verb + " ")) it.remove();
        }
        pending.add(line);
        if (state == State.STOPPED || state == State.FAILED) start();
    }

    private void start() {
        state = State.STARTING;
        error = "";
        progress = -1;
        try {
            Path dir = dataDir();
            Files.createDirectories(dir);
            Path jar = extractHelper(dir);

            File frameFile = File.createTempFile("gegi-frames", ".bin");
            frameFile.deleteOnExit();
            MappedByteBuffer map;
            try (RandomAccessFile raf = new RandomAccessFile(frameFile, "rw")) {
                raf.setLength(BrowserProtocol.fileBytes());
                map = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, BrowserProtocol.fileBytes());
            }
            map.order(ByteOrder.LITTLE_ENDIAN);
            frames = map;
            lastSequence = 0;

            List<String> cmd = new ArrayList<>();
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            cmd.add(Paths.get(System.getProperty("java.home"), "bin", windows ? "javaw.exe" : "java").toString());
            if (!System.getProperty("java.specification.version", "1.8").startsWith("1.")) {
                // java-cef reaches into AWT internals for native window handles, and the helper
                // sets KeyEvent's scancode for Windows (Java 9+ only).
                for (String pkg : new String[]{"sun.awt", "sun.lwawt", "sun.lwawt.macosx", "java.awt", "java.awt.peer", "java.awt.event"}) {
                    cmd.add("--add-opens");
                    cmd.add("java.desktop/" + pkg + "=ALL-UNNAMED");
                }
            }
            cmd.add("-Xmx256m");
            cmd.add("-jar");
            cmd.add(jar.toString());
            cmd.add("--install");
            cmd.add(dir.toString());
            cmd.add("--frames");
            cmd.add(frameFile.getAbsolutePath());

            ProcessBuilder builder = new ProcessBuilder(cmd).redirectError(dir.resolve("helper.log").toFile());
            process = builder.start();
            commands = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
            final Process started = process;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                gameStopping = true;
                started.destroy();
            }, "GEGI helper shutdown"));
            Thread reader = new Thread(() -> readEvents(started), "GEGI helper events");
            reader.setDaemon(true);
            reader.start();
            Log.info("Started the Chromium helper ({})", jar);
        } catch (IOException | RuntimeException e) {
            fail("could not start the Chromium helper: " + e);
        }
    }

    private static Path extractHelper(Path dir) throws IOException {
        Path jar = dir.resolve("gegi-browser.jar");
        try (InputStream in = BrowserRuntime.class.getResourceAsStream("/gegi-browser.jar")) {
            if (in == null) throw new IOException("gegi-browser.jar is missing from the mod jar");
            byte[] bytes = readAll(in);
            if (!Files.exists(jar) || Files.size(jar) != bytes.length) {
                Path tmp = Files.createTempFile(dir, "gegi-browser", ".jar");
                Files.write(tmp, bytes);
                Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return jar;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private void readEvents(Process source) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(source.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] f = line.split(" ", 3);
                switch (f[0]) {
                    case BrowserProtocol.PROGRESS:
                        state = State.DOWNLOADING;
                        progress = f.length > 2 ? parse(f[2]) : -1;
                        break;
                    case BrowserProtocol.READY:
                        onReady();
                        break;
                    case BrowserProtocol.TITLE:
                        title = f.length > 1 ? line.substring(f[0].length() + 1) : "";
                        break;
                    case BrowserProtocol.LOADING:
                        loading = f.length > 1 && f[1].equals("1");
                        break;
                    case BrowserProtocol.ERROR:
                        String message = line.length() > f[0].length() ? line.substring(f[0].length() + 1) : "unknown";
                        Log.warn("Chromium helper: {}", message);
                        if (state != State.READY) fail(message);
                        break;
                    default:
                        break;
                }
            }
        } catch (IOException ignored) {
            // the helper's stdout closed; handled below
        }
        synchronized (this) {
            if (process == source) {
                if (gameStopping) {
                    state = State.STOPPED;
                } else if (state != State.FAILED) {
                    fail("the Chromium helper exited (see " + dataDir().resolve("helper.log") + ")");
                }
                process = null;
                commands = null;
            }
        }
    }

    private synchronized void onReady() {
        state = State.READY;
        progress = -1;
        try {
            Files.write(dataDir().resolve("chromium-ready"), new byte[0]);
        } catch (IOException ignored) {
            // only a hint for the consent screen
        }
        List<String> queued = new ArrayList<>(pending);
        pending.clear();
        for (String line : queued) send(line);
    }

    private void fail(String message) {
        error = message;
        state = State.FAILED;
        Log.error("GEGI: {}", message);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Copies the newest whole frame into the target, if there is one it has not seen.
     * A frame the helper rewrote during the copy is dropped; the next one replaces it.
     * Call on the render thread.
     */
    public boolean pollFrame(FrameTarget target) {
        MappedByteBuffer map = frames;
        if (map == null || map.getInt(BrowserProtocol.OFFSET_MAGIC) != BrowserProtocol.MAGIC) return false;
        long sequence = map.getLong(BrowserProtocol.OFFSET_SEQUENCE);
        if (sequence == lastSequence || (sequence & 1) != 0) return false;
        int width = map.getInt(BrowserProtocol.OFFSET_WIDTH);
        int height = map.getInt(BrowserProtocol.OFFSET_HEIGHT);
        if (width <= 16 || height <= 16 || width > BrowserProtocol.MAX_WIDTH || height > BrowserProtocol.MAX_HEIGHT) return false;
        int bytes = width * height * 4;
        ByteBuffer destination = target.prepare(width, height);
        ByteBuffer source = map.duplicate();
        source.clear();
        source.position(BrowserProtocol.HEADER_BYTES);
        source.limit(BrowserProtocol.HEADER_BYTES + bytes);
        destination.clear();
        destination.put(source);
        if (map.getLong(BrowserProtocol.OFFSET_SEQUENCE) != sequence) return false;
        lastSequence = sequence;
        target.done();
        return true;
    }
}
