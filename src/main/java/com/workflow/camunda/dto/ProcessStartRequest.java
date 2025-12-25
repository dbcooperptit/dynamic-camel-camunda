package com.workflow.camunda.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for starting a process
 */
@Data
public class ProcessStartRequest {
    private Map<String, Object> variables = new HashMap<>();
}
