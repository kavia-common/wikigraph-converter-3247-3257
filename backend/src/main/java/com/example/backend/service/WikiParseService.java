package com.example.backend.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

/**
 * Service responsible for parsing a Wikipedia page and extracting:
 *  - The page title
 *  - A de-duplicated, ordered list of internal Wikipedia links (absolute URLs)
 *
 * The parser validates that the incoming URL:
 *  - Uses HTTPS scheme
 *  - Host ends with "wikipedia.org" (supports language subdomains like en.wikipedia.org)
 *
 * It filters out non-content namespaces like Special:, Help:, File:, Category:, etc.
 * and removes fragment identifiers from links. A safe upper bound is applied on
 * the number of links returned to avoid overloading downstream processing.
 */
@Service
public class WikiParseService {

    private static final String USER_AGENT =
        "WikiGraphConverterBot/0.1 (+https://example.com) Jsoup";
    private static final int TIMEOUT_MILLIS = (int) Duration.ofSeconds(15).toMillis();
    private static final int MAX_LINKS = 200;

    private static final String[] EXCLUDED_NAMESPACES = new String[] {
        "Special:", "Help:", "File:", "Category:", "Portal:", "Talk:", "Template:",
        "Template_talk:", "User:", "User_talk:", "Wikipedia:", "Wikipedia_talk:",
        "Draft:", "TimedText:", "Module:", "Book:", "Education_Program:", "Gadget:",
        "Gadget_definition:", "MediaWiki:", "MediaWiki_talk:"
    };

    // PUBLIC_INTERFACE
    /**
     * Parse a Wikipedia page and extract the title and internal links.
     *
     * @param url HTTPS Wikipedia page URL (e.g., https://en.wikipedia.org/wiki/Graph_theory)
     * @return ParsedResult containing the title and unique linked page URLs
     * @throws WikiParseException if validation fails or the page cannot be fetched/parsed
     */
    public ParsedResult parse(String url) {
        validateWikipediaUrl(url);

        final Document doc;
        try {
            doc = Jsoup
                .connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MILLIS)
                .followRedirects(true)
                .referrer("https://www.google.com")
                .get();
        } catch (HttpStatusException e) {
            throw new WikiParseException("Failed to fetch URL (" + e.getStatusCode() + "): " + e.getUrl(), e);
        } catch (UnsupportedMimeTypeException e) {
            throw new WikiParseException("Unsupported content type for URL: " + e.getUrl(), e);
        } catch (MalformedURLException e) {
            throw new WikiParseException("Malformed URL: " + url, e);
        } catch (IOException e) {
            throw new WikiParseException("I/O error fetching URL: " + url, e);
        }

        String title = extractTitle(doc);
        List<String> links = extractInternalLinks(doc, url);

        return new ParsedResult(title, links);
    }

    private void validateWikipediaUrl(String url) {
        final URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new WikiParseException("URL is not a valid URI: " + url, e);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw new WikiParseException("URL must use https scheme: " + url);
        }
        String host = uri.getHost();
        if (host == null || !host.toLowerCase(Locale.ROOT).endsWith("wikipedia.org")) {
            throw new WikiParseException("URL must be a wikipedia.org host: " + url);
        }
    }

    private String extractTitle(Document doc) {
        // Prefer the firstHeading if present, otherwise fallback to document title
        Element heading = doc.selectFirst("h1#firstHeading");
        String headingText = heading != null ? heading.text() : null;
        if (headingText != null && !headingText.isBlank()) {
            return headingText;
        }
        String docTitle = doc.title();
        if (docTitle == null) {
            return "";
        }
        // Wikipedia titles often end with " - Wikipedia"
        return docTitle.replace(" - Wikipedia", "").trim();
    }

    private List<String> extractInternalLinks(Document doc, String pageUrl) {
        final URI baseUri;
        try {
            baseUri = new URI(pageUrl);
        } catch (URISyntaxException e) {
            // Should not happen due to earlier validation
            throw new WikiParseException("Internal error: invalid base URL: " + pageUrl, e);
        }

        String origin = baseUri.getScheme() + "://" + baseUri.getHost();

        // Collect internal wiki links under the /wiki/ path
        // Avoid special namespaces and fragments
        Elements anchors = doc.select("a[href^=\"/wiki/\"]");
        Set<String> unique = new LinkedHashSet<>();

        for (Element a : anchors) {
            String href = a.attr("href");
            if (href == null || href.isBlank()) {
                continue;
            }

            // Skip fragments and query-only navigations
            if (href.startsWith("#")) {
                continue;
            }

            // Remove URL fragment if present to normalize
            int hashIdx = href.indexOf('#');
            if (hashIdx >= 0) {
                href = href.substring(0, hashIdx);
            }
            // Skip empty after removing fragment
            if (href.isBlank()) {
                continue;
            }

            // Filter excluded namespaces by the part after /wiki/
            String afterWiki = href.startsWith("/wiki/") ? href.substring("/wiki/".length()) : href;
            if (isExcludedNamespace(afterWiki)) {
                continue;
            }

            // Build absolute URL using the current page origin
            String absolute = origin + href;

            // Simple sanity check: ensure it still points to the same host
            if (!absolute.toLowerCase(Locale.ROOT).contains("wikipedia.org")) {
                continue;
            }

            unique.add(absolute);

            if (unique.size() >= MAX_LINKS) {
                break; // enforce limit
            }
        }

        return new ArrayList<>(unique);
    }

    private boolean isExcludedNamespace(String pathPart) {
        if (pathPart == null || pathPart.isBlank()) {
            return true;
        }
        for (String ns : EXCLUDED_NAMESPACES) {
            if (pathPart.startsWith(ns)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Data transfer object representing the result of parsing a Wikipedia page.
     * Contains the title and the list of linked page URLs (absolute), deduplicated and ordered.
     */
    public static final class ParsedResult {
        private final String title;
        private final List<String> linkedUrls;

        public ParsedResult(String title, List<String> linkedUrls) {
            this.title = title;
            this.linkedUrls = linkedUrls != null ? List.copyOf(linkedUrls) : List.of();
        }

        // PUBLIC_INTERFACE
        /** The parsed page title. */
        public String getTitle() {
            return title;
        }

        // PUBLIC_INTERFACE
        /** The unique ordered list of absolute internal Wikipedia links found on the page. */
        public List<String> getLinkedUrls() {
            return linkedUrls;
        }

        @Override
        public String toString() {
            return "ParsedResult{title='" + title + "', linkedUrls=" + linkedUrls.size() + "}";
        }
    }

    /**
     * Exception signaling problems validating, fetching, or parsing Wikipedia pages.
     */
    public static class WikiParseException extends RuntimeException {
        public WikiParseException(String message) {
            super(message);
        }

        public WikiParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
