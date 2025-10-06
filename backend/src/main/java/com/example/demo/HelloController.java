package com.example.backend;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@Tag(name = "Hello Controller", description = "Basic endpoints for backend")
public class HelloController {
    
    @GetMapping("/")
    @Operation(summary = "Welcome endpoint", description = "Returns a welcome message")
    public String hello() {
        return "Hello, Spring Boot! Welcome to backend";
    }
    
    @GetMapping("/docs")
    @Operation(summary = "API Documentation", description = "Redirects to Swagger UI preserving original scheme/host/port")
    public RedirectView docs(HttpServletRequest request) {
        // Build an absolute URL based on the incoming request, honoring X-Forwarded-* headers
        String target = UriComponentsBuilder
                .fromHttpRequest(new ServletServerHttpRequest(request))
                .replacePath("/swagger-ui.html")
                .replaceQuery(null)
                .build()
                .toUriString();

        RedirectView rv = new RedirectView(target);
        // Use HTTP 1.1 compatible redirects when necessary (preserves 303/307 semantics if used)
        rv.setHttp10Compatible(false);
        return rv;
    }
    
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns application health status")
    public String health() {
        return "OK";
    }
    
    @GetMapping("/api/info")
    @Operation(summary = "Application info", description = "Returns application information")
    public String info() {
        return "Spring Boot Application: backend";
    }
} 