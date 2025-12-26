package com.workflow.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for dynamic Camel route definition.
 * Represents a visual route design that can be converted to a Camel
 * RouteBuilder.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CamelRouteDefinition {

    /**
     * Schema version of this definition payload.
     * Used for backward-compatible migrations when persisted definitions evolve.
     */
    private Integer schemaVersion;

    /**
     * Optional tenant identifier for scoping routes.
     * If omitted, backend will treat it as "default".
     */
    private String tenantId;

    /**
     * Unique route identifier
     */
    private String id;

    /**
     * Human-readable route name
     */
    private String name;

    /**
     * Route description
     */
    private String description;

    /**
     * List of nodes in the route
     */
    private List<RouteNode> nodes;

    /**
     * List of edges connecting nodes
     */
    private List<RouteEdge> edges;

    /**
     * Route status (DEPLOYED, STOPPED, DRAFT)
     */
    private String status;

    /**
     * Node definition in the route
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteNode {
        /**
         * Unique node ID
         */
        private String id;

        /**
         * Node type: from, to, log, setBody, choice, transform, filter
         */
        private String type;

        /**
         * Endpoint URI (for from/to nodes)
         */
        private String uri;

        /**
         * Message (for log nodes)
         */
        private String message;

        /**
         * Expression (for setBody, transform, filter nodes)
         */
        private String expression;

        /**
         * Expression language (simple, constant, jsonpath)
         */
        private String expressionLanguage;

        /**
         * Conditions for choice node
         */
        private List<ChoiceCondition> conditions;

        /**
         * Additional properties
         */
        private Map<String, Object> properties;

        /**
         * Visual position X
         */
        private Double positionX;

        /**
         * Visual position Y
         */
        private Double positionY;
    }

    /**
     * Edge connecting two nodes
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteEdge {
        private String id;
        private String source;
        private String target;
        /**
         * Optional ReactFlow handle id used by the visual builder (e.g. "when", "otherwise", "try", "catch").
         * This keeps the routing model fully dynamic without requiring static route code.
         */
        private String sourceHandle;
        private String targetHandle;
        private String label;
        private String condition;

        /**
         * Optional exception type (fully qualified class name) for try/catch edges.
         * Example: "java.lang.IllegalArgumentException".
         */
        private String exceptionType;
    }

    /**
     * Condition for choice/when node
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChoiceCondition {
        private String id;
        private String expression;
        private String targetNodeId;
        private boolean otherwise;
    }
}
