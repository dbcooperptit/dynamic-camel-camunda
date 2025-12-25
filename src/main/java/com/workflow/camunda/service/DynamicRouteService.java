package com.workflow.camunda.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.camunda.dto.CamelRouteDefinition;
import com.workflow.camunda.dto.CamelRouteDefinition.RouteNode;
import com.workflow.camunda.dto.CamelRouteDefinition.RouteEdge;
import com.workflow.camunda.dto.TaskEvent;
import com.workflow.camunda.service.AccountService;
import com.workflow.camunda.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Service;

import static org.apache.camel.builder.Builder.constant;
import static org.apache.camel.builder.Builder.simple;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.LoopDefinition;

/**
 * Service for dynamically creating and managing Camel routes at runtime.
 * Allows routes to be created from JSON definitions and injected into
 * CamelContext.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicRouteService {

    private final CamelContext camelContext;
    private final AccountService accountService;
    private final NotificationService notificationService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String EX_PROP_BODY_JSON = DynamicRouteService.class.getName() + ".bodyJson";

    // Store route definitions (in-memory, could be persisted to DB)
    private final Map<String, CamelRouteDefinition> routeDefinitions = new ConcurrentHashMap<>();

    // EIPs that create a scope and require children or .end()
    private static final Set<String> SCOPED_EIPS = Set.of(
            "filter", "split", "loop", "choice", "multicast", "recipientlist");

    /**
     * Helper class to represent route nodes as a tree structure
     */
    private static class RouteNodeTree {
        private final RouteNode node;
        private final List<RouteNodeTree> children;

        public RouteNodeTree(RouteNode node) {
            this.node = node;
            this.children = new ArrayList<>();
        }

        public RouteNode getNode() {
            return node;
        }

        public List<RouteNodeTree> getChildren() {
            return children;
        }

        public void addChild(RouteNodeTree child) {
            children.add(child);
        }
    }

    /**
     * Deploy a route definition to CamelContext
     */
    public CamelRouteDefinition deployRoute(CamelRouteDefinition definition) throws Exception {
        String routeId = definition.getId();

        if (routeId == null || routeId.isBlank()) {
            routeId = "dynamic-route-" + System.currentTimeMillis();
            definition.setId(routeId);
        }

        // Remove existing route if present
        if (camelContext.getRoute(routeId) != null) {
            removeRoute(routeId);
        }

        // Build and add route
        RouteBuilder routeBuilder = buildRouteFromDefinition(definition);
        camelContext.addRoutes(routeBuilder);

        // Mark as deployed
        definition.setStatus("DEPLOYED");
        routeDefinitions.put(routeId, definition);

        log.info("Deployed dynamic route: {} ({})", definition.getName(), routeId);
        return definition;
    }

    /**
     * Remove a route from CamelContext
     */
    public void removeRoute(String routeId) throws Exception {
        if (camelContext.getRoute(routeId) != null) {
            camelContext.getRouteController().stopRoute(routeId);
            camelContext.removeRoute(routeId);
            log.info("Removed route: {}", routeId);
        }

        CamelRouteDefinition def = routeDefinitions.get(routeId);
        if (def != null) {
            def.setStatus("STOPPED");
        }
    }

    /**
     * Start a stopped route
     */
    public void startRoute(String routeId) throws Exception {
        if (camelContext.getRoute(routeId) != null) {
            camelContext.getRouteController().startRoute(routeId);
            CamelRouteDefinition def = routeDefinitions.get(routeId);
            if (def != null) {
                def.setStatus("DEPLOYED");
            }
            log.info("Started route: {}", routeId);
        }
    }

    /**
     * Stop a running route
     */
    public void stopRoute(String routeId) throws Exception {
        if (camelContext.getRoute(routeId) != null) {
            camelContext.getRouteController().stopRoute(routeId);
            CamelRouteDefinition def = routeDefinitions.get(routeId);
            if (def != null) {
                def.setStatus("STOPPED");
            }
            log.info("Stopped route: {}", routeId);
        }
    }

    /**
     * Get all route definitions
     */
    public List<CamelRouteDefinition> getAllRoutes() {
        return new ArrayList<>(routeDefinitions.values());
    }

    /**
     * Get a specific route definition
     */
    public Optional<CamelRouteDefinition> getRoute(String routeId) {
        return Optional.ofNullable(routeDefinitions.get(routeId));
    }

    /**
     * Delete a route completely
     */
    public void deleteRoute(String routeId) throws Exception {
        removeRoute(routeId);
        routeDefinitions.remove(routeId);
        log.info("Deleted route: {}", routeId);
    }

    /**
     * Build a RouteBuilder from the visual definition
     */
    private RouteBuilder buildRouteFromDefinition(CamelRouteDefinition definition) {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Find the "from" node (source)
                RouteNode fromNode = definition.getNodes().stream()
                        .filter(n -> "from".equals(n.getType()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Route must have a 'from' node"));

                // Build adjacency map from edges
                Map<String, List<String>> adjacencyMap = new HashMap<>();
                for (RouteEdge edge : definition.getEdges()) {
                    adjacencyMap.computeIfAbsent(edge.getSource(), k -> new ArrayList<>())
                            .add(edge.getTarget());
                }

                // Create node lookup
                Map<String, RouteNode> nodeMap = definition.getNodes().stream()
                        .collect(Collectors.toMap(RouteNode::getId, n -> n));

                // Build route starting from "from" node
                RouteDefinition route = from(fromNode.getUri())
                        .routeId(definition.getId())
                        .description(definition.getDescription());

                // Get first level nodes after "from"
                List<String> nextNodeIds = adjacencyMap.getOrDefault(fromNode.getId(), Collections.emptyList());

                // Build tree structure for each child
                for (String nodeId : nextNodeIds) {
                    RouteNodeTree tree = buildNodeTree(nodeId, adjacencyMap, nodeMap, new HashSet<>());
                    applyNodeTreeToRoute(route, tree, adjacencyMap, nodeMap);
                }
            }
        };
    }

    /**
     * Build a tree structure from a flat graph starting at nodeId
     */
    private RouteNodeTree buildNodeTree(
            String nodeId,
            Map<String, List<String>> adjacencyMap,
            Map<String, RouteNode> nodeMap,
            Set<String> visited) {

        if (visited.contains(nodeId)) {
            return null; // Avoid cycles
        }
        visited.add(nodeId);

        RouteNode node = nodeMap.get(nodeId);
        if (node == null) {
            return null;
        }

        RouteNodeTree tree = new RouteNodeTree(node);

        // If this is a scoped EIP, its children need to be nested
        String nodeType = node.getType() != null ? node.getType().toLowerCase() : "";
        if (SCOPED_EIPS.contains(nodeType)) {
            // All outgoing edges are children of this scope
            List<String> childIds = adjacencyMap.getOrDefault(nodeId, Collections.emptyList());
            for (String childId : childIds) {
                RouteNodeTree childTree = buildNodeTree(childId, adjacencyMap, nodeMap, new HashSet<>(visited));
                if (childTree != null) {
                    tree.addChild(childTree);
                }
            }
        }

        return tree;
    }

    /**
     * Apply a node tree to the route, processing children recursively
     */
    private void applyNodeTreeToRoute(
            ProcessorDefinition<?> route,
            RouteNodeTree tree,
            Map<String, List<String>> adjacencyMap,
            Map<String, RouteNode> nodeMap) {

        if (tree == null) {
            return;
        }

        RouteNode node = tree.getNode();
        String nodeType = node.getType() != null ? node.getType().toLowerCase() : "";

        // Handle scoped EIPs that need children
        if (SCOPED_EIPS.contains(nodeType)) {
            applyScopedEIP(route, tree, adjacencyMap, nodeMap);
        } else {
            // Non-scoped EIPs - apply directly
            applyNodeToRoute(route, node);

            // Process next nodes sequentially (not as children)
            List<String> nextNodeIds = adjacencyMap.getOrDefault(node.getId(), Collections.emptyList());
            for (String nextId : nextNodeIds) {
                RouteNodeTree nextTree = buildNodeTree(nextId, adjacencyMap, nodeMap, new HashSet<>());
                applyNodeTreeToRoute(route, nextTree, adjacencyMap, nodeMap);
            }
        }
    }

    /**
     * Apply scoped EIPs (filter, split, loop, etc.) with proper child nesting
     */
    private void applyScopedEIP(
            ProcessorDefinition<?> route,
            RouteNodeTree tree,
            Map<String, List<String>> adjacencyMap,
            Map<String, RouteNode> nodeMap) {

        RouteNode node = tree.getNode();
        String nodeType = node.getType().toLowerCase();

        switch (nodeType) {
            case "filter":
                String filterExpr = node.getExpression() != null ? node.getExpression() : "${body} != null";
                FilterDefinition filterDef = ((RouteDefinition) route).filter(simple(filterExpr));

                // Process children inside filter scope
                for (RouteNodeTree child : tree.getChildren()) {
                    applyNodeTreeToRoute(filterDef, child, adjacencyMap, nodeMap);
                }

                filterDef.end(); // Close the scope
                break;

            case "split":
                String splitExpr = node.getExpression() != null ? node.getExpression() : "${body}";
                SplitDefinition splitDef = ((RouteDefinition) route).split(simple(splitExpr));

                // Process children inside split scope
                for (RouteNodeTree child : tree.getChildren()) {
                    applyNodeTreeToRoute(splitDef, child, adjacencyMap, nodeMap);
                }

                splitDef.end(); // Close the scope
                break;

            case "loop":
                String loopExpr = node.getExpression() != null ? node.getExpression() : "3";
                LoopDefinition loopDef;
                try {
                    int loopCount = Integer.parseInt(loopExpr);
                    loopDef = ((RouteDefinition) route).loop(loopCount);
                } catch (NumberFormatException e) {
                    loopDef = ((RouteDefinition) route).loop(simple(loopExpr));
                }

                // Process children inside loop scope
                for (RouteNodeTree child : tree.getChildren()) {
                    applyNodeTreeToRoute(loopDef, child, adjacencyMap, nodeMap);
                }

                loopDef.end(); // Close the scope
                break;

            default:
                // For other scoped EIPs, just log and continue
                log.warn("Scoped EIP '{}' not fully implemented yet, treating as regular node", nodeType);
                applyNodeToRoute((RouteDefinition) route, node);
        }
    }

    /**
     * Apply a node's configuration to the route (for non-scoped EIPs only)
     * Each node sends notification on completion or failure
     */
    private void applyNodeToRoute(ProcessorDefinition<?> route, RouteNode node) {
        String nodeType = node.getType().toLowerCase();
        String nodeId = node.getId();

        // Route ID from exchange property (set when route starts)
        switch (nodeType) {
            case "to":
                String toUri = node.getUri();
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    try {
                        // The actual "to" will be done by the next step
                        sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                                "Sending to: " + toUri, exchange.getIn().getBody(), null,
                                System.currentTimeMillis() - startTime);
                    } catch (Exception e) {
                        sendStepNotification(nodeId, nodeType, routeId, "FAILED",
                                "Failed to send to: " + toUri, null, e.getMessage(),
                                System.currentTimeMillis() - startTime);
                        throw e;
                    }
                });
                route.to(toUri);
                break;

            case "log":
                String message = node.getMessage() != null ? node.getMessage() : "Processing: ${body}";
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    String resolvedMsg = resolveSimpleExpression(exchange, message);
                    sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                            "Log: " + resolvedMsg, resolvedMsg, null,
                            System.currentTimeMillis() - startTime);
                });
                route.log(message);
                break;

            case "setbody":
                String expr = node.getExpression() != null ? node.getExpression() : "${body}";
                String lang = node.getExpressionLanguage();
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    Object oldBody = exchange.getIn().getBody();
                    try {
                        Object newBody;
                        if ("constant".equalsIgnoreCase(lang)) {
                            newBody = expr;
                            exchange.getIn().setBody(expr);
                        } else {
                            newBody = resolveSimpleExpression(exchange, expr);
                            exchange.getIn().setBody(newBody);
                        }
                        sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                                "Set body from expression: " + expr, newBody, null,
                                System.currentTimeMillis() - startTime);
                    } catch (Exception e) {
                        sendStepNotification(nodeId, nodeType, routeId, "FAILED",
                                "Failed to set body", oldBody, e.getMessage(),
                                System.currentTimeMillis() - startTime);
                        throw e;
                    }
                });
                break;

            case "transform":
                String transformExpr = node.getExpression() != null ? node.getExpression() : "${body}";
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    Object oldBody = exchange.getIn().getBody();
                    try {
                        String newBody = resolveSimpleExpression(exchange, transformExpr);
                        exchange.getIn().setBody(newBody);
                        sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                                "Transformed with: " + transformExpr, newBody, null,
                                System.currentTimeMillis() - startTime);
                    } catch (Exception e) {
                        sendStepNotification(nodeId, nodeType, routeId, "FAILED",
                                "Transform failed", oldBody, e.getMessage(),
                                System.currentTimeMillis() - startTime);
                        throw e;
                    }
                });
                break;

            case "delay":
                Object delayVal = node.getProperties() != null ? node.getProperties().get("delay") : 1000;
                long delay = delayVal instanceof Number ? ((Number) delayVal).longValue() : 1000L;
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                            "Delay: " + delay + "ms", delay, null,
                            System.currentTimeMillis() - startTime);
                });
                route.delay(delay);
                break;

            case "convertbodyto":
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    Object oldBody = exchange.getIn().getBody();
                    try {
                        String converted = exchange.getIn().getBody(String.class);
                        exchange.getIn().setBody(converted);
                        sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                                "Converted body to String", converted, null,
                                System.currentTimeMillis() - startTime);
                    } catch (Exception e) {
                        sendStepNotification(nodeId, nodeType, routeId, "FAILED",
                                "Convert failed", oldBody, e.getMessage(),
                                System.currentTimeMillis() - startTime);
                        throw e;
                    }
                });
                break;

            case "aggregate":
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                            "Aggregate node executed", exchange.getIn().getBody(), null,
                            System.currentTimeMillis() - startTime);
                });
                route.log("Aggregate node: ${body}");
                break;

            case "multicast":
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                            "Multicast node executed", exchange.getIn().getBody(), null,
                            System.currentTimeMillis() - startTime);
                });
                route.log("Multicast node: ${body}");
                break;

            case "enrich":
                String enrichUri = node.getUri() != null ? node.getUri() : "direct:enricher";
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                            "Enriching from: " + enrichUri, exchange.getIn().getBody(), null,
                            System.currentTimeMillis() - startTime);
                });
                route.enrich(enrichUri);
                break;

            case "throttle":
                String throttleExpr = node.getExpression() != null ? node.getExpression() : "10";
                int rate = 10;
                try {
                    rate = Integer.parseInt(throttleExpr);
                } catch (NumberFormatException e) {
                    // use default
                }
                final int finalRate = rate;
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                            "Throttle: " + finalRate + " per second", finalRate, null,
                            System.currentTimeMillis() - startTime);
                });
                route.throttle(finalRate);
                break;

            case "wiretap":
                String wiretapUri = node.getUri() != null ? node.getUri() : "log:wiretap";
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                            "Wire tap to: " + wiretapUri, exchange.getIn().getBody(), null,
                            System.currentTimeMillis() - startTime);
                });
                route.wireTap(wiretapUri);
                break;

            // ====== SAGA NODES ======
            case "debit":
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    String account = extractValue(node, exchange, "accountNumber", "sourceAccount", null);
                    if (account == null)
                        throw new IllegalArgumentException("Account Number is required for Debit");

                    String amountStr = extractValue(node, exchange, "amount", "amount", "0");
                    java.math.BigDecimal amount = new java.math.BigDecimal(amountStr);

                    String txnId = exchange.getIn().getHeader("transactionId", String.class);
                    try {
                        log.info("💸 SAGA DEBIT: {} - {} (TxnId: {})", account, amount, txnId);
                        accountService.debit(account, amount, txnId);
                        exchange.getIn().setHeader("sagaState", "DEBITED");

                        sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                                "Debited " + amount + " from " + account,
                                Map.of("account", account, "amount", amount, "txnId", txnId), null,
                                System.currentTimeMillis() - startTime);
                    } catch (Exception e) {
                        sendStepNotification(nodeId, nodeType, routeId, "FAILED",
                                "Debit failed for " + account, null, e.getMessage(),
                                System.currentTimeMillis() - startTime);
                        throw e;
                    }
                });
                break;

            case "credit":
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    String account = extractValue(node, exchange, "accountNumber", "destAccount", null);
                    if (account == null)
                        throw new IllegalArgumentException("Account Number is required for Credit");

                    String amountStr = extractValue(node, exchange, "amount", "amount", "0");
                    java.math.BigDecimal amount = new java.math.BigDecimal(amountStr);

                    String txnId = exchange.getIn().getHeader("transactionId", String.class);
                    try {
                        log.info("💰 SAGA CREDIT: {} + {} (TxnId: {})", account, amount, txnId);
                        accountService.credit(account, amount, txnId);
                        exchange.getIn().setHeader("sagaState", "CREDITED");

                        sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                                "Credited " + amount + " to " + account,
                                Map.of("account", account, "amount", amount, "txnId", txnId), null,
                                System.currentTimeMillis() - startTime);
                    } catch (Exception e) {
                        sendStepNotification(nodeId, nodeType, routeId, "FAILED",
                                "Credit failed for " + account, null, e.getMessage(),
                                System.currentTimeMillis() - startTime);
                        throw e;
                    }
                });
                break;

            case "sagatransfer":
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    String sourceAccount = extractValue(node, exchange, "sourceAccount", "sourceAccount", null);
                    String destAccount = extractValue(node, exchange, "destAccount", "destAccount", null);

                    String amountStr = extractValue(node, exchange, "amount", "amount", "0");
                    java.math.BigDecimal amount = new java.math.BigDecimal(amountStr);

                    String description = extractValue(node, exchange, "description", "description", "Dynamic transfer");

                    try {
                        log.info("🔄 SAGA TRANSFER: {} → {} ({})", sourceAccount, destAccount, amount);
                        String txnId = accountService.executeTransfer(sourceAccount, destAccount, amount, description);
                        exchange.getIn().setHeader("transactionId", txnId);
                        exchange.getIn().setHeader("transferStatus", "COMPLETED");

                        sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                                "Transferred " + amount + " from " + sourceAccount + " to " + destAccount,
                                Map.of("sourceAccount", sourceAccount, "destAccount", destAccount,
                                        "amount", amount, "txnId", txnId),
                                null,
                                System.currentTimeMillis() - startTime);
                    } catch (Exception e) {
                        sendStepNotification(nodeId, nodeType, routeId, "FAILED",
                                "Transfer failed", null, e.getMessage(),
                                System.currentTimeMillis() - startTime);
                        throw e;
                    }
                });
                break;

            case "compensate":
                route.process(exchange -> {
                    long startTime = System.currentTimeMillis();
                    String routeId = exchange.getFromRouteId();
                    String account = extractValue(node, exchange, "accountNumber", "sourceAccount", null);

                    String amountStr = extractValue(node, exchange, "amount", "amount", "0");
                    java.math.BigDecimal amount = new java.math.BigDecimal(amountStr);

                    String txnId = exchange.getIn().getHeader("transactionId", String.class);
                    try {
                        log.warn("↩️ SAGA COMPENSATE: {} + {} (TxnId: {})", account, amount, txnId);
                        accountService.compensateDebit(account, amount, txnId);
                        exchange.getIn().setHeader("sagaState", "COMPENSATED");

                        sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                                "Compensated/Rolled back " + amount + " for " + account,
                                Map.of("account", account, "amount", amount, "txnId", txnId), null,
                                System.currentTimeMillis() - startTime);
                    } catch (Exception e) {
                        sendStepNotification(nodeId, nodeType, routeId, "FAILED",
                                "Compensation failed for " + account, null, e.getMessage(),
                                System.currentTimeMillis() - startTime);
                        throw e;
                    }
                });
                break;

            default:
                log.warn("Unknown node type: {}", node.getType());
                route.process(exchange -> {
                    String routeId = exchange.getFromRouteId();
                    sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                            "Unknown node type executed: " + nodeType, null, null, 0L);
                });
        }
    }

    /**
     * Send step notification via NotificationService
     */
    private void sendStepNotification(String nodeId, String nodeType, String routeId,
            String status, String message, Object result,
            String error, Long durationMs) {
        try {
            notificationService.sendEvent(TaskEvent.builder()
                    .taskId(nodeId)
                    .type("CAMEL_NODE")
                    .nodeType(nodeType)
                    .routeId(routeId)
                    .status(status)
                    .message(message)
                    .result(result)
                    .error(error)
                    .durationMs(durationMs)
                    .timestamp(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to send step notification: {}", e.getMessage());
        }
    }

    /**
     * Resolve simple expression using Camel context
     */
    private String resolveSimpleExpression(Exchange exchange, String expression) {
        if (expression != null && expression.contains("${")) {
            try {
                return camelContext.resolveLanguage("simple")
                        .createExpression(expression)
                        .evaluate(exchange, String.class);
            } catch (Exception e) {
                log.warn("Failed to resolve expression: {}", expression);
                return expression;
            }
        }
        return expression;
    }

    /**
     * Helper to extract a value from:
     * 1. Node Property (if configured)
     * 2. Body (JSON/Map/POJO in exchange.in.body, using propertyKey as lookup)
     * 3. Header (using fallbackHeaderKey)
     * 4. Default Value
     */
    private String extractValue(RouteNode node, Exchange exchange, String propertyKey, String fallbackHeaderKey,
            String defaultValue) {
        // 1. Check Node Configuration
        if (node.getProperties() != null && node.getProperties().containsKey(propertyKey)) {
            String configValue = String.valueOf(node.getProperties().get(propertyKey));
            if (configValue != null && !configValue.isBlank()) {
                return resolveProperty(exchange, configValue);
            }
        }

        // 2. Check Body (exchange.in.body)
        // Prefer a simple JSON/Map/POJO lookup over Camel Simple expressions.
        String bodyValue = extractFromBody(exchange, propertyKey);
        if (bodyValue != null && !bodyValue.isBlank() && !"null".equals(bodyValue)) {
            return bodyValue;
        }

        // 3. Check Header (fallback)
        if (fallbackHeaderKey != null) {
            String headerValue = exchange.getIn().getHeader(fallbackHeaderKey, String.class);
            if (headerValue != null)
                return headerValue;
        }

        // 4. Default
        return defaultValue;
    }

    private String extractFromBody(Exchange exchange, String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) {
            return null;
        }

        JsonNode root = exchange.getProperty(EX_PROP_BODY_JSON, JsonNode.class);
        if (root == null) {
            root = parseBodyAsJsonNode(exchange);
            if (root != null) {
                exchange.setProperty(EX_PROP_BODY_JSON, root);
            }
        }

        if (root == null) {
            return null;
        }

        JsonNode current = root;
        // Support simple dot notation: "a.b.c"
        for (String part : propertyKey.split("\\.")) {
            if (part.isBlank()) {
                continue;
            }
            current = current.path(part);
            if (current.isMissingNode() || current.isNull()) {
                return null;
            }
        }

        if (current.isMissingNode() || current.isNull()) {
            return null;
        }
        return current.isValueNode() ? current.asText() : current.toString();
    }

    private JsonNode parseBodyAsJsonNode(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            return null;
        }
        if (body instanceof JsonNode jsonNode) {
            return jsonNode;
        }

        try {
            if (body instanceof String s) {
                if (s.isBlank()) {
                    return null;
                }
                // If it's a JSON string, parse it.
                return OBJECT_MAPPER.readTree(s);
            }
            if (body instanceof byte[] bytes) {
                return OBJECT_MAPPER.readTree(bytes);
            }

            // Map / POJO -> JsonNode
            return OBJECT_MAPPER.valueToTree(body);
        } catch (Exception e) {
            // Not JSON or unparsable body -> ignore and fallback to headers/default
            return null;
        }
    }

    /**
     * Resolve property value, evaluating Simple expressions if needed
     */
    private String resolveProperty(Exchange exchange, String value) {
        if (value != null && value.contains("${")) {
            try {
                return camelContext.resolveLanguage("simple").createExpression(value).evaluate(exchange, String.class);
            } catch (Exception e) {
                log.warn("Failed to evaluate simple expression: {}", value, e);
                return value;
            }
        }
        return value;
    }
}
