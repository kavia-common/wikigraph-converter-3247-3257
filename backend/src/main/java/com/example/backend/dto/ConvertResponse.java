package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload for the convert endpoint.
 * Conveys operation status, optional message, and a summary of created graph items.
 */
@Schema(name = "ConvertResponse", description = "Response payload that includes the conversion status and a summary of created nodes and relationships.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConvertResponse {

    @Schema(description = "Operation status indicator. Common values: SUCCESS, ERROR.", example = "SUCCESS", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(description = "Optional informational or error message.", example = "Converted successfully.")
    private String message;

    @Schema(description = "Summary of created graph entities.")
    private Summary summary;

    /** Default constructor for frameworks. */
    public ConvertResponse() {
        // For deserialization
    }

    /** All-args constructor. */
    public ConvertResponse(String status, String message, Summary summary) {
        this.status = status;
        this.message = message;
        this.summary = summary;
    }

    // PUBLIC_INTERFACE
    /**
     * Convenience factory for successful responses with summary.
     * @param summary summary of the conversion process
     * @return a success ConvertResponse
     */
    public static ConvertResponse success(Summary summary) {
        return new ConvertResponse("SUCCESS", null, summary);
    }

    // PUBLIC_INTERFACE
    /**
     * Convenience factory for successful responses with message and summary.
     * @param message optional success message
     * @param summary summary of the conversion process
     * @return a success ConvertResponse
     */
    public static ConvertResponse success(String message, Summary summary) {
        return new ConvertResponse("SUCCESS", message, summary);
    }

    // PUBLIC_INTERFACE
    /**
     * Convenience factory for error responses.
     * @param message error message to include
     * @return an error ConvertResponse
     */
    public static ConvertResponse error(String message) {
        return new ConvertResponse("ERROR", message, null);
    }

    // Getters and Setters
    public String getStatus() {
        return status;
    }

    public ConvertResponse setStatus(String status) {
        this.status = status;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public ConvertResponse setMessage(String message) {
        this.message = message;
        return this;
    }

    public Summary getSummary() {
        return summary;
    }

    public ConvertResponse setSummary(Summary summary) {
        this.summary = summary;
        return this;
    }

    @Override
    public String toString() {
        return "ConvertResponse{status='" + status + "', message='" + message + "', summary=" + summary + '}';
    }

    /**
     * Summary of the conversion outcome.
     */
    @Schema(name = "ConvertResponseSummary", description = "Summary of graph entities created by the conversion.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Summary {

        @Schema(description = "Number of nodes created.", example = "42")
        @JsonProperty("nodesCreated")
        private Integer nodesCreated;

        @Schema(description = "Number of relationships created.", example = "87")
        @JsonProperty("relationshipsCreated")
        private Integer relationshipsCreated;

        @Schema(description = "A human-readable title for the converted page.", example = "Graph theory")
        @JsonProperty("title")
        private String title;

        /** Default constructor for frameworks. */
        public Summary() {
        }

        /** All-args constructor. */
        public Summary(Integer nodesCreated, Integer relationshipsCreated, String title) {
            this.nodesCreated = nodesCreated;
            this.relationshipsCreated = relationshipsCreated;
            this.title = title;
        }

        // PUBLIC_INTERFACE
        /**
         * Static factory for building a summary instance.
         * @param nodesCreated nodes created
         * @param relationshipsCreated relationships created
         * @param title title of the page
         * @return a new Summary
         */
        public static Summary of(Integer nodesCreated, Integer relationshipsCreated, String title) {
            return new Summary(nodesCreated, relationshipsCreated, title);
        }

        // Getters and Setters
        public Integer getNodesCreated() {
            return nodesCreated;
        }

        public Summary setNodesCreated(Integer nodesCreated) {
            this.nodesCreated = nodesCreated;
            return this;
        }

        public Integer getRelationshipsCreated() {
            return relationshipsCreated;
        }

        public Summary setRelationshipsCreated(Integer relationshipsCreated) {
            this.relationshipsCreated = relationshipsCreated;
            return this;
        }

        public String getTitle() {
            return title;
        }

        public Summary setTitle(String title) {
            this.title = title;
            return this;
        }

        @Override
        public String toString() {
            return "Summary{nodesCreated=" + nodesCreated + ", relationshipsCreated=" + relationshipsCreated + ", title='" + title + "'}";
        }
    }
}
