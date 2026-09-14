package net.pr1nted.gegi.catalog;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinksTest {

    @Test
    void opensItchAndNewgroundsOverHttps() {
        assertTrue(Links.isAllowed(URI.create("https://itch.io/games/platform-web")));
        assertTrue(Links.isAllowed(URI.create("https://pr1nted.itch.io/open-doctrines")));
        assertTrue(Links.isAllowed(URI.create("https://www.newgrounds.com/portal/view/123456")));
        assertTrue(Links.isAllowed(URI.create("https://newgrounds.com/games")));
    }

    @Test
    void refusesEverythingElse() {
        assertFalse(Links.isAllowed(URI.create("http://pr1nted.itch.io/open-doctrines")), "plain http");
        assertFalse(Links.isAllowed(URI.create("https://example.com/")), "another site");
        assertFalse(Links.isAllowed(URI.create("https://itch.io.example.com/")), "a lookalike host");
        assertFalse(Links.isAllowed(URI.create("https://evilitch.io/")), "a suffix without the dot");
        assertFalse(Links.isAllowed(URI.create("https://user@pr1nted.itch.io/")), "credentials in the link");
        assertFalse(Links.isAllowed(URI.create("file:///etc/passwd")), "a local file");
        assertFalse(Links.isAllowed(null));
    }

    @Test
    void thumbnailsOnlyFromItchsImageCdn() {
        assertTrue(Links.isAllowedImage(URI.create("https://img.itch.zone/aW1n/315x250%23c/x.png")));
        assertFalse(Links.isAllowedImage(URI.create("https://pr1nted.itch.io/cover.png")));
        assertFalse(Links.isAllowedImage(URI.create("http://img.itch.zone/x.png")));
    }

    @Test
    void readsWhatAPlayerPastes() {
        assertEquals(URI.create("https://pr1nted.itch.io/open-doctrines"),
                Links.parsePasted("  pr1nted.itch.io/open-doctrines ").orElseThrow());
        assertEquals(URI.create("https://www.newgrounds.com/portal/view/1"),
                Links.parsePasted("https://www.newgrounds.com/portal/view/1").orElseThrow());
        assertTrue(Links.parsePasted("example.com").isEmpty());
        assertTrue(Links.parsePasted("two words").isEmpty());
        assertTrue(Links.parsePasted("").isEmpty());
    }
}
