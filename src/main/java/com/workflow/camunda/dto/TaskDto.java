package com.workflow.camunda.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * DTO for Task information
 */
@Data
@Builder
public class TaskDto {
    private String id;
    private String name;
    private String assignee;
    private String processInstanceId;
    private String taskDefinitionKey;
    private Date createTime;
}
