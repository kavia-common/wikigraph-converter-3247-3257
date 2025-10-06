package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request payload for the convert endpoint.
 * Carries the Wikipedia page URL to be converted to Neo4j graph data.
 */
@Schema(name = "ConvertRequest", description = "Request payload containing the source Wikipedia page URL to convert.")
public class ConvertRequest {

    @Schema(description = "The Wikipedia page URL to convert.", example = "https://en.wikipedia.org/wiki/Graph_theory", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "url must not be blank")
    @Pattern(
        regexp = "^(https?://).+",
        message = "url must start with http:// or https://"
    )
    private String url;

    /** Default constructor for frameworks. */
    public ConvertRequest() {
        // For deserialization
    }

    /** All-args constructor. */
    @JsonCreator
    public ConvertRequest(@JsonProperty("url") String url) {
        this.url = url;
    }

    // PUBLIC_INTERFACE
    /**
     * Static factory for creating a request with the given URL.
     * @param url the Wikipedia page URL
     * @return a new ConvertRequest
     */
    public static ConvertRequest of(String url) {
        return new ConvertRequest(url);
    }

    // Getters and Setters
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    // Fluent/builder-style convenience
    public ConvertRequest withUrl(String url) {
        this.url = url;
        return this;
    }

    @Override
    public String toString() {
        return "ConvertRequest{url='" + url + "'}";
    }
}
