package net.pr1nted.openarcade.browser;

import me.friwi.jcefmaven.CefAppBuilder;
import net.pr1nted.openarcade.browser.api.BrowserProtocol;
import org.cef.CefApp;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.ArcadeOsrBrowser;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * The Chromium helper's main. Started by the game as
 * {@code java -jar openarcade-browser.jar --install <dir> --frames <file>}.
 *
 * <p>Reads commands from stdin, writes events to stdout, paints frames into the
 * mapped file (see BrowserProtocol). Everything else that would print (CEF, jcef,
 * Java warnings) goes to stderr, so nothing can corrupt the event stream. It exits
 * when stdin closes, so it can never outlive the game that started it.
 */
public final class BrowserHost {
    private BrowserHost() {}

    private static PrintStream events;

    public static void main(String[] args) throws Exception {
        events = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        System.setOut(System.err);

        File install = null;
        File frames = null;
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (args[i].equals("--install")) install = new File(args[i + 1]);
            else if (args[i].equals("--frames")) frames = new File(args[i + 1]);
        }
        if (install == null || frames == null) {
            event(BrowserProtocol.ERROR, "usage: --install <dir> --frames <file>");
            System.exit(2);
        }

        CefApp app;
        try {
            CefAppBuilder builder = new CefAppBuilder();
            builder.setInstallDir(new File(install, "jcef-bundle"));
            builder.getCefSettings().windowless_rendering_enabled = true;
            // A lasting profile, so a player who logs in to itch.io stays logged in.
            builder.getCefSettings().root_cache_path = new File(install, "profile").getAbsolutePath();
            builder.getCefSettings().cache_path = new File(install, "profile/default").getAbsolutePath();
            builder.addJcefArgs("--autoplay-policy=no-user-gesture-required");
            builder.setProgressHandler((state, percent) -> event(BrowserProtocol.PROGRESS, state.name(), Math.round(Math.max(0, percent))));
            app = builder.build();
        } catch (Throwable t) {
            event(BrowserProtocol.ERROR, "Chromium could not start: " + t);
            System.exit(3);
            return;
        }

        CefClient client = app.createClient();
        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onTitleChange(CefBrowser browser, String title) {
                event(BrowserProtocol.TITLE, title == null ? "" : title.replace('\n', ' '));
            }
        });
        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
                event(BrowserProtocol.LOADING, isLoading ? 1 : 0);
            }
        });
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String targetUrl, String targetFrameName) {
                // There is one view in the game: a popup (itch.io's "Run game" in a new
                // window, for one) loads in it instead.
                browser.loadURL(targetUrl);
                return true;
            }
        });

        FrameWriter writer = new FrameWriter(frames);
        event(BrowserProtocol.READY);

        ArcadeOsrBrowser browser = null;
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            String[] f = line.trim().split(" ", 5);
            if (f.length == 0 || f[0].isEmpty()) continue;
            try {
                switch (f[0]) {
                    case BrowserProtocol.OPEN: {
                        int w = clampWidth(Integer.parseInt(f[1]));
                        int h = clampHeight(Integer.parseInt(f[2]));
                        String url = line.trim().split(" ", 4)[3];
                        if (browser == null) {
                            CefBrowserSettings settings = new CefBrowserSettings();
                            settings.windowless_frame_rate = 60;
                            browser = new ArcadeOsrBrowser(client, url, false, null, settings, w, h, writer);
                            browser.createImmediately();
                        } else {
                            browser.resize(w, h);
                            browser.loadURL(url);
                        }
                        browser.setFocus(true);
                        break;
                    }
                    case BrowserProtocol.RESIZE:
                        if (browser != null) browser.resize(clampWidth(Integer.parseInt(f[1])), clampHeight(Integer.parseInt(f[2])));
                        break;
                    case BrowserProtocol.MOUSE_MOVE:
                        if (browser != null) browser.mouseMove(Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                        break;
                    case BrowserProtocol.MOUSE_DOWN:
                    case BrowserProtocol.MOUSE_UP:
                        if (browser != null) {
                            boolean down = f[0].equals(BrowserProtocol.MOUSE_DOWN);
                            browser.mouseButton(down, Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                                    down && f.length > 4 ? Integer.parseInt(f[4]) : 1, 0);
                        }
                        break;
                    case BrowserProtocol.WHEEL:
                        if (browser != null) browser.wheel(Integer.parseInt(f[1]), Integer.parseInt(f[2]), Double.parseDouble(f[4]), 0);
                        break;
                    case BrowserProtocol.KEY_DOWN:
                    case BrowserProtocol.KEY_UP:
                        if (browser != null) browser.key(f[0].equals(BrowserProtocol.KEY_DOWN), Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                                f.length > 3 ? Integer.parseInt(f[3]) : 0);
                        break;
                    case BrowserProtocol.CHAR:
                        if (browser != null) browser.character(Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                        break;
                    case BrowserProtocol.FOCUS:
                        if (browser != null) browser.setFocus("1".equals(f[1]));
                        break;
                    case BrowserProtocol.QUIT:
                        shutdown(browser, client, app);
                        return;
                    default:
                        event(BrowserProtocol.ERROR, "unknown command " + f[0]);
                }
            } catch (RuntimeException e) {
                event(BrowserProtocol.ERROR, "bad command '" + line + "': " + e);
            }
        }
        // stdin closed: the game has gone, so this goes too.
        shutdown(browser, client, app);
    }

    private static int clampWidth(int w) {
        return Math.max(1, Math.min(BrowserProtocol.MAX_WIDTH, w));
    }

    private static int clampHeight(int h) {
        return Math.max(1, Math.min(BrowserProtocol.MAX_HEIGHT, h));
    }

    private static void shutdown(ArcadeOsrBrowser browser, CefClient client, CefApp app) {
        try {
            if (browser != null) browser.close(true);
            client.dispose();
            app.dispose();
        } finally {
            System.exit(0);
        }
    }

    static synchronized void event(String name, Object... fields) {
        StringBuilder line = new StringBuilder(name);
        for (Object field : fields) line.append(' ').append(field);
        events.println(line);
    }
}
