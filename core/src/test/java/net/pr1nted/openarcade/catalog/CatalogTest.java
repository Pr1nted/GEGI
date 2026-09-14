package net.pr1nted.openarcade.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogTest {

    @Test
    void openDoctrinesIsTheFirstRecommendation() {
        Catalog catalog = Catalog.bundled();
        assertFalse(catalog.recommended().isEmpty());
        GameEntry first = catalog.recommended().get(0);
        assertEquals("Open Doctrines", first.title());
        assertEquals("https://pr1nted.itch.io/open-doctrines", first.url().toString());
        assertTrue(first.bundledImage().isPresent(), "shows its own art even offline");
    }

    @Test
    void everyShelfAndSiteIsAnAllowedLink() {
        Catalog catalog = Catalog.bundled();
        assertFalse(catalog.shelves().isEmpty());
        catalog.shelves().forEach(s -> {
            assertTrue(Links.isAllowed(s.feed()), s.feed().toString());
            assertTrue(s.feed().getPath().endsWith(".xml"), "itch.io's feed form of a browse page");
            assertFalse(s.tab().isBlank());
        });
        catalog.sites().forEach(s -> assertTrue(Links.isAllowed(s.url()), s.url().toString()));
    }
}
