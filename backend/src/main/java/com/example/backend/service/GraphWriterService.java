package com.example.backend.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.Values;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.SummaryCounters;
import org.springframework.stereotype.Service;

/**
 * Service that writes a simple Wikipedia page graph into Neo4j.
 *
 * Semantics:
 *  - MERGE a Page node for the main URL and set the title.
 *  - For each linked URL, MERGE a Page node and set a minimal title derived from the URL slug if present.
 *  - MERGE a LINKS_TO relationship from the main Page to each linked Page.
 *  - Accumulates the counters (nodesCreated and relationshipsCreated) from all queries and returns them.
 *
 * All writing uses parameterized Cypher queries to avoid injection issues and uses try-with-resources
 * to manage driver resources.
 */
@Service
public class GraphWriterService {

    private final Driver driver;

    public GraphWriterService(Driver driver) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
    }

    // PUBLIC_INTERFACE
    /**
     * Write the supplied graph data into Neo4j with MERGE semantics.
     *
     * Steps:
     * 1) Open a write session.
     * 2) MERGE (:Page {url: mainUrl}) and SET its title.
     * 3) For each linked URL, MERGE (:Page {url: linkedUrl}) and optionally set a minimal title from URL slug.
     * 4) MERGE (main)-[:LINKS_TO]->(linked).
     * 5) Accumulate counters from result summaries.
     * 6) Return a small DTO with totals.
     *
     * @param mainUrl the absolute Wikipedia page URL of the main page
     * @param mainTitle the human-readable title of the main page
     * @param linkedUrls a collection of absolute Wikipedia page URLs linked from the main page
     * @return GraphWriteResult containing nodesCreated and relationshipsCreated totals
     * @throws GraphWriteException if any write operation fails
     */
    public GraphWriteResult writeGraph(String mainUrl, String mainTitle, Collection<String> linkedUrls) {
        int totalNodesCreated = 0;
        int totalRelationshipsCreated = 0;

        // Use a write session. If a specific DB is needed, SessionConfig can be customized here.
        try (Session session = driver.session(SessionConfig.defaultConfig())) {

            // Execute writes in a single write transaction for atomicity and performance.
            GraphWriteResult result = session.executeWrite((TransactionCallback<GraphWriteResult>) tx -> {
                int nodesCreated = 0;
                int relationshipsCreated = 0;

                // 1) MERGE main page node and set title
                String mergeMainCypher =
                    "MERGE (p:Page {url: $url}) " +
                    "ON CREATE SET p.title = $title " +
                    "ON MATCH SET p.title = coalesce(p.title, $title) " +
                    "RETURN p";

                ResultSummary mainSummary = tx.run(
                    new Query(mergeMainCypher, Values.parameters(
                        "url", mainUrl,
                        "title", mainTitle
                    ))
                ).consume();

                nodesCreated += safeNodeCreates(mainSummary.counters());

                // 2) For each linked URL: MERGE node, set minimal title if available, and MERGE relationship
                if (linkedUrls != null) {
                    for (String linked : linkedUrls) {
                        String minimalTitle = deriveTitleFromUrl(linked);

                        String mergeLinkedCypher =
                            "MERGE (l:Page {url: $url}) " +
                            "ON CREATE SET l.title = $title " +
                            "ON MATCH SET l.title = coalesce(l.title, $title) " +
                            "RETURN l";

                        ResultSummary linkedSummary = tx.run(
                            new Query(mergeLinkedCypher, Values.parameters(
                                "url", linked,
                                "title", minimalTitle
                            ))
                        ).consume();

                        nodesCreated += safeNodeCreates(linkedSummary.counters());

                        // MERGE relationship
                        String mergeRelCypher =
                            "MATCH (p:Page {url: $mainUrl}), (l:Page {url: $linkedUrl}) " +
                            "MERGE (p)-[:LINKS_TO]->(l)";

                        ResultSummary relSummary = tx.run(
                            new Query(mergeRelCypher, Values.parameters(
                                "mainUrl", mainUrl,
                                "linkedUrl", linked
                            ))
                        ).consume();

                        relationshipsCreated += safeRelCreates(relSummary.counters());
                    }
                }

                return new GraphWriteResult(nodesCreated, relationshipsCreated);
            });

            totalNodesCreated += result.getNodesCreated();
            totalRelationshipsCreated += result.getRelationshipsCreated();

        } catch (Exception e) {
            throw new GraphWriteException("Failed to write graph to Neo4j: " + e.getMessage(), e);
        }

        return new GraphWriteResult(totalNodesCreated, totalRelationshipsCreated);
    }

    /**
     * Defensive helper to read nodes created from counters.
     */
    private static int safeNodeCreates(SummaryCounters counters) {
        return counters != null ? counters.nodesCreated() : 0;
    }

    /**
     * Defensive helper to read relationships created from counters.
     */
    private static int safeRelCreates(SummaryCounters counters) {
        return counters != null ? counters.relationshipsCreated() : 0;
    }

    /**
     * Attempts to derive a minimal, human-readable title from a Wikipedia URL slug.
     * Example:
     *  - https://en.wikipedia.org/wiki/Graph_theory -> "Graph theory"
     *  - https://en.wikipedia.org/wiki/Alan_Turing -> "Alan Turing"
     * If parsing fails, returns null so that MERGE SET coalesce can keep any existing title.
     */
    private static String deriveTitleFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url);
            String path = uri.getPath(); // e.g., /wiki/Graph_theory
            if (path == null || path.isBlank()) {
                return null;
            }
            // Expect /wiki/Slug
            int idx = path.lastIndexOf('/');
            String slug = idx >= 0 ? path.substring(idx + 1) : path;
            if (slug.isBlank()) {
                return null;
            }
            // Replace underscores with spaces and trim
            String candidate = slug.replace('_', ' ').trim();
            if (candidate.isBlank()) {
                return null;
            }
            // Capitalize first letter for a nicer look
            return capitalizeFirst(candidate);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        char first = s.charAt(0);
        char upper = Character.toUpperCase(first);
        if (first == upper) {
            return s;
        }
        return upper + s.substring(1);
    }

    /**
     * Custom runtime exception used to signal write failures.
     */
    public static class GraphWriteException extends RuntimeException {
        public GraphWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Small DTO for returning counters from writeGraph.
     */
    public static class GraphWriteResult {
        private final int nodesCreated;
        private final int relationshipsCreated;

        public GraphWriteResult(int nodesCreated, int relationshipsCreated) {
            this.nodesCreated = nodesCreated;
            this.relationshipsCreated = relationshipsCreated;
        }

        // PUBLIC_INTERFACE
        /** Number of nodes that were created by the write operations. */
        public int getNodesCreated() {
            return nodesCreated;
        }

        // PUBLIC_INTERFACE
        /** Number of relationships that were created by the write operations. */
        public int getRelationshipsCreated() {
            return relationshipsCreated;
        }

        @Override
        public String toString() {
            return "GraphWriteResult{nodesCreated=" + nodesCreated +
                ", relationshipsCreated=" + relationshipsCreated + "}";
        }
    }
}
