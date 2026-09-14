package net.pr1nted.openarcade.catalog;

import java.net.URI;
import java.util.Optional;

/**
 * One game on a shelf.
 *
 * @param title    the game's name, without itch.io's "[Free] [Windows]" suffixes
 * @param url      where the game is played; always passes {@link Links#isAllowed(URI)}
 * @param blurb    one line about it, possibly empty
 * @param imageUrl a thumbnail on an allowed image host, or empty
 * @param price    "Free", "$4.99", or empty when the source does not say
 * @param site     where it comes from, shown on the card ("itch.io", "Newgrounds")
 * @param bundledImage a texture shipped with the mod, used instead of {@code imageUrl}
 */
public record GameEntry(String title, URI url, String blurb, Optional<URI> imageUrl,
                        String price, String site, Optional<String> bundledImage) {

    public GameEntry {
        if (!Links.isAllowed(url)) {
            throw new IllegalArgumentException("not a link Open Arcade opens: " + url);
        }
        blurb = blurb == null ? "" : blurb;
        price = price == null ? "" : price;
        imageUrl = imageUrl.filter(Links::isAllowedImage);
    }
}
