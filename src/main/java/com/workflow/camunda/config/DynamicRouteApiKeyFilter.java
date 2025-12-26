package com.workflow.camunda.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class DynamicRouteApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final DynamicRouteSecurityProperties securityProperties;

    public DynamicRouteApiKeyFilter(DynamicRouteSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/camel-routes");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String expected = securityProperties.getApiKey();
        if (expected == null || expected.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(API_KEY_HEADER);
        if (provided == null || !provided.equals(expected)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":\"Unauthorized\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
