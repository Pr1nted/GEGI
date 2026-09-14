package net.pr1nted.gegi.catalog;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * Which links GEGI will open, and which images it will fetch.
 *
 * <p>Only itch.io and Newgrounds, only over https. The menu opens pages without a
 * confirmation prompt, which is only reasonable because the set of places it can
 * send a player is this short and fixed.
 */
public final class Links {
    private Links() {}

    public static boolean isAllowed(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
        if (uri.getUserInfo() != null) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("itch.io") || host.endsWith(".itch.io")
                || host.equals("newgrounds.com") || host.endsWith(".newgrounds.com");
    }

    /** Thumbnails come from itch.io's image CDN and nowhere else. */
    public static boolean isAllowedImage(URI uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getHost().equalsIgnoreCase("img.itch.zone");
    }

    /**
     * Reads what a player pasted. "pr1nted.itch.io/open-doctrines" and
     * "https://www.newgrounds.com/portal/view/123" both work; anything outside the
     * allowed sites does not.
     */
    public static Optional<URI> parsePasted(String text) {
        if (text == null) return Optional.empty();
        String t = text.trim();
        if (t.isEmpty() || t.contains(" ")) return Optional.empty();
        if (!t.contains("://")) t = "https://" + t;
        try {
            URI uri = URI.create(t);
            return isAllowed(uri) ? Optional.of(uri) : Optional.<URI>empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
