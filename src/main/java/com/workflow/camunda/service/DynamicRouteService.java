package com.workflow.camunda.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.camunda.config.DynamicRouteSecurityProperties;
import com.workflow.camunda.dto.CamelRouteDefinition;
import com.workflow.camunda.dto.CamelRouteDefinition.RouteNode;
import com.workflow.camunda.dto.CamelRouteDefinition.RouteEdge;
import com.workflow.camunda.dto.TaskEvent;
import com.workflow.camunda.entity.CamelRouteEntity;
import com.workflow.camunda.entity.Transaction;
import com.workflow.camunda.repository.CamelRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.model.*;
import org.springframework.stereotype.Service;

import static org.apache.camel.builder.Builder.simple;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.net.URI;

import jakarta.annotation.PostConstruct;

/**
 * Service for dynamically creating and managing Camel routes at runtime.
 * Allows routes to be created from JSON definitions and injected into
 * CamelContext.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicRouteService {

    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String DEFAULT_TENANT_ID = "default";

    private final CamelContext camelContext;
    private final AccountService accountService;
    private final NotificationService notificationService;

    private final CamelRouteRepository camelRouteRepository;
    private final ObjectMapper objectMapper;

    private final DynamicRouteSecurityProperties dynamicRouteSecurityProperties;

    private static final String EX_PROP_BODY_JSON = DynamicRouteService.class.getName() + ".bodyJson";

    // Store route definitions in-memory keyed by an internal route key:
    // <tenantId>::<logicalRouteId>
    // This guarantees uniqueness across tenants and matches the Camel routeId used
    // at runtime.
    private final Map<String, CamelRouteDefinition> routeDefinitions = new ConcurrentHashMap<>();

    // Serialize mutations per route id (deploy/start/stop/remove/delete)
    private final Map<String, Object> routeLocks = new ConcurrentHashMap<>();

    // EIPs that create a scope and require children or .end()
    private static final Set<String> SCOPED_EIPS = Set.of(
            "filter", "split", "loop", "choice", "trycatch", "multicast", "recipientlist");

    @PostConstruct
    public void loadPersistedRoutes() {
        try {
            List<CamelRouteEntity> entities = camelRouteRepository.findAll();
            if (entities.isEmpty()) {
                return;
            }

            long deployedCount = 0;
            for (CamelRouteEntity entity : entities) {
                try {
                    CamelRouteDefinition def = objectMapper.readValue(entity.getDefinitionJson(),
                            CamelRouteDefinition.class);

                    // Entity id is the INTERNAL key used for Camel runtime routeId.
                    // The JSON definition id is the LOGICAL route id (what the UI uses).
                    String tenantId = (entity.getTenantId() == null || entity.getTenantId().isBlank())
                            ? DEFAULT_TENANT_ID
                            : entity.getTenantId().trim();
                    if (def.getTenantId() == null || def.getTenantId().isBlank()) {
                        def.setTenantId(tenantId);
                    }

                    // Backward compatibility: older persisted rows used entity.id == logical
                    // routeId.
                    // Migrate to tenant-scoped internal key when possible.
                    String internalKey = entity.getId();
                    if (internalKey != null && !internalKey.contains("::")) {
                        String legacyId = internalKey;
                        String migratedKey = routeKey(tenantId, internalKey);
                        try {
                            if (!camelRouteRepository.existsById(migratedKey)) {
                                CamelRouteEntity migrated = CamelRouteEntity.builder()
                                        .id(migratedKey)
                                        .name(entity.getName())
                                        .tenantId(tenantId)
                                        .description(entity.getDescription())
                                        .definitionJson(entity.getDefinitionJson())
                                        .status(entity.getStatus())
                                        .build();
                                camelRouteRepository.save(migrated);
                                camelRouteRepository.deleteById(legacyId);
                                internalKey = migratedKey;
                                entity = migrated;
                                log.info("Migrated legacy camel_routes id '{}' -> '{}' (tenant: {})", legacyId,
                                        migratedKey, tenantId);
                            } else {
                                internalKey = migratedKey;
                            }
                        } catch (Exception migrateEx) {
                            // Best-effort only.
                            internalKey = routeKey(tenantId, internalKey);
                        }
                    }

                    if (def.getStatus() == null) {
                        def.setStatus(entity.getStatus());
                    }

                    if (def.getSchemaVersion() == null) {
                        def.setSchemaVersion(CURRENT_SCHEMA_VERSION);
                    }

                    def = migrateIfNeeded(def);

                    routeDefinitions.put(internalKey, def);

                    if ("DEPLOYED".equalsIgnoreCase(entity.getStatus())) {
                        deployToCamelContext(def, internalKey);
                        deployedCount++;
                    }
                } catch (Exception parseOrDeployEx) {
                    log.warn("Failed to load persisted Camel route '{}': {}", entity.getId(),
                            parseOrDeployEx.getMessage());
                }
            }

            log.info("Loaded {} persisted Camel route definitions (deployed: {})", entities.size(), deployedCount);
        } catch (Exception e) {
            log.warn("Failed to load persisted Camel routes: {}", e.getMessage());
        }
    }

    private CamelRouteDefinition migrateIfNeeded(CamelRouteDefinition def) {
        // Backward-compatible migration hook.
        // CURRENT_SCHEMA_VERSION is intentionally low right now; as you evolve the
        // JSON, add migrations here.
        Integer v = def.getSchemaVersion();
        if (v == null) {
            def.setSchemaVersion(CURRENT_SCHEMA_VERSION);
            return def;
        }
        if (v > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported route schemaVersion: " + v + " (server supports: " + CURRENT_SCHEMA_VERSION + ")");
        }
        // v < CURRENT_SCHEMA_VERSION migrations would go here.
        def.setSchemaVersion(CURRENT_SCHEMA_VERSION);
        return def;
    }

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
        validateDefinition(definition);
        String tenantId = normalizeTenant(definition.getTenantId());
        String logicalRouteId = definition.getId();
        if (logicalRouteId == null || logicalRouteId.isBlank()) {
            logicalRouteId = "dynamic-route-" + System.currentTimeMillis();
            definition.setId(logicalRouteId);
        }

        String internalKey = routeKey(tenantId, logicalRouteId);
        synchronized (routeLock(internalKey)) {
            // Deploy to Camel (with rollback on failure)
            deployToCamelContext(definition, internalKey);

            // Mark as deployed
            definition.setStatus("DEPLOYED");
            routeDefinitions.put(internalKey, definition);

            // Persist
            persistRoute(definition);

            log.info("Deployed dynamic route: {} (tenant: {}, routeId: {}, key: {})", definition.getName(), tenantId,
                    logicalRouteId, internalKey);
            return definition;
        }
    }

    /**
     * Remove a route from CamelContext
     */
    public void removeRoute(String routeId) throws Exception {
        synchronized (routeLock(routeId)) {
            if (camelContext.getRoute(routeId) != null) {
                camelContext.getRouteController().stopRoute(routeId);
                camelContext.removeRoute(routeId);
                log.info("Removed route: {}", routeId);
            }

            CamelRouteDefinition def = routeDefinitions.get(routeId);
            if (def != null) {
                def.setStatus("STOPPED");
                persistRoute(def);
            }
        }
    }

    public void removeRoute(String routeId, String tenantId) throws Exception {
        String internalKey = routeKey(normalizeTenant(tenantId), routeId);
        removeRoute(internalKey);
    }

    /**
     * Start a stopped route
     */
    public void startRoute(String routeId) throws Exception {
        synchronized (routeLock(routeId)) {
            if (camelContext.getRoute(routeId) != null) {
                camelContext.getRouteController().startRoute(routeId);
                CamelRouteDefinition def = routeDefinitions.get(routeId);
                if (def != null) {
                    def.setStatus("DEPLOYED");
                    persistRoute(def);
                }
                log.info("Started route: {}", routeId);
            }
        }
    }

    public void startRoute(String routeId, String tenantId) throws Exception {
        String internalKey = routeKey(normalizeTenant(tenantId), routeId);
        startRoute(internalKey);
    }

    /**
     * Stop a running route
     */
    public void stopRoute(String routeId) throws Exception {
        synchronized (routeLock(routeId)) {
            if (camelContext.getRoute(routeId) != null) {
                camelContext.getRouteController().stopRoute(routeId);
                CamelRouteDefinition def = routeDefinitions.get(routeId);
                if (def != null) {
                    def.setStatus("STOPPED");
                    persistRoute(def);
                }
                log.info("Stopped route: {}", routeId);
            }
        }
    }

    public void stopRoute(String routeId, String tenantId) throws Exception {
        String internalKey = routeKey(normalizeTenant(tenantId), routeId);
        stopRoute(internalKey);
    }

    /**
     * Get all route definitions
     */
    public List<CamelRouteDefinition> getAllRoutes() {
        return new ArrayList<>(routeDefinitions.values());
    }

    public List<CamelRouteDefinition> getAllRoutes(String tenantId) {
        String tid = normalizeTenant(tenantId);
        return routeDefinitions.values().stream()
                .filter(Objects::nonNull)
                .filter(d -> tid.equalsIgnoreCase(normalizeTenant(d.getTenantId())))
                .toList();
    }

    /**
     * Get a specific route definition
     */
    public Optional<CamelRouteDefinition> getRoute(String routeId) {
        return Optional.ofNullable(routeDefinitions.get(routeId));
    }

    public Optional<CamelRouteDefinition> getRoute(String routeId, String tenantId) {
        String tid = normalizeTenant(tenantId);
        String internalKey = routeKey(tid, routeId);
        return Optional.ofNullable(routeDefinitions.get(internalKey));
    }

    /**
     * Delete a route completely
     */
    public void deleteRoute(String routeId) throws Exception {
        synchronized (routeLock(routeId)) {
            removeRoute(routeId);
            routeDefinitions.remove(routeId);
            // Best-effort: if tenantId isn't known here, fall back to deleting by id.
            camelRouteRepository.deleteById(routeId);
            log.info("Deleted route: {}", routeId);
        }
    }

    public void deleteRoute(String routeId, String tenantId) throws Exception {
        String tid = normalizeTenant(tenantId);
        String internalKey = routeKey(tid, routeId);
        synchronized (routeLock(internalKey)) {
            removeRoute(internalKey);
            routeDefinitions.remove(internalKey);

            // Primary delete path uses the internal key.
            try {
                camelRouteRepository.deleteById(internalKey);
            } catch (Exception ignored) {
                // Legacy fallback
                camelRouteRepository.deleteByIdAndTenantId(routeId, tid);
            }

            log.info("Deleted route: {} (tenant: {}, key: {})", routeId, tid, internalKey);
        }
    }

    private Object routeLock(String routeId) {
        return routeLocks.computeIfAbsent(routeId, k -> new Object());
    }

    private void deployToCamelContext(CamelRouteDefinition definition, String internalKey) throws Exception {
        if (internalKey == null || internalKey.isBlank()) {
            throw new IllegalArgumentException("Internal route key is required");
        }

        // Save a rollback snapshot BEFORE modifying the running route.
        CamelRouteDefinition rollbackDef = routeDefinitions.get(internalKey);
        if (rollbackDef == null) {
            try {
                Optional<CamelRouteEntity> entityOpt = camelRouteRepository.findById(internalKey);
                if (entityOpt.isPresent()) {
                    CamelRouteEntity entity = entityOpt.get();
                    CamelRouteDefinition def = objectMapper.readValue(entity.getDefinitionJson(),
                            CamelRouteDefinition.class);
                    rollbackDef = def;
                }
            } catch (Exception ignored) {
                // ignore
            }
        }

        boolean hadExisting = camelContext.getRoute(internalKey) != null;
        if (hadExisting) {
            try {
                camelContext.getRouteController().stopRoute(internalKey);
            } catch (Exception ignored) {
                // ignore
            }
            try {
                camelContext.removeRoute(internalKey);
            } catch (Exception ignored) {
                // ignore
            }
        }

        try {
            RouteBuilder routeBuilder = buildRouteFromDefinition(definition, internalKey);
            camelContext.addRoutes(routeBuilder);
        } catch (Exception deployEx) {
            // Best-effort cleanup of partially added route
            try {
                if (camelContext.getRoute(internalKey) != null) {
                    try {
                        camelContext.getRouteController().stopRoute(internalKey);
                    } catch (Exception ignored) {
                    }
                    camelContext.removeRoute(internalKey);
                }
            } catch (Exception ignored) {
            }

            // Rollback previous route if we had one
            if (hadExisting && rollbackDef != null) {
                try {
                    RouteBuilder rollbackBuilder = buildRouteFromDefinition(rollbackDef, internalKey);
                    camelContext.addRoutes(rollbackBuilder);
                    if (camelContext.getRoute(internalKey) != null) {
                        try {
                            camelContext.getRouteController().startRoute(internalKey);
                        } catch (Exception ignored) {
                        }
                    }
                    log.warn("Rolled back route '{}' after failed deploy: {}", internalKey, deployEx.getMessage());
                } catch (Exception rollbackEx) {
                    log.error("Failed to rollback route '{}' after failed deploy: {}", internalKey,
                            rollbackEx.getMessage());
                }
            }
            throw deployEx;
        }
    }

    private void persistRoute(CamelRouteDefinition definition) {
        try {
            if (definition.getSchemaVersion() == null) {
                definition.setSchemaVersion(CURRENT_SCHEMA_VERSION);
            }
            if (definition.getTenantId() == null || definition.getTenantId().isBlank()) {
                definition.setTenantId(DEFAULT_TENANT_ID);
            }

            String tid = normalizeTenant(definition.getTenantId());
            String logicalRouteId = definition.getId();
            if (logicalRouteId == null || logicalRouteId.isBlank()) {
                throw new IllegalArgumentException("Route id is required for persistence");
            }
            String internalKey = routeKey(tid, logicalRouteId);

            String json = objectMapper.writeValueAsString(definition);
            CamelRouteEntity entity = CamelRouteEntity.builder()
                    .id(internalKey)
                    .name(definition.getName())
                    .tenantId(tid)
                    .description(definition.getDescription())
                    .definitionJson(json)
                    .status(definition.getStatus())
                    .build();
            camelRouteRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist Camel route '{}': {}", definition.getId(), e.getMessage());
        }
    }

    private String routeKey(String tenantId, String routeId) {
        String tid = normalizeTenant(tenantId);
        String rid = (routeId == null) ? "" : routeId.trim();
        if (rid.isBlank()) {
            throw new IllegalArgumentException("routeId is required");
        }
        if (rid.contains("::") || tid.contains("::")) {
            throw new IllegalArgumentException("tenantId/routeId must not contain '::'");
        }
        String key = tid + "::" + rid;
        if (key.length() > 128) {
            throw new IllegalArgumentException("tenantId::routeId too long (max 128 chars)");
        }
        return key;
    }

    private void validateDefinition(CamelRouteDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Route definition is required");
        }

        if (definition.getSchemaVersion() == null) {
            definition.setSchemaVersion(CURRENT_SCHEMA_VERSION);
        }
        if (definition.getSchemaVersion() > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schemaVersion: " + definition.getSchemaVersion());
        }
        if (definition.getTenantId() == null || definition.getTenantId().isBlank()) {
            definition.setTenantId(DEFAULT_TENANT_ID);
        }
        if (definition.getNodes() == null || definition.getNodes().isEmpty()) {
            throw new IllegalArgumentException("Route must have nodes");
        }

        long fromCount = definition.getNodes().stream()
                .filter(Objects::nonNull)
                .filter(n -> "from".equalsIgnoreCase(n.getType()))
                .count();
        if (fromCount != 1) {
            throw new IllegalArgumentException("Route must contain exactly 1 'from' node (found: " + fromCount + ")");
        }

        RouteNode from = definition.getNodes().stream()
                .filter(Objects::nonNull)
                .filter(n -> "from".equalsIgnoreCase(n.getType()))
                .findFirst()
                .orElse(null);
        if (from == null || from.getUri() == null || from.getUri().isBlank()) {
            throw new IllegalArgumentException("'from' node must define a non-empty uri");
        }
        validateEndpointUri(from.getUri(), "from", from.getId());

        Map<String, RouteNode> nodeMap = definition.getNodes().stream()
                .filter(Objects::nonNull)
                .filter(n -> n.getId() != null && !n.getId().isBlank())
                .collect(Collectors.toMap(RouteNode::getId, n -> n, (a, b) -> a));
        if (nodeMap.size() != definition.getNodes().size()) {
            throw new IllegalArgumentException("All nodes must have unique non-empty ids");
        }

        List<RouteEdge> edges = definition.getEdges() != null ? definition.getEdges() : Collections.emptyList();
        for (RouteEdge edge : edges) {
            if (edge == null)
                continue;
            if (!nodeMap.containsKey(edge.getSource())) {
                throw new IllegalArgumentException("Edge source not found: " + edge.getSource());
            }
            if (!nodeMap.containsKey(edge.getTarget())) {
                throw new IllegalArgumentException("Edge target not found: " + edge.getTarget());
            }
        }

        // Validate scoped edges (handles) for branching nodes
        for (RouteNode node : nodeMap.values()) {
            if (node == null || node.getId() == null)
                continue;
            String type = node.getType() != null ? node.getType().toLowerCase(Locale.ROOT) : "";
            if (!"choice".equals(type) && !"trycatch".equals(type)) {
                continue;
            }

            List<RouteEdge> outgoing = edges.stream()
                    .filter(e -> e != null && node.getId().equals(e.getSource()))
                    .toList();

            if (outgoing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Scoped node '" + type + "' must have at least one outgoing edge: " + node.getId());
            }

            if ("choice".equals(type)) {
                boolean hasWhen = outgoing.stream().anyMatch(e -> "when".equals(normalizeHandle(e.getSourceHandle())));
                boolean hasOtherwise = outgoing.stream()
                        .anyMatch(e -> "otherwise".equals(normalizeHandle(e.getSourceHandle())));
                if (!hasWhen && !hasOtherwise) {
                    throw new IllegalArgumentException(
                            "choice node must have outgoing edges with sourceHandle 'when' and/or 'otherwise': "
                                    + node.getId());
                }
            }

            if ("trycatch".equals(type)) {
                boolean hasTry = outgoing.stream().anyMatch(e -> "try".equals(normalizeHandle(e.getSourceHandle())));
                if (!hasTry) {
                    throw new IllegalArgumentException(
                            "trycatch node must have at least one outgoing edge with sourceHandle 'try': "
                                    + node.getId());
                }
            }
        }

        // Reachability + cycle detection (graph cycles are not supported; use loop EIP
        // instead)
        Map<String, List<String>> adjacency = new HashMap<>();
        for (RouteEdge edge : edges) {
            if (edge == null)
                continue;
            adjacency.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge.getTarget());
        }

        String fromId = from.getId();
        if (fromId == null || fromId.isBlank()) {
            throw new IllegalArgumentException("'from' node must have a non-empty id");
        }

        detectCycles(fromId, adjacency);

        Set<String> reachable = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(fromId);
        while (!stack.isEmpty()) {
            String cur = stack.pop();
            if (!reachable.add(cur))
                continue;
            for (String next : adjacency.getOrDefault(cur, Collections.emptyList())) {
                stack.push(next);
            }
        }

        List<String> unreachable = nodeMap.keySet().stream()
                .filter(id -> !reachable.contains(id))
                .sorted()
                .toList();
        if (!unreachable.isEmpty()) {
            throw new IllegalArgumentException("Unreachable nodes from 'from' node: " + unreachable);
        }

        // Validate endpoint URIs for from/to nodes (config-driven allowlists)
        for (RouteNode node : nodeMap.values()) {
            if (node == null || node.getId() == null)
                continue;
            if (node.getType() == null)
                continue;
            String type = node.getType().trim().toLowerCase(Locale.ROOT);
            if (("to".equals(type) || "from".equals(type)) && !isBlank(node.getUri())) {
                validateEndpointUri(node.getUri(), type, node.getId());
            }
        }
    }

    private void validateEndpointUri(String uri, String nodeType, String nodeId) {
        if (isBlank(uri)) {
            return;
        }

        List<String> allowedSchemes = dynamicRouteSecurityProperties.getAllowedUriSchemes();
        List<String> allowedHosts = dynamicRouteSecurityProperties.getAllowedHttpHosts();

        String scheme = extractScheme(uri);

        // Allowlist is opt-in: empty lists mean "allow all".
        if (allowedSchemes != null && !allowedSchemes.isEmpty()) {
            boolean ok = allowedSchemes.stream().anyMatch(s -> s != null && s.trim().equalsIgnoreCase(scheme));
            if (!ok) {
                throw new IllegalArgumentException(
                        "URI scheme not allowed for " + nodeType + " node '" + nodeId + "': " + scheme);
            }
        }

        if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && allowedHosts != null && !allowedHosts.isEmpty()) {
            try {
                URI parsed = URI.create(uri);
                String host = parsed.getHost();
                if (host == null || host.isBlank()) {
                    throw new IllegalArgumentException(
                            "HTTP URI must include a hostname for " + nodeType + " node '" + nodeId + "'");
                }
                boolean ok = allowedHosts.stream().anyMatch(h -> h != null && h.trim().equalsIgnoreCase(host));
                if (!ok) {
                    throw new IllegalArgumentException(
                            "HTTP host not allowed for " + nodeType + " node '" + nodeId + "': " + host);
                }
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException(
                        "Invalid URI for " + nodeType + " node '" + nodeId + "': " + ex.getMessage());
            }
        }
    }

    private String extractScheme(String uri) {
        String trimmed = uri.trim();
        int idx = trimmed.indexOf(':');
        if (idx <= 0) {
            return "";
        }
        return trimmed.substring(0, idx);
    }

    private void detectCycles(String startNodeId, Map<String, List<String>> adjacency) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        dfsDetectCycle(startNodeId, adjacency, visiting, visited);
    }

    private void dfsDetectCycle(String nodeId, Map<String, List<String>> adjacency,
            Set<String> visiting, Set<String> visited) {
        if (visited.contains(nodeId)) {
            return;
        }
        if (!visiting.add(nodeId)) {
            throw new IllegalArgumentException("Cycle detected in route graph at node: " + nodeId);
        }
        for (String next : adjacency.getOrDefault(nodeId, Collections.emptyList())) {
            dfsDetectCycle(next, adjacency, visiting, visited);
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
    }

    private String normalizeHandle(String handle) {
        return handle == null ? "" : handle.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String normalizeTenant(String tenantId) {
        return isBlank(tenantId) ? DEFAULT_TENANT_ID : tenantId.trim();
    }

    /**
     * Build a RouteBuilder from the visual definition
     */
    private RouteBuilder buildRouteFromDefinition(CamelRouteDefinition definition, String internalKey) {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Find the "from" node (source)
                RouteNode fromNode = definition.getNodes().stream()
                        .filter(n -> "from".equals(n.getType()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Route must have a 'from' node"));

                List<RouteEdge> edges = definition.getEdges() != null ? definition.getEdges() : Collections.emptyList();

                // Build adjacency map from edges
                Map<String, List<String>> adjacencyMap = new HashMap<>();
                // Outgoing edges grouped by sourceHandle (for branching nodes)
                Map<String, Map<String, List<RouteEdge>>> outgoingEdgesByHandle = new HashMap<>();
                for (RouteEdge edge : edges) {
                    if (edge == null)
                        continue;
                    adjacencyMap.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge.getTarget());

                    String handle = normalizeHandle(edge.getSourceHandle());
                    outgoingEdgesByHandle
                            .computeIfAbsent(edge.getSource(), k -> new HashMap<>())
                            .computeIfAbsent(handle, k -> new ArrayList<>())
                            .add(edge);
                }

                // Create node lookup
                Map<String, RouteNode> nodeMap = definition.getNodes().stream()
                        .collect(Collectors.toMap(RouteNode::getId, n -> n));

                // Build route starting from "from" node
                RouteDefinition route = from(fromNode.getUri())
                        .routeId(internalKey)
                        .description(definition.getDescription());

                // Get first level nodes after "from"
                List<String> nextNodeIds = adjacencyMap.getOrDefault(fromNode.getId(), Collections.emptyList());

                // Build tree structure for each child
                for (String nodeId : nextNodeIds) {
                    RouteNodeTree tree = buildNodeTree(nodeId, adjacencyMap, nodeMap, new HashSet<>());
                    applyNodeTreeToRoute(route, tree, adjacencyMap, outgoingEdgesByHandle, nodeMap);
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
            Map<String, Map<String, List<RouteEdge>>> outgoingEdgesByHandle,
            Map<String, RouteNode> nodeMap) {

        if (tree == null) {
            return;
        }

        RouteNode node = tree.getNode();
        String nodeType = node.getType() != null ? node.getType().toLowerCase() : "";

        // Handle scoped EIPs that need children
        if (SCOPED_EIPS.contains(nodeType)) {
            applyScopedEIP(route, tree, adjacencyMap, outgoingEdgesByHandle, nodeMap);
        } else {
            // Non-scoped EIPs - apply directly
            applyNodeToRoute(route, node);

            // Process next nodes sequentially (not as children)
            List<String> nextNodeIds = adjacencyMap.getOrDefault(node.getId(), Collections.emptyList());
            for (String nextId : nextNodeIds) {
                RouteNodeTree nextTree = buildNodeTree(nextId, adjacencyMap, nodeMap, new HashSet<>());
                applyNodeTreeToRoute(route, nextTree, adjacencyMap, outgoingEdgesByHandle, nodeMap);
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
            Map<String, Map<String, List<RouteEdge>>> outgoingEdgesByHandle,
            Map<String, RouteNode> nodeMap) {

        RouteNode node = tree.getNode();
        String nodeType = node.getType().toLowerCase();

        switch (nodeType) {
            case "filter":
                String filterExpr = node.getExpression() != null ? node.getExpression() : "${body} != null";
                FilterDefinition filterDef = route.filter(simple(filterExpr));

                // Process children inside filter scope
                for (RouteNodeTree child : tree.getChildren()) {
                    applyNodeTreeToRoute(filterDef, child, adjacencyMap, outgoingEdgesByHandle, nodeMap);
                }

                filterDef.end(); // Close the scope
                break;

            case "split":
                String splitExpr = node.getExpression() != null ? node.getExpression() : "${body}";
                SplitDefinition splitDef = route.split(simple(splitExpr));

                // Process children inside split scope
                for (RouteNodeTree child : tree.getChildren()) {
                    applyNodeTreeToRoute(splitDef, child, adjacencyMap, outgoingEdgesByHandle, nodeMap);
                }

                splitDef.end(); // Close the scope
                break;

            case "loop":
                String loopExpr = node.getExpression() != null ? node.getExpression() : "3";
                LoopDefinition loopDef;
                try {
                    int loopCount = Integer.parseInt(loopExpr);
                    loopDef = route.loop(loopCount);
                } catch (NumberFormatException e) {
                    loopDef = route.loop(simple(loopExpr));
                }

                // Process children inside loop scope
                for (RouteNodeTree child : tree.getChildren()) {
                    applyNodeTreeToRoute(loopDef, child, adjacencyMap, outgoingEdgesByHandle, nodeMap);
                }

                loopDef.end(); // Close the scope
                break;

            case "trycatch":
                // Outgoing edges are split by sourceHandle: "try" and "catch".
                // Catch edges can optionally provide exceptionType (FQCN) to build typed
                // doCatch blocks.
                var doTry = route.doTry();

                List<RouteEdge> tryEdges = outgoingEdgesByHandle
                        .getOrDefault(node.getId(), Collections.emptyMap())
                        .getOrDefault("try", Collections.emptyList());
                for (RouteEdge edge : tryEdges) {
                    if (edge == null)
                        continue;
                    RouteNodeTree tryTree = buildNodeTree(edge.getTarget(), adjacencyMap, nodeMap, new HashSet<>());
                    applyNodeTreeToRoute(doTry, tryTree, adjacencyMap, outgoingEdgesByHandle, nodeMap);
                }

                List<RouteEdge> catchEdges = outgoingEdgesByHandle
                        .getOrDefault(node.getId(), Collections.emptyMap())
                        .getOrDefault("catch", Collections.emptyList());

                if (!catchEdges.isEmpty()) {
                    Map<String, List<RouteEdge>> catchGroups = catchEdges.stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.groupingBy(e -> isBlank(e.getExceptionType()) ? "java.lang.Exception"
                                    : e.getExceptionType().trim()));

                    for (Map.Entry<String, List<RouteEdge>> entry : catchGroups.entrySet()) {
                        Class<? extends Exception> exClass = resolveExceptionClass(entry.getKey());
                        var doCatch = doTry.doCatch(exClass);
                        for (RouteEdge edge : entry.getValue()) {
                            RouteNodeTree catchTree = buildNodeTree(edge.getTarget(), adjacencyMap, nodeMap,
                                    new HashSet<>());
                            applyNodeTreeToRoute(doCatch, catchTree, adjacencyMap, outgoingEdgesByHandle, nodeMap);
                        }
                    }
                    doTry.end();
                } else {
                    doTry.end();
                }
                break;

            case "choice":
                // Dynamic choice with edge-level condition support.
                // - Each outgoing edge with sourceHandle "when" may provide edge.condition;
                // fallback to node.expression.
                // - "otherwise" edges are supported.
                String defaultWhenExpr = node.getExpression() != null ? node.getExpression() : "${body} != null";
                var choice = route.choice();

                List<RouteEdge> whenEdges = outgoingEdgesByHandle
                        .getOrDefault(node.getId(), Collections.emptyMap())
                        .getOrDefault("when", Collections.emptyList());
                for (RouteEdge edge : whenEdges) {
                    if (edge == null)
                        continue;
                    String expr = !isBlank(edge.getCondition()) ? edge.getCondition().trim() : defaultWhenExpr;
                    var whenDef = choice.when(simple(expr));
                    RouteNodeTree whenTree = buildNodeTree(edge.getTarget(), adjacencyMap, nodeMap, new HashSet<>());
                    applyNodeTreeToRoute(whenDef, whenTree, adjacencyMap, outgoingEdgesByHandle, nodeMap);
                }

                List<RouteEdge> otherwiseEdges = outgoingEdgesByHandle
                        .getOrDefault(node.getId(), Collections.emptyMap())
                        .getOrDefault("otherwise", Collections.emptyList());
                if (!otherwiseEdges.isEmpty()) {
                    var otherwiseDef = choice.otherwise();
                    for (RouteEdge edge : otherwiseEdges) {
                        if (edge == null)
                            continue;
                        RouteNodeTree otherwiseTree = buildNodeTree(edge.getTarget(), adjacencyMap, nodeMap,
                                new HashSet<>());
                        applyNodeTreeToRoute(otherwiseDef, otherwiseTree, adjacencyMap, outgoingEdgesByHandle, nodeMap);
                    }
                }

                choice.end();
                break;

            default:
                // For other scoped EIPs, just log and continue
                log.warn("Scoped EIP '{}' not fully implemented yet, treating as regular node", nodeType);
                applyNodeToRoute(route, node);
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Exception> resolveExceptionClass(String fqcn) {
        if (isBlank(fqcn)) {
            return Exception.class;
        }
        try {
            Class<?> clazz = Class.forName(fqcn.trim());
            if (!Exception.class.isAssignableFrom(clazz)) {
                log.warn("Configured exceptionType '{}' is not an Exception; falling back to Exception", fqcn);
                return Exception.class;
            }
            return (Class<? extends Exception>) clazz;
        } catch (Exception e) {
            log.warn("Failed to resolve exceptionType '{}': {}; falling back to Exception", fqcn, e.getMessage());
            return Exception.class;
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
                    // Auto-create transaction if not provided
                    if (txnId == null || txnId.isBlank()) {
                        String destAccount = extractValue(node, exchange, "destAccount", "destAccount", "N/A");
                        String description = extractValue(node, exchange, "description", "description",
                                "Debit transaction");
                        Transaction txn = accountService.createTransaction(account, destAccount, amount, description);
                        txnId = txn.getTransactionId();
                        exchange.getIn().setHeader("transactionId", txnId);
                        log.info("📝 Auto-created transaction: {}", txnId);
                    }
                    final String finalTxnId = txnId;
                    try {
                        log.info("💸 SAGA DEBIT: {} - {} (TxnId: {})", account, amount, finalTxnId);
                        accountService.debit(account, amount, finalTxnId);
                        exchange.getIn().setHeader("sagaState", "DEBITED");

                        // Build result map that handles null values
                        Map<String, Object> resultMap = new java.util.HashMap<>();
                        resultMap.put("account", account);
                        resultMap.put("amount", amount);
                        resultMap.put("txnId", txnId != null ? txnId : "N/A");

                        sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                                "Debited " + amount + " from " + account,
                                resultMap, null,
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

                        // Build result map that handles null values
                        Map<String, Object> resultMap = new java.util.HashMap<>();
                        resultMap.put("account", account);
                        resultMap.put("amount", amount);
                        resultMap.put("txnId", txnId != null ? txnId : "N/A");

                        sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                                "Credited " + amount + " to " + account,
                                resultMap, null,
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

                        // Build body map that handles null values
                        Map<String, Object> bodyMap = new java.util.HashMap<>();
                        bodyMap.put("status", "COMPLETED");
                        bodyMap.put("transactionId", txnId != null ? txnId : "N/A");
                        bodyMap.put("sourceAccount", sourceAccount != null ? sourceAccount : "unknown");
                        bodyMap.put("destAccount", destAccount != null ? destAccount : "unknown");
                        bodyMap.put("amount", amount);
                        bodyMap.put("description", description != null ? description : "");
                        exchange.getIn().setBody(bodyMap);

                        // Build result map that handles null values
                        Map<String, Object> resultMap = new java.util.HashMap<>();
                        resultMap.put("sourceAccount", sourceAccount != null ? sourceAccount : "unknown");
                        resultMap.put("destAccount", destAccount != null ? destAccount : "unknown");
                        resultMap.put("amount", amount);
                        resultMap.put("txnId", txnId != null ? txnId : "N/A");

                        sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                                "Transferred " + amount + " from " + sourceAccount + " to " + destAccount,
                                resultMap,
                                null,
                                System.currentTimeMillis() - startTime);
                    } catch (Exception e) {
                        sendStepNotification(nodeId, nodeType, routeId, "FAILED",
                                "Transfer failed", null, e.getMessage(),
                                System.currentTimeMillis() - startTime);
                        exchange.getIn().setHeader("transferStatus", "FAILED");

                        // Build error body map that handles null values
                        Map<String, Object> errorBodyMap = new java.util.HashMap<>();
                        errorBodyMap.put("status", "FAILED");
                        errorBodyMap.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
                        exchange.getIn().setBody(errorBodyMap);
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

                    String txnId = extractValue(node, exchange, "transactionId", "transactionId", null);
                    try {
                        log.warn("↩️ SAGA COMPENSATE: {} + {} (TxnId: {})", account, amount, txnId);
                        accountService.compensateDebit(account, amount, txnId);
                        exchange.getIn().setHeader("sagaState", "COMPENSATED");

                        // Build result map that handles null values
                        Map<String, Object> resultMap = new java.util.HashMap<>();
                        resultMap.put("account", account != null ? account : "unknown");
                        resultMap.put("amount", amount);
                        resultMap.put("txnId", txnId != null ? txnId : "N/A");

                        sendStepNotification(nodeId, nodeType, routeId, "COMPLETED",
                                "Compensated/Rolled back " + amount + " for " + account,
                                resultMap, null,
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
     * 3. Body fallback (using fallbackHeaderKey, if different from propertyKey)
     * 4. Header (using fallbackHeaderKey)
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

        // 2b. Check Body fallback key (common when payload uses domain terms like
        // sourceAccount/destAccount)
        if (fallbackHeaderKey != null && !fallbackHeaderKey.isBlank() && !fallbackHeaderKey.equals(propertyKey)) {
            String fallbackBodyValue = extractFromBody(exchange, fallbackHeaderKey);
            if (fallbackBodyValue != null && !fallbackBodyValue.isBlank() && !"null".equals(fallbackBodyValue)) {
                return fallbackBodyValue;
            }
        }

        // 3. Check Header (fallback)
        if (fallbackHeaderKey != null) {
            String headerValue = exchange.getIn().getHeader(fallbackHeaderKey, String.class);
            if (headerValue != null && !headerValue.isBlank() && !"null".equals(headerValue))
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
                return objectMapper.readTree(s);
            }
            if (body instanceof byte[] bytes) {
                return objectMapper.readTree(bytes);
            }

            // Map / POJO -> JsonNode
            return objectMapper.valueToTree(body);
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
