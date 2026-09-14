package net.pr1nted.gegi.catalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The mod's only network access: GET a page or an image, bounded in time and size.
 *
 * <p>HttpURLConnection rather than java.net.http, which Java 8 (Minecraft 1.12.2 to
 * 1.16.5) does not have. Requests run on a small pool of daemon threads, never on
 * the game's.
 */
public final class Http {
    private Http() {}

    public static final String USER_AGENT = "Gegi/1.0 (Minecraft mod; itch.io and Newgrounds browser)";
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, new java.util.concurrent.ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "GEGI HTTP " + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    public static CompletableFuture<byte[]> get(URI uri) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetch(uri);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, POOL);
    }

    static byte[] fetch(URI uri) throws IOException {
        URLConnection opened = uri.toURL().openConnection();
        if (!(opened instanceof HttpURLConnection)) throw new IOException("not an http link: " + uri);
        HttpURLConnection connection = (HttpURLConnection) opened;
        // Redirects are followed within https only: HttpURLConnection never crosses protocols.
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException(uri.getHost() + " answered " + status);
            try (InputStream body = connection.getInputStream()) {
                return readCapped(body);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > MAX_BYTES) throw new IOException("response larger than " + MAX_BYTES + " bytes");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
