package com.example.backend.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Objects;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
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
 * Behavior:
 *  - MERGE a Page node for the main URL and set the title.
 *  - For each linked URL, MERGE a Page node with a minimal title derived from the URL slug (if possible).
 *  - MERGE a LINKS_TO relationship from the main Page to each linked Page.
 *  - Accumulate counters (nodesCreated, relationshipsCreated) and return them.
 */
@Service
public class GraphWriterService {

    private final Driver driver;

    public GraphWriterService(Driver driver) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
    }

    // PUBLIC_INTERFACE
    /**
     * Writes the provided page and its links into Neo4j using MERGE semantics.
     *
     * @param mainUrl absolute Wikipedia URL of the main page
     * @param mainTitle human-readable title of the main page
     * @param linkedUrls collection of absolute Wikipedia URLs linked from the main page
     * @return GraphWriteResult totals for nodes and relationships created
     * @throws GraphWriteException on any failure while writing to Neo4j
     */
    public GraphWriteResult writeGraph(String mainUrl, String mainTitle, Collection<String> linkedUrls) {
        int totalNodesCreated = 0;
        int totalRelationshipsCreated = 0;

        try (var session = driver.session(SessionConfig.defaultConfig())) {
            GraphWriteResult txResult = session.executeWrite((TransactionCallback<GraphWriteResult>) tx -> {
                int nodesCreated = 0;
                int relationshipsCreated = 0;

                // MERGE main page
                String mergeMainCypher =
                    "MERGE (p:Page {url: $url}) " +
                    "ON CREATE SET p.title = $title " +
                    "ON MATCH SET p.title = coalesce(p.title, $title)";
                ResultSummary mainSummary = tx.run(new Query(
                    mergeMainCypher,
                    Values.parameters("url", mainUrl, "title", mainTitle)
                )).consume();
                nodesCreated += safeNodeCreates(mainSummary.counters());

                if (linkedUrls != null) {
                    for (String linked : linkedUrls) {
                        String minimalTitle = deriveTitleFromUrl(linked);

                        // MERGE linked page
                        String mergeLinkedCypher =
                            "MERGE (l:Page {url: $url}) " +
                            "ON CREATE SET l.title = $title " +
                            "ON MATCH SET l.title = coalesce(l.title, $title)";
                        ResultSummary linkedSummary = tx.run(new Query(
                            mergeLinkedCypher,
                            Values.parameters("url", linked, "title", minimalTitle)
                        )).consume();
                        nodesCreated += safeNodeCreates(linkedSummary.counters());

                        // MERGE relationship
                        String mergeRelCypher =
                            "MATCH (p:Page {url: $mainUrl}), (l:Page {url: $linkedUrl}) " +
                            "MERGE (p)-[:LINKS_TO]->(l)";
                        ResultSummary relSummary = tx.run(new Query(
                            mergeRelCypher,
                            Values.parameters("mainUrl", mainUrl, "linkedUrl", linked)
                        )).consume();
                        relationshipsCreated += safeRelCreates(relSummary.counters());
                    }
                }

                return new GraphWriteResult(nodesCreated, relationshipsCreated);
            });

            totalNodesCreated += txResult.getNodesCreated();
            totalRelationshipsCreated += txResult.getRelationshipsCreated();

        } catch (Exception e) {
            throw new GraphWriteException("Failed to write graph to Neo4j: " + e.getMessage(), e);
        }

        return new GraphWriteResult(totalNodesCreated, totalRelationshipsCreated);
    }

    /**
     * Defensive helper to get node creation count from summary counters.
     */
    private static int safeNodeCreates(SummaryCounters counters) {
        return counters != null ? counters.nodesCreated() : 0;
    }

    /**
     * Defensive helper to get relationship creation count from summary counters.
     */
    private static int safeRelCreates(SummaryCounters counters) {
        return counters != null ? counters.relationshipsCreated() : 0;
    }

    /**
     * Derives a minimal title from a Wikipedia URL slug (e.g., Graph_theory -> Graph theory).
     * Returns null if parsing fails to allow coalesce to preserve existing titles.
     */
    private static String deriveTitleFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            int idx = path.lastIndexOf('/');
            String slug = idx >= 0 ? path.substring(idx + 1) : path;
            if (slug.isBlank()) {
                return null;
            }
            String candidate = slug.replace('_', ' ').trim();
            if (candidate.isBlank()) {
                return null;
            }
            // Capitalize first letter only
            char first = candidate.charAt(0);
            char upper = Character.toUpperCase(first);
            if (first == upper) {
                return candidate;
            }
            return upper + candidate.substring(1);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * Runtime exception to indicate graph write failures.
     */
    public static class GraphWriteException extends RuntimeException {
        public GraphWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * DTO with aggregated counters from a writeGraph operation.
     */
    public static class GraphWriteResult {
        private final int nodesCreated;
        private final int relationshipsCreated;

        public GraphWriteResult(int nodesCreated, int relationshipsCreated) {
            this.nodesCreated = nodesCreated;
            this.relationshipsCreated = relationshipsCreated;
        }

        // PUBLIC_INTERFACE
        public int getNodesCreated() {
            return nodesCreated;
        }

        // PUBLIC_INTERFACE
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
