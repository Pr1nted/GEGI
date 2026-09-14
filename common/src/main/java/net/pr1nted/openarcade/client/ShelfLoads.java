package net.pr1nted.openarcade.client;

import net.pr1nted.openarcade.catalog.Catalog;
import net.pr1nted.openarcade.catalog.GameEntry;
import net.pr1nted.openarcade.catalog.Http;
import net.pr1nted.openarcade.catalog.ItchFeed;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The itch.io shelves, fetched once and kept for ten minutes, so reopening the menu
 * or switching tabs does not refetch a feed that has not had time to change.
 */
final class ShelfLoads {
    private ShelfLoads() {}

    private static final long FRESH_FOR_MS = 10 * 60 * 1000L;

    static final class Load {
        final long startedAt = System.currentTimeMillis();
        volatile List<GameEntry> games = List.of();
        volatile String error = "";
        volatile boolean done;
    }

    private static final Map<String, Load> LOADS = new ConcurrentHashMap<>();

    static Load get(Catalog.Shelf shelf) {
        return LOADS.compute(shelf.id(), (id, old) -> {
            boolean stale = old != null && System.currentTimeMillis() - old.startedAt > FRESH_FOR_MS;
            boolean failed = old != null && old.done && !old.error.isEmpty();
            if (old != null && !stale && !failed) return old;
            Load load = new Load();
            Http.get(shelf.feed()).thenApply(bytes -> {
                try {
                    return ItchFeed.parse(bytes);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }).whenComplete((games, error) -> {
                if (error != null) {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    load.error = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                } else {
                    load.games = List.copyOf(games);
                }
                load.done = true;
            });
            return load;
        });
    }
}
