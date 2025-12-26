package com.workflow.camunda.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import com.workflow.camunda.service.CamelDemoRouteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for testing Apache Camel routes directly (without Camunda).
 * 
 * Endpoints:
 * - GET /api/camel/integration - Test external API integration
 * - POST /api/camel/routing - Test content-based routing
 * - POST /api/camel/transform - Test data transformation
 * - GET /api/camel/orchestrate - Test microservices orchestration
 * - GET /api/camel/pipeline - Test pipeline pattern
 * - GET /api/camel/resilient - Test resilient service call
 */
@Slf4j
@RestController
@RequestMapping("/api/camel")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CamelController {

    private final ProducerTemplate producerTemplate;
    private final CamelDemoRouteService camelDemoRouteService;

    /**
     * Deploy demo routes so they become visible under /api/camel-routes and callable via direct: endpoints.
     *
     * POST /api/camel/demo/deploy
     * Body (optional): { "routeIds": ["callExternalApi", "routeMessage"] }
     */
    @PostMapping("/demo/deploy")
    public ResponseEntity<?> deployDemoRoutes(@RequestBody(required = false) Map<String, Object> body) {
        List<String> defaultRouteIds = List.of(
                "callExternalApi",
                "routeMessage",
                "transformJson",
                "orchestrate",
                "pipeline",
                "resilientCall"
        );

        List<String> routeIds = defaultRouteIds;
        if (body != null && body.get("routeIds") instanceof List<?> raw) {
            routeIds = raw.stream().map(String::valueOf).toList();
        }

        List<String> deployed = new java.util.ArrayList<>();
        Map<String, String> errors = new java.util.HashMap<>();

        for (String routeId : routeIds) {
            try {
                camelDemoRouteService.ensureDeployed(routeId);
                deployed.add(routeId);
            } catch (Exception e) {
                errors.put(routeId, e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", errors.isEmpty(),
                "deployed", deployed,
                "errors", errors
        ));
    }

    // ==================== Sample 1: Integration Routes ====================

    /**
     * Test external API call
     */
    @GetMapping("/integration")
    public ResponseEntity<?> testIntegration() {
        log.info("Testing integration route: callExternalApi");
        try {
            camelDemoRouteService.ensureDeployed("callExternalApi");
            String result = producerTemplate.requestBody("direct:callExternalApi", "", String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Integration route failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "route", "callExternalApi"));
        }
    }

    /**
     * Test API call with dynamic parameter
     */
    @GetMapping("/integration/{userId}")
    public ResponseEntity<?> testIntegrationWithParams(@PathVariable String userId) {
        log.info("Testing integration route with userId: {}", userId);
        try {
            camelDemoRouteService.ensureDeployed("callApiWithParams");
            Map<String, Object> headers = Map.of("userId", userId);
            String result = producerTemplate.requestBodyAndHeaders(
                    "direct:callApiWithParams", "", headers, String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Integration route failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "route", "callApiWithParams"));
        }
    }

    /**
     * Test POST to external API
     */
    @PostMapping("/integration/post")
    public ResponseEntity<?> testPostIntegration(@RequestBody Map<String, Object> body) {
        log.info("Testing POST integration: {}", body);
        try {
            camelDemoRouteService.ensureDeployed("postToExternalApi");
            String result = producerTemplate.requestBody(
                    "direct:postToExternalApi",
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body),
                    String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("POST integration route failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "route", "postToExternalApi"));
        }
    }

    // ==================== Sample 2: Message Routing ====================

    /**
     * Test content-based routing
     */
    @PostMapping("/routing")
    public ResponseEntity<?> testRouting(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "priority", defaultValue = "low") String priority) {
        log.info("Testing routing with priority: {} and body: {}", priority, body);
        try {
            camelDemoRouteService.ensureDeployed("routeMessage");
            Map<String, Object> headers = new HashMap<>();
            headers.put("priority", priority);
            String result = producerTemplate.requestBodyAndHeaders(
                    "direct:routeMessage", body, headers, String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Routing failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "route", "routeMessage"));
        }
    }

    /**
     * Test message filter
     */
    @PostMapping("/routing/filter")
    public ResponseEntity<?> testFilter(@RequestBody Map<String, Object> body) {
        log.info("Testing filter with body: {}", body);
        try {
            camelDemoRouteService.ensureDeployed("filterMessage");
            String result = producerTemplate.requestBody("direct:filterMessage", body, String.class);
            return ResponseEntity.ok(Map.of(
                    "input", body,
                    "result", result != null ? result : "Message filtered out (amount < 1000)"));
        } catch (Exception e) {
            log.error("Filter failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "route", "filterMessage"));
        }
    }

    /**
     * Test multicast
     */
    @PostMapping("/routing/multicast")
    public ResponseEntity<?> testMulticast(@RequestBody Map<String, Object> body) {
        log.info("Testing multicast with body: {}", body);
        try {
            camelDemoRouteService.ensureDeployed("multicast");
            String result = producerTemplate.requestBody("direct:multicast", body, String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Multicast failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "route", "multicast"));
        }
    }

    // ==================== Sample 3: Data Transformation ====================

    /**
     * Test JSON transformation
     */
    @PostMapping("/transform")
    public ResponseEntity<?> testTransform(@RequestBody String jsonBody) {
        log.info("Testing JSON transformation");
        try {
            camelDemoRouteService.ensureDeployed("transformJson");
            String result = producerTemplate.requestBody("direct:transformJson", jsonBody, String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Transform failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "route", "transformJson"));
        }
    }

    /**
     * Test Map to XML conversion
     */
    @PostMapping("/transform/xml")
    public ResponseEntity<?> testTransformToXml(@RequestBody Map<String, Object> body) {
        log.info("Testing Map to XML transformation");
        try {
            camelDemoRouteService.ensureDeployed("mapToXmlStructure");
            String result = producerTemplate.requestBody("direct:mapToXmlStructure", body, String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("XML Transform failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "route", "mapToXmlStructure"));
        }
    }

    /**
     * Test field mapping
     */
    @PostMapping("/transform/map-fields")
    public ResponseEntity<?> testMapFields(@RequestBody String jsonBody) {
        log.info("Testing field mapping");
        try {
            camelDemoRouteService.ensureDeployed("mapFields");
            String result = producerTemplate.requestBody("direct:mapFields", jsonBody, String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Field mapping failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "route", "mapFields"));
        }
    }

    // ==================== Sample 4: Microservices Orchestration
    // ====================

    /**
     * Test scatter-gather orchestration
     */
    @GetMapping("/orchestrate")
    public ResponseEntity<?> testOrchestrate() {
        log.info("Testing microservices orchestration");
        try {
            camelDemoRouteService.ensureDeployed("orchestrate");
            Object result = producerTemplate.requestBody("direct:orchestrate", "");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Orchestration failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "route", "orchestrate"));
        }
    }

    /**
     * Test pipeline pattern
     */
    @PostMapping("/pipeline")
    public ResponseEntity<?> testPipeline(@RequestBody Map<String, Object> body) {
        log.info("Testing pipeline pattern");
        try {
            camelDemoRouteService.ensureDeployed("pipeline");
            String result = producerTemplate.requestBody("direct:pipeline", body, String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Pipeline failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "route", "pipeline"));
        }
    }

    /**
     * Test resilient service call (with retry and fallback)
     */
    @GetMapping("/resilient")
    public ResponseEntity<?> testResilient() {
        log.info("Testing resilient service call");
        try {
            camelDemoRouteService.ensureDeployed("resilientCall");
            String result = producerTemplate.requestBody("direct:resilientCall", "", String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Resilient call failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "route", "resilientCall"));
        }
    }

    // ==================== Health Check ====================

    /**
     * List all available Camel routes
     */
    @GetMapping("/routes")
    public ResponseEntity<?> listRoutes() {
        Map<String, Object> routes = new HashMap<>();

        routes.put("integration", Map.of(
                "GET /api/camel/integration", "Call external API",
                "GET /api/camel/integration/{userId}", "Call API with user ID",
                "POST /api/camel/integration/post", "POST to external API"));

        routes.put("routing", Map.of(
                "POST /api/camel/routing", "Content-based routing (set 'priority' header)",
                "POST /api/camel/routing/filter", "Filter messages (pass {amount: 1000+})",
                "POST /api/camel/routing/multicast", "Multicast to multiple handlers"));

        routes.put("transform", Map.of(
                "POST /api/camel/transform", "Enrich JSON data",
                "POST /api/camel/transform/xml", "Convert Map to XML",
                "POST /api/camel/transform/map-fields", "Rename JSON fields"));

        routes.put("microservices", Map.of(
                "GET /api/camel/orchestrate", "Scatter-gather pattern",
                "POST /api/camel/pipeline", "Sequential pipeline",
                "GET /api/camel/resilient", "Retry with fallback"));

        return ResponseEntity.ok(routes);
    }

    // ==================== Saga Money Transfer ====================

    /**
     * Execute Saga-based money transfer
     * Example: POST /api/camel/saga/transfer
     * Body: { "sourceAccount": "ACC-001", "destAccount": "ACC-002", "amount": 500 }
     * 
     * To test rollback, use amount = 999
     */
    @PostMapping("/saga/transfer")
    public ResponseEntity<?> sagaTransfer(@RequestBody Map<String, Object> request) {
        log.info("SAGA Transfer request: {}", request);
        try {
            camelDemoRouteService.ensureDeployed("saga-transfer");
            String sourceAccount = (String) request.get("sourceAccount");
            String destAccount = (String) request.get("destAccount");
            Double amount = Double.valueOf(request.get("amount").toString());

            Map<String, Object> headers = new HashMap<>();
            headers.put("sourceAccount", sourceAccount);
            headers.put("destAccount", destAccount);
            headers.put("amount", amount);

            Object result = producerTemplate.requestBodyAndHeaders(
                    "direct:saga-transfer", "", headers);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("SAGA Transfer failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "status", "FAILED"));
        }
    }

    /**
     * Check account balance
     */
    @GetMapping("/saga/balance/{accountId}")
    public ResponseEntity<?> sagaBalance(@PathVariable String accountId) {
        log.info("SAGA: Checking balance for {}", accountId);
        try {
            camelDemoRouteService.ensureDeployed("saga-balance");
            Object result = producerTemplate.requestBodyAndHeaders(
                    "direct:saga-balance", "", Map.of("accountId", accountId));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("SAGA Balance check failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all accounts
     */
    @GetMapping("/saga/accounts")
    public ResponseEntity<?> sagaAccounts() {
        log.info("SAGA: Getting all accounts");
        try {
            camelDemoRouteService.ensureDeployed("saga-accounts");
            Object result = producerTemplate.requestBody("direct:saga-accounts", "");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("SAGA Get accounts failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
