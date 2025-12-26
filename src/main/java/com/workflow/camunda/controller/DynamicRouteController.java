package com.workflow.camunda.controller;

import com.workflow.camunda.dto.CamelRouteDefinition;
import com.workflow.camunda.service.DynamicRouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing dynamic Camel routes.
 * Allows creating, deploying, and testing routes via API.
 */
@Slf4j
@RestController
@RequestMapping("/api/camel-routes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DynamicRouteController {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String DEFAULT_TENANT = "default";

    private final DynamicRouteService dynamicRouteService;
    private final ProducerTemplate producerTemplate;

    /**
     * Deploy a new route from JSON definition
     */
    @PostMapping
    public ResponseEntity<?> deployRoute(
            @RequestHeader(value = TENANT_HEADER, required = false) String tenantId,
            @RequestBody CamelRouteDefinition definition) {
        try {
            String tid = (tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT : tenantId.trim();
            if (definition.getTenantId() == null || definition.getTenantId().isBlank()) {
                definition.setTenantId(tid);
            }
            log.info("Deploying route: {} ({})", definition.getName(), definition.getId());
            CamelRouteDefinition deployed = dynamicRouteService.deployRoute(definition);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Route deployed successfully",
                    "route", deployed));
        } catch (Exception e) {
            log.error("Failed to deploy route", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /**
     * Get all deployed routes
     */
    @GetMapping
    public ResponseEntity<List<CamelRouteDefinition>> getAllRoutes(
            @RequestHeader(value = TENANT_HEADER, required = false) String tenantId) {
        String tid = (tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT : tenantId.trim();
        return ResponseEntity.ok(dynamicRouteService.getAllRoutes(tid));
    }

    /**
     * Get a specific route by ID
     */
    @GetMapping("/{routeId}")
    public ResponseEntity<?> getRoute(
            @RequestHeader(value = TENANT_HEADER, required = false) String tenantId,
            @PathVariable String routeId) {
        String tid = (tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT : tenantId.trim();
        return dynamicRouteService.getRoute(routeId, tid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a route
     */
    @DeleteMapping("/{routeId}")
    public ResponseEntity<?> deleteRoute(
            @RequestHeader(value = TENANT_HEADER, required = false) String tenantId,
            @PathVariable String routeId) {
        try {
            String tid = (tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT : tenantId.trim();
            dynamicRouteService.deleteRoute(routeId, tid);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Route deleted: " + routeId));
        } catch (Exception e) {
            log.error("Failed to delete route", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /**
     * Start a stopped route
     */
    @PostMapping("/{routeId}/start")
    public ResponseEntity<?> startRoute(
            @RequestHeader(value = TENANT_HEADER, required = false) String tenantId,
            @PathVariable String routeId) {
        try {
            String tid = (tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT : tenantId.trim();
            dynamicRouteService.startRoute(routeId, tid);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Route started: " + routeId));
        } catch (Exception e) {
            log.error("Failed to start route", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /**
     * Stop a running route
     */
    @PostMapping("/{routeId}/stop")
    public ResponseEntity<?> stopRoute(
            @RequestHeader(value = TENANT_HEADER, required = false) String tenantId,
            @PathVariable String routeId) {
        try {
            String tid = (tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT : tenantId.trim();
            dynamicRouteService.stopRoute(routeId, tid);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Route stopped: " + routeId));
        } catch (Exception e) {
            log.error("Failed to stop route", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /**
     * Test a deployed route by sending a message
     */
    @PostMapping("/{routeId}/test")
    public ResponseEntity<?> testRoute(
            @RequestHeader(value = TENANT_HEADER, required = false) String tenantId,
            @PathVariable String routeId,
            @RequestBody(required = false) Object body) {
        try {
            String tid = (tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT : tenantId.trim();
            // Find the route's from URI
            CamelRouteDefinition def = dynamicRouteService.getRoute(routeId, tid)
                    .orElseThrow(() -> new IllegalArgumentException("Route not found: " + routeId));

            // Find the from node to get the endpoint
            String fromUri = def.getNodes().stream()
                    .filter(n -> "from".equals(n.getType()))
                    .findFirst()
                    .map(CamelRouteDefinition.RouteNode::getUri)
                    .orElseThrow(() -> new IllegalArgumentException("Route has no 'from' node"));

            // Send test message
            Object result = producerTemplate.requestBody(fromUri, body != null ? body : "Test message");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "routeId", routeId,
                    "input", body != null ? body : "Test message",
                    "output", result != null ? result : "null"));
        } catch (Exception e) {
            log.error("Failed to test route", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /**
     * Quick test endpoint - send to any direct: endpoint
     */
    @PostMapping("/test/{endpointName}")
    public ResponseEntity<?> quickTest(
            @PathVariable String endpointName,
            @RequestBody(required = false) Object body) {
        try {
            String uri = "direct:" + endpointName;
            Object result = producerTemplate.requestBody(uri, body != null ? body : "");
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "endpoint", uri,
                    "result", result != null ? result : "null"));
        } catch (Exception e) {
            log.error("Quick test failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }
}
