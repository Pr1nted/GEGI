package net.pr1nted.openarcade.menu;

import net.pr1nted.openarcade.Strings;
import net.pr1nted.openarcade.catalog.Catalog;
import net.pr1nted.openarcade.catalog.GameEntry;
import net.pr1nted.openarcade.catalog.Links;
import net.pr1nted.openarcade.catalog.ShelfLoads;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the arcade menu knows and decides, without drawing any of it: the tabs
 * (Recommended, one per itch.io shelf, Browse sites), the search, the pasted link,
 * which games show, where each row is, and what a click at a point hits.
 *
 * <p>Each Minecraft version's screen draws this with that version's GUI API and
 * forwards its input here, so the menu behaves the same everywhere and a port only
 * has to redo the drawing. Coordinates are GUI pixels.
 */
public final class ArcadeModel {

    public static final int ROW_HEIGHT = 58;
    public static final int ROW_GAP = 4;
    public static final int THUMB_WIDTH = 63;
    public static final int THUMB_HEIGHT = 50;
    public static final int TABS_Y = 24;
    public static final int FIELDS_Y = 50;
    public static final int LIST_TOP = 74;
    public static final int FOOTER = 32;
    public static final int OPEN_LINK_WIDTH = 70;
    public static final int DONE_WIDTH = 120;

    public static final int WHITE = 0xFFE8FFF1;
    public static final int DIM = 0xFF8FB9A2;
    public static final int GOLD = 0xFFFFD700;
    public static final int ROW = 0x70101A16;
    public static final int ROW_HOVER = 0xA02E5A45;
    public static final int PLACEHOLDER = 0xFF1A4D33;

    private final Catalog catalog;
    private final List<GameEntry> siteEntries = new ArrayList<>();

    private int tab;
    private double scroll;
    private String query = "";
    private String pasted = "";
    private String notice;

    public ArcadeModel(Catalog catalog) {
        this.catalog = catalog;
        for (Catalog.SiteLink site : catalog.sites()) {
            siteEntries.add(new GameEntry(site.title(), site.url(), site.url().getHost(), Optional.<URI>empty(), "",
                    Strings.string("openarcade.site.browse"), Optional.<String>empty()));
        }
    }

    public Catalog catalog() {
        return catalog;
    }

    // ---- tabs

    public int tabCount() {
        return 2 + catalog.shelves().size();
    }

    public int tab() {
        return tab;
    }

    public void selectTab(int index) {
        tab = Math.max(0, Math.min(tabCount() - 1, index));
        scroll = 0;
    }

    public String tabLabel(int index) {
        if (index == 0) return Strings.string("openarcade.tab.recommended");
        if (index == tabCount() - 1) return Strings.string("openarcade.tab.sites");
        return catalog.shelves().get(index - 1).tab();
    }

    // ---- search and pasted link

    public String query() {
        return query;
    }

    public void setQuery(String text) {
        query = text == null ? "" : text;
        scroll = 0;
    }

    public String pasted() {
        return pasted;
    }

    public void setPasted(String text) {
        pasted = text == null ? "" : text;
        notice = null;
    }

    /** The pasted link as a game to play, or empty after putting the reason in the footer. */
    public Optional<GameEntry> takePasted() {
        Optional<URI> uri = Links.parsePasted(pasted);
        if (!uri.isPresent()) {
            notice = Strings.string("openarcade.link.invalid");
            return Optional.empty();
        }
        notice = null;
        URI link = uri.get();
        return Optional.of(new GameEntry(link.getHost(), link, link.getHost(), Optional.<URI>empty(), "", link.getHost(),
                Optional.<String>empty()));
    }

    /** The line under the list: why a pasted link was refused, or the usual hint. */
    public String footer() {
        return notice != null ? notice : Strings.string("openarcade.opens_in_browser");
    }

    public boolean footerIsNotice() {
        return notice != null;
    }

    // ---- games

    /** The games on the current tab, after the search box. Starts loading shelves as a side effect. */
    public List<GameEntry> visibleGames() {
        List<GameEntry> games;
        if (tab == 0) {
            Map<URI, GameEntry> merged = new LinkedHashMap<>();
            for (GameEntry g : catalog.recommended()) merged.put(g.url(), g);
            if (!catalog.shelves().isEmpty()) {
                for (GameEntry g : ShelfLoads.get(catalog.shelves().get(0)).games) {
                    if (!merged.containsKey(g.url())) merged.put(g.url(), g);
                }
            }
            games = new ArrayList<>(merged.values());
        } else if (tab == tabCount() - 1) {
            games = siteEntries;
        } else {
            games = ShelfLoads.get(catalog.shelves().get(tab - 1)).games;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return games;
        List<GameEntry> matches = new ArrayList<>();
        for (GameEntry g : games) {
            if (g.title().toLowerCase(Locale.ROOT).contains(q) || g.blurb().toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(g);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    /** "Loading...", an error, "nothing matches", or null when the list speaks for itself. */
    public String status(List<GameEntry> games) {
        ShelfLoads.Load load = tab == 0 && !catalog.shelves().isEmpty() ? ShelfLoads.get(catalog.shelves().get(0))
                : tab > 0 && tab < tabCount() - 1 ? ShelfLoads.get(catalog.shelves().get(tab - 1)) : null;
        if (load != null && !load.done) return Strings.string("openarcade.loading");
        if (load != null && !load.error.isEmpty()) return Strings.string("openarcade.failed", load.error);
        if (games.isEmpty()) return Strings.string("openarcade.empty");
        return null;
    }

    public boolean featured(GameEntry game) {
        return catalog.recommended().contains(game);
    }

    /** "Recommended · itch.io · Free", leaving out what is not known. */
    public String metaLine(GameEntry game) {
        StringBuilder line = new StringBuilder();
        for (String part : new String[]{featured(game) ? Strings.string("openarcade.featured") : "", game.site(), game.price()}) {
            if (part == null || part.trim().isEmpty()) continue;
            if (line.length() > 0) line.append("  ·  ");
            line.append(part);
        }
        return line.toString();
    }

    // ---- layout

    public int listLeft(int width) {
        return Math.max(10, width / 2 - 230);
    }

    public int listRight(int width) {
        return Math.min(width - 10, width / 2 + 230);
    }

    public int listBottom(int height) {
        return height - FOOTER;
    }

    public int tabWidth(int width) {
        int span = listRight(width) - listLeft(width);
        return Math.max(40, (span - ROW_GAP * (tabCount() - 1)) / tabCount());
    }

    public int tabX(int width, int index) {
        return listLeft(width) + index * (tabWidth(width) + ROW_GAP);
    }

    /** The search box and the link box share the row under the tabs, half each. */
    public int fieldHalf(int width) {
        return (listRight(width) - listLeft(width) - ROW_GAP) / 2;
    }

    // ---- scrolling

    /** Scroll by mouse-wheel notches (positive is up, as the game reports it). */
    public void scrollNotches(double notches) {
        scroll -= notches * ROW_HEIGHT / 2.0;
    }

    /** Keeps the scroll inside the list and returns it, in pixels; call before drawing. */
    public int clampScroll(int height, int gameCount) {
        int visible = listBottom(height) - LIST_TOP;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, gameCount * ROW_HEIGHT - visible)));
        return (int) scroll;
    }

    public int rowY(int index) {
        return LIST_TOP + index * ROW_HEIGHT - (int) scroll;
    }

    /** The game under a point, or null. */
    public GameEntry gameAt(int width, int height, double x, double y) {
        int top = LIST_TOP;
        int bottom = listBottom(height);
        if (x < listLeft(width) || x >= listRight(width) || y < top || y >= bottom) return null;
        int index = (int) ((y - top + scroll) / ROW_HEIGHT);
        double withinRow = (y - top + scroll) - index * ROW_HEIGHT;
        List<GameEntry> games = visibleGames();
        if (index < 0 || index >= games.size() || withinRow > ROW_HEIGHT - ROW_GAP) return null;
        return games.get(index);
    }
}
