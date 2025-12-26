package com.workflow.camunda.service;

import com.workflow.camunda.dto.CamelRouteDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Ensures demo routes exist, but still keeps the system fully dynamic.
 *
 * These routes are deployed via {@link DynamicRouteService} (persisted + injected into CamelContext)
 * on-demand the first time they are used.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CamelDemoRouteService {

    private final CamelContext camelContext;
    private final DynamicRouteService dynamicRouteService;

    public void ensureDeployed(String routeId) {
        if (routeId == null || routeId.isBlank()) {
            throw new IllegalArgumentException("routeId is required");
        }

        // NOTE:
        // DynamicRouteService deploys routes into CamelContext using a tenant-scoped internal routeId:
        //   <tenantId>::<logicalRouteId>
        // while the consumer endpoint for demo routes is always direct:<logicalRouteId>.
        // So checking camelContext.getRoute(routeId) is NOT sufficient.
        if (hasDirectConsumer(routeId)) {
            return;
        }

        CamelRouteDefinition definition = buildDemoRoute(routeId);
        try {
            dynamicRouteService.deployRoute(definition);
            log.info("Auto-deployed demo route: {}", routeId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to auto-deploy demo route '" + routeId + "': " + e.getMessage(), e);
        }
    }

    private boolean hasDirectConsumer(String directName) {
        final String expected = "direct:" + directName;
        for (Route route : camelContext.getRoutes()) {
            try {
                String uri = route.getEndpoint() != null ? route.getEndpoint().getEndpointUri() : null;
                if (uri == null) continue;

                // Normalize common Camel URI variations.
                if (uri.startsWith("direct://")) {
                    uri = "direct:" + uri.substring("direct://".length());
                }

                if (expected.equals(uri)) {
                    return true;
                }
            } catch (Exception ignored) {
                // Best-effort check. If a route is misconfigured, we still want ensureDeployed to work.
            }
        }
        return false;
    }

    private CamelRouteDefinition buildDemoRoute(String routeId) {
        return switch (routeId) {
            // ==== Integration ====
            case "callExternalApi" -> simpleHttpGet(routeId, "https://httpbin.org/get");
            case "callApiWithParams" -> simpleHttpGet(routeId, "https://httpbin.org/anything");
            case "postToExternalApi" -> simpleHttpPost(routeId, "https://httpbin.org/post");

            // ==== Routing / Filtering ====
            case "routeMessage" -> simpleTransform(routeId, "Routed(priority=${header.priority}): ${body}");
            case "filterMessage" -> simpleFilter(routeId, "${body[amount]} >= 1000", "Accepted: ${body}");
            case "multicast" -> simpleTransform(routeId, "Multicast demo received: ${body}");

            // ==== Transformation ====
            case "transformJson" -> simpleTransform(routeId, "{\"original\": ${body}, \"ts\": \"${date:now:yyyy-MM-dd'T'HH:mm:ss}\"}");
            case "mapToXmlStructure" -> simpleTransform(routeId, "<root>${body}</root>");
            case "mapFields" -> simpleTransform(routeId, "Mapped: ${body}");

            // ==== Orchestration / Pipeline / Resilient ====
            case "orchestrate" -> simpleHttpGet(routeId, "https://httpbin.org/delay/1");
            case "pipeline" -> simpleTransform(routeId, "Pipeline processed: ${body}");
            case "resilientCall" -> simpleHttpGet(routeId, "https://httpbin.org/status/200");

            // ==== Saga ====
            case "saga-transfer" -> sagaTransfer(routeId);
            case "saga-balance" -> sagaBalance(routeId);
            case "saga-accounts" -> sagaAccounts(routeId);

            default -> throw new IllegalArgumentException("Unknown demo routeId: " + routeId);
        };
    }

    private CamelRouteDefinition simpleHttpGet(String routeId, String url) {
        return CamelRouteDefinition.builder()
            .id(routeId)
            .name(routeId)
            .description("Auto-generated demo route")
            .nodes(List.of(
                CamelRouteDefinition.RouteNode.builder().id("from").type("from").uri("direct:" + routeId).build(),
                CamelRouteDefinition.RouteNode.builder().id("log1").type("log").message("Calling: " + url).build(),
                CamelRouteDefinition.RouteNode.builder().id("to").type("to").uri(url).build(),
                CamelRouteDefinition.RouteNode.builder().id("asString").type("convertBodyTo").build()
            ))
            .edges(List.of(
                CamelRouteDefinition.RouteEdge.builder().id("e1").source("from").target("log1").build(),
                CamelRouteDefinition.RouteEdge.builder().id("e2").source("log1").target("to").build(),
                CamelRouteDefinition.RouteEdge.builder().id("e3").source("to").target("asString").build()
            ))
            .status("DEPLOYED")
            .build();
    }

    private CamelRouteDefinition simpleHttpPost(String routeId, String url) {
        return CamelRouteDefinition.builder()
            .id(routeId)
            .name(routeId)
            .description("Auto-generated demo route")
            .nodes(List.of(
                CamelRouteDefinition.RouteNode.builder().id("from").type("from").uri("direct:" + routeId).build(),
                CamelRouteDefinition.RouteNode.builder().id("log1").type("log").message("POST to: " + url).build(),
                CamelRouteDefinition.RouteNode.builder().id("to").type("to").uri(url).build(),
                CamelRouteDefinition.RouteNode.builder().id("asString").type("convertBodyTo").build()
            ))
            .edges(List.of(
                CamelRouteDefinition.RouteEdge.builder().id("e1").source("from").target("log1").build(),
                CamelRouteDefinition.RouteEdge.builder().id("e2").source("log1").target("to").build(),
                CamelRouteDefinition.RouteEdge.builder().id("e3").source("to").target("asString").build()
            ))
            .status("DEPLOYED")
            .build();
    }

    private CamelRouteDefinition simpleTransform(String routeId, String expression) {
        return CamelRouteDefinition.builder()
            .id(routeId)
            .name(routeId)
            .description("Auto-generated demo route")
            .nodes(List.of(
                CamelRouteDefinition.RouteNode.builder().id("from").type("from").uri("direct:" + routeId).build(),
                CamelRouteDefinition.RouteNode.builder().id("transform").type("transform").expression(expression).build(),
                CamelRouteDefinition.RouteNode.builder().id("asString").type("convertBodyTo").build()
            ))
            .edges(List.of(
                CamelRouteDefinition.RouteEdge.builder().id("e1").source("from").target("transform").build(),
                CamelRouteDefinition.RouteEdge.builder().id("e2").source("transform").target("asString").build()
            ))
            .status("DEPLOYED")
            .build();
    }

    private CamelRouteDefinition simpleFilter(String routeId, String filterExpr, String okExpr) {
        // from -> filter(scope) -> transform -> asString
        return CamelRouteDefinition.builder()
            .id(routeId)
            .name(routeId)
            .description("Auto-generated demo route")
            .nodes(List.of(
                CamelRouteDefinition.RouteNode.builder().id("from").type("from").uri("direct:" + routeId).build(),
                CamelRouteDefinition.RouteNode.builder().id("filter").type("filter").expression(filterExpr).build(),
                CamelRouteDefinition.RouteNode.builder().id("transform").type("transform").expression(okExpr).build(),
                CamelRouteDefinition.RouteNode.builder().id("asString").type("convertBodyTo").build()
            ))
            .edges(List.of(
                CamelRouteDefinition.RouteEdge.builder().id("e1").source("from").target("filter").build(),
                CamelRouteDefinition.RouteEdge.builder().id("e2").source("filter").target("transform").build(),
                CamelRouteDefinition.RouteEdge.builder().id("e3").source("transform").target("asString").build()
            ))
            .status("DEPLOYED")
            .build();
    }

    private CamelRouteDefinition sagaTransfer(String routeId) {
        // Uses DynamicRouteService's built-in sagaTransfer node to call AccountService.executeTransfer.
        return CamelRouteDefinition.builder()
            .id(routeId)
            .name(routeId)
            .description("Auto-generated saga transfer route")
            .nodes(List.of(
                CamelRouteDefinition.RouteNode.builder().id("from").type("from").uri("direct:" + routeId).build(),
                CamelRouteDefinition.RouteNode.builder().id("transfer").type("sagaTransfer").build(),
                CamelRouteDefinition.RouteNode.builder().id("asString").type("convertBodyTo").build()
            ))
            .edges(List.of(
                CamelRouteDefinition.RouteEdge.builder().id("e1").source("from").target("transfer").build(),
                CamelRouteDefinition.RouteEdge.builder().id("e2").source("transfer").target("asString").build()
            ))
            .status("DEPLOYED")
            .build();
    }

    private CamelRouteDefinition sagaBalance(String routeId) {
        return CamelRouteDefinition.builder()
            .id(routeId)
            .name(routeId)
            .description("Auto-generated saga balance route")
            .nodes(List.of(
                CamelRouteDefinition.RouteNode.builder().id("from").type("from").uri("direct:" + routeId).build(),
                CamelRouteDefinition.RouteNode.builder().id("balance").type("to")
                    .uri("bean:accountService?method=getBalance(${header.accountId})").build(),
                CamelRouteDefinition.RouteNode.builder().id("asString").type("convertBodyTo").build()
            ))
            .edges(List.of(
                CamelRouteDefinition.RouteEdge.builder().id("e1").source("from").target("balance").build(),
                CamelRouteDefinition.RouteEdge.builder().id("e2").source("balance").target("asString").build()
            ))
            .status("DEPLOYED")
            .build();
    }

    private CamelRouteDefinition sagaAccounts(String routeId) {
        return CamelRouteDefinition.builder()
            .id(routeId)
            .name(routeId)
            .description("Auto-generated saga accounts route")
            .nodes(List.of(
                CamelRouteDefinition.RouteNode.builder().id("from").type("from").uri("direct:" + routeId).build(),
                CamelRouteDefinition.RouteNode.builder().id("accounts").type("to")
                    .uri("bean:accountRepository?method=findAll").build(),
                CamelRouteDefinition.RouteNode.builder().id("asString").type("convertBodyTo").build()
            ))
            .edges(List.of(
                CamelRouteDefinition.RouteEdge.builder().id("e1").source("from").target("accounts").build(),
                CamelRouteDefinition.RouteEdge.builder().id("e2").source("accounts").target("asString").build()
            ))
            .status("DEPLOYED")
            .build();
    }
}
