package net.pr1nted.openarcade.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * What ships inside the mod: the recommended games (Open Doctrines first), the
 * shelves filled from itch.io's feeds, and plain links to browse each site.
 *
 * <p>Read from {@code /assets/openarcade/catalog.json} on the classpath. The Folia
 * plugin reads the same file, so the in-game menu and the server command always
 * recommend the same games. Gson comes from the game: every Minecraft since 1.8 ships
 * it, and only API old enough for all of them is used.
 */
public final class Catalog {

    /** One itch.io feed; {@code tab} is its short name on the tab row, {@code title} the heading. */
    public static final class Shelf {
        private final String id;
        private final String tab;
        private final String title;
        private final URI feed;

        public Shelf(String id, String tab, String title, URI feed) {
            this.id = id;
            this.tab = tab;
            this.title = title;
            this.feed = feed;
        }

        public String id() {
            return id;
        }

        public String tab() {
            return tab;
        }

        public String title() {
            return title;
        }

        public URI feed() {
            return feed;
        }
    }

    public static final class SiteLink {
        private final String title;
        private final URI url;

        public SiteLink(String title, URI url) {
            this.title = title;
            this.url = url;
        }

        public String title() {
            return title;
        }

        public URI url() {
            return url;
        }
    }

    public static final String RESOURCE = "/assets/openarcade/catalog.json";

    private final List<GameEntry> recommended;
    private final List<Shelf> shelves;
    private final List<SiteLink> sites;

    public Catalog(List<GameEntry> recommended, List<Shelf> shelves, List<SiteLink> sites) {
        this.recommended = Collections.unmodifiableList(new ArrayList<>(recommended));
        this.shelves = Collections.unmodifiableList(new ArrayList<>(shelves));
        this.sites = Collections.unmodifiableList(new ArrayList<>(sites));
    }

    public List<GameEntry> recommended() {
        return recommended;
    }

    public List<Shelf> shelves() {
        return shelves;
    }

    public List<SiteLink> sites() {
        return sites;
    }

    public static Catalog bundled() {
        try (InputStream in = Catalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException(RESOURCE + " is missing from the jar");
            return read(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Open Arcade could not read its catalog", e);
        }
    }

    @SuppressWarnings("deprecation") // JsonParser.parseReader is Gson 2.8.6+; 1.12.2 ships 2.8.0
    static Catalog read(Reader reader) {
        JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
        List<GameEntry> recommended = new ArrayList<>();
        for (JsonElement e : array(root, "recommended")) {
            JsonObject o = e.getAsJsonObject();
            recommended.add(new GameEntry(
                    o.get("title").getAsString(),
                    URI.create(o.get("url").getAsString()),
                    string(o, "blurb"),
                    Optional.<URI>empty(),
                    string(o, "price"),
                    string(o, "site"),
                    Optional.ofNullable(o.has("texture") ? o.get("texture").getAsString() : null)));
        }
        List<Shelf> shelves = new ArrayList<>();
        for (JsonElement e : array(root, "shelves")) {
            JsonObject o = e.getAsJsonObject();
            URI feed = URI.create(o.get("feed").getAsString());
            if (!Links.isAllowed(feed)) throw new IllegalArgumentException("shelf feed not allowed: " + feed);
            shelves.add(new Shelf(o.get("id").getAsString(), o.get("tab").getAsString(), o.get("title").getAsString(), feed));
        }
        List<SiteLink> sites = new ArrayList<>();
        for (JsonElement e : array(root, "sites")) {
            JsonObject o = e.getAsJsonObject();
            URI url = URI.create(o.get("url").getAsString());
            if (!Links.isAllowed(url)) throw new IllegalArgumentException("site link not allowed: " + url);
            sites.add(new SiteLink(o.get("title").getAsString(), url));
        }
        return new Catalog(recommended, shelves, sites);
    }

    private static JsonArray array(JsonObject root, String key) {
        return root.has(key) ? root.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }
}
