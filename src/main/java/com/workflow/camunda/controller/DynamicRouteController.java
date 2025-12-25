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

    private final DynamicRouteService dynamicRouteService;
    private final ProducerTemplate producerTemplate;

    /**
     * Deploy a new route from JSON definition
     */
    @PostMapping
    public ResponseEntity<?> deployRoute(@RequestBody CamelRouteDefinition definition) {
        try {
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
    public ResponseEntity<List<CamelRouteDefinition>> getAllRoutes() {
        return ResponseEntity.ok(dynamicRouteService.getAllRoutes());
    }

    /**
     * Get a specific route by ID
     */
    @GetMapping("/{routeId}")
    public ResponseEntity<?> getRoute(@PathVariable String routeId) {
        return dynamicRouteService.getRoute(routeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a route
     */
    @DeleteMapping("/{routeId}")
    public ResponseEntity<?> deleteRoute(@PathVariable String routeId) {
        try {
            dynamicRouteService.deleteRoute(routeId);
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
    public ResponseEntity<?> startRoute(@PathVariable String routeId) {
        try {
            dynamicRouteService.startRoute(routeId);
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
    public ResponseEntity<?> stopRoute(@PathVariable String routeId) {
        try {
            dynamicRouteService.stopRoute(routeId);
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
            @PathVariable String routeId,
            @RequestBody(required = false) Object body) {
        try {
            // Find the route's from URI
            CamelRouteDefinition def = dynamicRouteService.getRoute(routeId)
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
