package com.workflow.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO representing a delegate and its available actions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelegateInfo {

    /**
     * Bean name used in delegateExpression (e.g., "moneyTransferDelegate")
     */
    private String name;

    /**
     * Display name for UI
     */
    private String displayName;

    /**
     * Description of what this delegate does
     */
    private String description;

    /**
     * Available actions for this delegate
     */
    private List<ActionInfo> actions;

    /**
     * Represents an action that a delegate can perform
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionInfo {
        /**
         * Action identifier (e.g., "validateSourceAccount")
         */
        private String name;

        /**
         * Display name for UI
         */
        private String displayName;

        /**
         * Description of what this action does
         */
        private String description;

        /**
         * Required variables for this action
         * Key: variable name, Value: variable type description
         */
        private Map<String, String> requiredVariables;
    }
}
