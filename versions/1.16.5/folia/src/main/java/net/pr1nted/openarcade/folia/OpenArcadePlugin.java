package net.pr1nted.openarcade.folia;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Open Arcade on a Folia (or Paper) server.
 *
 * <p>A server has no screen to draw a menu on, so here the menu is chat: {@code /arcade}
 * lists the same recommended games and site links the client mod shows, as links
 * the player clicks to open in their own browser. Nothing is scheduled and no world
 * state is touched, which is what makes it safe on Folia's regionised threads.
 */
public final class OpenArcadePlugin extends JavaPlugin {

    private static final class Link {
        private final String title;
        private final String url;
        private final String blurb;

        Link(String title, String url, String blurb) {
            this.title = title;
            this.url = url;
            this.blurb = blurb;
        }

        String title() {
            return title;
        }

        String url() {
            return url;
        }

        String blurb() {
            return blurb;
        }
    }

    private final List<Link> links = new ArrayList<>();

    @Override
    public void onEnable() {
        try (InputStream in = getResource("openarcade/catalog.json")) {
            if (in == null) throw new IllegalStateException("catalog.json missing from the plugin jar");
            JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : array(root, "recommended")) {
                JsonObject o = e.getAsJsonObject();
                links.add(new Link(o.get("title").getAsString(), o.get("url").getAsString(),
                        o.has("blurb") ? o.get("blurb").getAsString() : ""));
            }
            for (JsonElement e : array(root, "sites")) {
                JsonObject o = e.getAsJsonObject();
                links.add(new Link(o.get("title").getAsString(), o.get("url").getAsString(), ""));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Open Arcade could not read its catalog", e);
        }
        getLogger().info("Open Arcade enabled with " + links.size() + " links");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(Component.text("Open Arcade", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" - click a game to open it in your browser", NamedTextColor.GRAY)));
        for (Link link : links) {
            if (sender instanceof Player) {
                Component line = Component.text("  > ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(link.title(), NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.openUrl(link.url()))
                                .hoverEvent(HoverEvent.showText(Component.text(link.url(), NamedTextColor.GRAY))));
                if (!link.blurb().isEmpty()) {
                    line = line.append(Component.text(" " + link.blurb(), NamedTextColor.GRAY));
                }
                sender.sendMessage(line);
            } else {
                sender.sendMessage(Component.text("  " + link.title() + ": " + link.url()));
            }
        }
        return true;
    }

    private static JsonArray array(JsonObject root, String key) {
        return root.has(key) ? root.getAsJsonArray(key) : new JsonArray();
    }
}
