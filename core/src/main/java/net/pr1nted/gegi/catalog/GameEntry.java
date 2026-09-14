package net.pr1nted.gegi.catalog;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * One game on a shelf. A value: two entries with the same fields are equal.
 *
 * <p>title: the game's name, without itch.io's "[Free] [Windows]" suffixes.
 * url: where the game is played; always passes {@link Links#isAllowed(URI)}.
 * blurb: one line about it, possibly empty.
 * imageUrl: a thumbnail on an allowed image host, or empty.
 * price: "Free", "$4.99", or empty when the source does not say.
 * site: where it comes from, shown on the card ("itch.io", "Newgrounds").
 * bundledImage: a texture shipped with the mod ("namespace:path"), used instead of imageUrl.
 */
public final class GameEntry {
    private final String title;
    private final URI url;
    private final String blurb;
    private final Optional<URI> imageUrl;
    private final String price;
    private final String site;
    private final Optional<String> bundledImage;

    public GameEntry(String title, URI url, String blurb, Optional<URI> imageUrl,
                     String price, String site, Optional<String> bundledImage) {
        if (!Links.isAllowed(url)) {
            throw new IllegalArgumentException("not a link GEGI opens: " + url);
        }
        this.title = Objects.requireNonNull(title, "title");
        this.url = url;
        this.blurb = blurb == null ? "" : blurb;
        this.imageUrl = imageUrl == null ? Optional.<URI>empty() : imageUrl.filter(Links::isAllowedImage);
        this.price = price == null ? "" : price;
        this.site = site == null ? "" : site;
        this.bundledImage = bundledImage == null ? Optional.<String>empty() : bundledImage;
    }

    public String title() {
        return title;
    }

    public URI url() {
        return url;
    }

    public String blurb() {
        return blurb;
    }

    public Optional<URI> imageUrl() {
        return imageUrl;
    }

    public String price() {
        return price;
    }

    public String site() {
        return site;
    }

    public Optional<String> bundledImage() {
        return bundledImage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameEntry)) return false;
        GameEntry other = (GameEntry) o;
        return title.equals(other.title) && url.equals(other.url) && blurb.equals(other.blurb)
                && imageUrl.equals(other.imageUrl) && price.equals(other.price) && site.equals(other.site)
                && bundledImage.equals(other.bundledImage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, url, blurb, imageUrl, price, site, bundledImage);
    }

    @Override
    public String toString() {
        return "GameEntry[" + title + ", " + url + "]";
    }
}
