package net.pr1nted.openarcade.catalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** The mod's only network access: GET a page or an image, bounded in time and size. */
public final class Http {
    private Http() {}

    public static final String USER_AGENT = "OpenArcade/1.0 (Minecraft mod; itch.io and Newgrounds browser)";
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static CompletableFuture<byte[]> get(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException(uri.getHost() + " answered " + response.statusCode());
                }
                return readCapped(body);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
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
