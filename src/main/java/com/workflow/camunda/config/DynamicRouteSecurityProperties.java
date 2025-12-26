package com.workflow.camunda.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "dynamic.routes.security")
public class DynamicRouteSecurityProperties {

    /**
     * If non-empty, only these Camel URI schemes are allowed (e.g. ["direct","log","https"]).
     * If empty, all schemes are allowed.
     */
    private List<String> allowedUriSchemes = new ArrayList<>();

    /**
     * If non-empty, only these HTTP/HTTPS hostnames are allowed.
     * If empty, all hosts are allowed.
     */
    private List<String> allowedHttpHosts = new ArrayList<>();

    /**
     * Optional API key for protecting dynamic route management endpoints.
     * If blank, no API key is required.
     */
    private String apiKey = "";
}
