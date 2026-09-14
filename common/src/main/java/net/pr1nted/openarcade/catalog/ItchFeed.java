package net.pr1nted.openarcade.catalog;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * itch.io's browse pages as data: every browse URL has an RSS twin with ".xml"
 * appended (https://itch.io/games/platform-web.xml). Each item carries
 * {@code plainTitle}, {@code link}, {@code imageurl}, {@code price},
 * {@code currency} and a {@code description} whose first line is the blurb.
 */
public final class ItchFeed {
    private ItchFeed() {}

    public static List<GameEntry> parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // A feed is data from the network: no DTDs, no external entities, no XInclude.
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));

        List<GameEntry> games = new ArrayList<>();
        NodeList items = doc.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String title = text(item, "plainTitle");
            if (title.isEmpty()) title = text(item, "title");
            URI link;
            try {
                link = URI.create(text(item, "link"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (title.isEmpty() || !Links.isAllowed(link)) continue;
            Optional<URI> image = Optional.empty();
            try {
                String raw = text(item, "imageurl");
                if (!raw.isEmpty()) image = Optional.of(URI.create(raw));
            } catch (IllegalArgumentException ignored) {
                // a card without a picture is still a card
            }
            games.add(new GameEntry(title, link, blurb(text(item, "description")), image,
                    price(text(item, "price"), text(item, "currency")), "itch.io", Optional.empty()));
        }
        return games;
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getParentNode() == parent) return n.getTextContent().strip();
        }
        return "";
    }

    /** The description is "a line of text" followed by an {@code <img>} tag; keep the line. */
    static String blurb(String description) {
        String firstLine = description.split("\\R", 2)[0];
        return firstLine.replaceAll("<[^>]*>", "").strip();
    }

    static String price(String amount, String currency) {
        if (amount.isEmpty()) return "";
        String digits = amount.replaceAll("[^0-9.]", "");
        try {
            if (!digits.isEmpty() && Double.parseDouble(digits) == 0.0) return "Free";
        } catch (NumberFormatException ignored) {
            // fall through and show what itch.io sent
        }
        return currency.isEmpty() || amount.contains(currency) ? amount : amount + " " + currency;
    }
}
