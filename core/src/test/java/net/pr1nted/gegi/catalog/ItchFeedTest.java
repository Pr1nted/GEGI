package net.pr1nted.gegi.catalog;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItchFeedTest {

    private static byte[] sample() throws Exception {
        // Two items from https://itch.io/games/platform-web.xml, captured 2026-09-14.
        try (InputStream in = ItchFeedTest.class.getResourceAsStream("/itch-platform-web-sample.xml")) {
            return in.readAllBytes();
        }
    }

    @Test
    void readsARealFeed() throws Exception {
        List<GameEntry> games = ItchFeed.parse(sample());
        assertEquals(2, games.size());
        GameEntry first = games.get(0);
        assertFalse(first.title().contains("[Free]"), "uses plainTitle, not the title with itch.io's tags");
        assertTrue(first.url().getHost().endsWith("itch.io"));
        assertTrue(first.imageUrl().isPresent(), "keeps the img.itch.zone thumbnail");
        assertEquals("Free", first.price());
        assertEquals("itch.io", first.site());
        assertFalse(first.blurb().contains("<img"), "the blurb is the first line, without the image tag");
    }

    @Test
    void dropsItemsThatLinkOutsideTheAllowedSites() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8" ?><rss version="2.0"><channel>
                <item><plainTitle>Fine</plainTitle><link>https://someone.itch.io/fine</link><price>$1.99</price><currency>USD</currency></item>
                <item><plainTitle>Elsewhere</plainTitle><link>https://example.com/game</link></item>
                <item><plainTitle>Broken</plainTitle><link>not a uri at all</link></item>
                </channel></rss>""";
        List<GameEntry> games = ItchFeed.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, games.size());
        assertEquals("Fine", games.get(0).title());
        assertEquals("$1.99 USD", games.get(0).price());
        assertTrue(games.get(0).imageUrl().isEmpty());
    }

    @Test
    void leavesOutGamesThatSayTheyAreForAdults() throws Exception {
        // The first is a real item from https://itch.io/games/free/platform-web.xml (2026-09-14).
        String xml = """
                <?xml version="1.0" encoding="UTF-8" ?><rss version="2.0"><channel>
                <item><plainTitle>The Freak Circus</plainTitle><title>The Freak Circus [Free] [Visual Novel]</title><link>https://garula.itch.io/the-freak-circus</link><description>18+ Yandere &lt;img src="https://img.itch.zone/a.png"/&gt;</description></item>
                <item><plainTitle>Night Shift [NSFW]</plainTitle><link>https://a.itch.io/night-shift</link></item>
                <item><plainTitle>Quiet Room</plainTitle><link>https://b.itch.io/quiet-room</link><description>An adult visual novel</description></item>
                <item><plainTitle>Garden</plainTitle><link>https://c.itch.io/garden</link><description>A cosy farming game</description></item>
                <item><plainTitle>Essex Express</plainTitle><link>https://d.itch.io/essex</link><description>Trains for young adults, rated 12+</description></item>
                </channel></rss>""";
        List<GameEntry> games = ItchFeed.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("Garden", "Essex Express"), games.stream().map(GameEntry::title).toList());
    }

    @Test
    void adultWordsAreMatchedAsWholeWords() {
        assertTrue(ItchFeed.saysAdult("18+ Yandere"));
        assertTrue(ItchFeed.saysAdult("Rated 18 +"));
        assertTrue(ItchFeed.saysAdult("[R-18] demo"));
        assertTrue(ItchFeed.saysAdult("For adults only"));
        assertTrue(ItchFeed.saysAdult("Hentai puzzle"));
        assertTrue(ItchFeed.saysAdult("<b>NSFW</b>"));
        assertFalse(ItchFeed.saysAdult("Middlesex Motors"));
        assertFalse(ItchFeed.saysAdult("A game for young adults"));
        assertFalse(ItchFeed.saysAdult("Top 118+ levels"));
        assertTrue(ItchFeed.saysAdult(null, "Pornographic"), "a null text is skipped, the next one still counts");
        assertFalse(ItchFeed.saysAdult("Grand strategy about running a country"));
    }

    @Test
    void refusesADoctype() {
        // A feed is network data: a DTD is how an external-entity attack starts.
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <rss><channel><item><plainTitle>&x;</plainTitle><link>https://a.itch.io/b</link></item></channel></rss>""";
        assertThrows(Exception.class, () -> ItchFeed.parse(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void gameEntriesCannotPointOutsideTheAllowedSites() {
        assertThrows(IllegalArgumentException.class, () -> new GameEntry("x", URI.create("https://example.com"),
                "", java.util.Optional.empty(), "", "", java.util.Optional.empty()));
    }
}
