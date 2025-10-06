package com.example.backend.controller;

import com.example.backend.dto.ConvertRequest;
import com.example.backend.dto.ConvertResponse;
import com.example.backend.service.GraphWriterService;
import com.example.backend.service.WikiParseService;
import com.example.backend.service.GraphWriterService.GraphWriteResult;
import com.example.backend.service.WikiParseService.ParsedResult;
import com.example.backend.service.WikiParseService.WikiParseException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller providing an endpoint to convert a Wikipedia page into Neo4j graph data.
 *
 * Endpoint: POST /api/convert
 * Request body: ConvertRequest { url }
 * Validates that the URL uses https and the host ends with wikipedia.org (supports subdomains).
 * On success, parses the page via WikiParseService and writes graph data using GraphWriterService.
 * Returns a ConvertResponse with summary (nodesCreated, relationshipsCreated, title).
 */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Conversion", description = "Endpoints for converting Wikipedia pages to Neo4j graph data")
public class ConvertController {

    private final WikiParseService wikiParseService;
    private final GraphWriterService graphWriterService;

    public ConvertController(WikiParseService wikiParseService, GraphWriterService graphWriterService) {
        this.wikiParseService = Objects.requireNonNull(wikiParseService, "wikiParseService must not be null");
        this.graphWriterService = Objects.requireNonNull(graphWriterService, "graphWriterService must not be null");
    }

    // PUBLIC_INTERFACE
    /**
     * Convert a Wikipedia page URL into graph data in Neo4j.
     *
     * Validation:
     *  - URL must be a valid URI
     *  - https scheme is required
     *  - host must end with wikipedia.org (supports language subdomains)
     *
     * Flow:
     *  1) Validate URL
     *  2) Parse page (title, links)
     *  3) Write graph (main page node and links)
     *  4) Return ConvertResponse with summary
     *
     * @param request the request containing the Wikipedia URL
     * @return ResponseEntity with ConvertResponse and appropriate status codes
     */
    @PostMapping(path = "/convert", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Convert a Wikipedia page to Neo4j graph",
        description = "Validates input URL, parses Wikipedia page to extract title and links, and writes a simple graph to Neo4j. "
            + "Requires the URL to use https and host to be wikipedia.org or a subdomain.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Conversion completed successfully",
                content = @Content(schema = @Schema(implementation = ConvertResponse.class))
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid request (URL validation failed)",
                content = @Content(schema = @Schema(implementation = ConvertResponse.class))
            ),
            @ApiResponse(
                responseCode = "500",
                description = "Internal error during parsing or graph writing",
                content = @Content(schema = @Schema(implementation = ConvertResponse.class))
            )
        }
    )
    public ResponseEntity<ConvertResponse> convert(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "JSON body containing the Wikipedia page URL",
            content = @Content(schema = @Schema(implementation = ConvertRequest.class))
        )
        @Valid @RequestBody ConvertRequest request,
        @Parameter(hidden = true, in = ParameterIn.HEADER) @RequestHeader(value = "X-Request-ID", required = false) String requestId
    ) {
        String url = request != null ? request.getUrl() : null;
        try {
            String validationError = validateWikipediaUrl(url);
            if (validationError != null) {
                return ResponseEntity.badRequest().body(ConvertResponse.error(validationError));
            }

            // 1) Parse
            ParsedResult parseResult = wikiParseService.parse(url);

            // 2) Write graph
            GraphWriteResult counters = graphWriterService.writeGraph(
                url,
                parseResult.getTitle(),
                parseResult.getLinkedUrls()
            );

            // 3) Build response summary
            ConvertResponse.Summary summary = ConvertResponse.Summary.of(
                counters.getNodesCreated(),
                counters.getRelationshipsCreated(),
                parseResult.getTitle()
            );

            ConvertResponse response = ConvertResponse.success("Conversion completed", summary);
            return ResponseEntity.ok(response);

        } catch (WikiParseException e) {
            // Parsing/validation specific issues from service -> treat as server error per requirements
            return ResponseEntity.status(500).body(ConvertResponse.error("Parsing error: " + e.getMessage()));
        } catch (GraphWriterService.GraphWriteException e) {
            return ResponseEntity.status(500).body(ConvertResponse.error("Neo4j write error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ConvertResponse.error("Unexpected error: " + e.getMessage()));
        }
    }

    /**
     * Validates that the URL is https and the host endsWith wikipedia.org.
     * Returns null if valid, otherwise an error message.
     */
    private String validateWikipediaUrl(String url) {
        if (url == null || url.isBlank()) {
            return "url must not be blank";
        }
        final URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return "url is not a valid URI: " + url;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            return "url must use https scheme";
        }
        String host = uri.getHost();
        if (host == null || !host.toLowerCase(Locale.ROOT).endsWith("wikipedia.org")) {
            return "url must be a wikipedia.org host";
        }
        return null;
    }
}
