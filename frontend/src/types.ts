// API Types
export interface StepDefinition {
    id: string;
    name: string;
    type: StepType;
    assignee?: string;
    candidateGroups?: string[];
    delegateExpression?: string;
    delegateClass?: string;
    conditionExpression?: string;
    nextStepConditions?: Record<string, string>;
    nextSteps: string[];
    defaultFlow?: boolean;
    formKey?: string;
    variables?: Record<string, unknown>;
}

export type StepType =
    | 'START_EVENT'
    | 'END_EVENT'
    | 'USER_TASK'
    | 'SERVICE_TASK'
    | 'EXCLUSIVE_GATEWAY'
    | 'PARALLEL_GATEWAY';

export interface WorkflowDefinitionRequest {
    processKey: string;
    processName: string;
    description?: string;
    historyTimeToLive?: number;
    steps: StepDefinition[];
}

export interface DeploymentResponse {
    deploymentId: string;
    processDefinitionId: string;
    processKey: string;
    processName: string;
    deployedAt: string;
    bpmnXml?: string;
    message: string;
}

export interface DeploymentDto {
    id: string;
    name: string;
    source: string;
    deploymentTime: string;
    tenantId?: string;
}

export interface ProcessDefinitionDto {
    id: string;
    key: string;
    name?: string;
    version: number;
}

export interface TaskDto {
    id: string;
    name: string;
    assignee: string;
    processInstanceId: string;
    taskDefinitionKey: string;
    createTime: string;
}

export interface ProcessStartResponse {
    processInstanceId: string;
    processKey: string;
    status: string;
}

export interface ProcessStatusResponse {
    processInstanceId: string;
    isActive: boolean;
    status: string;
}

// React Flow Types for Visual Workflow Builder
export interface BpmnNodeData extends Record<string, unknown> {
    label: string;
    stepType: StepType;
    assignee?: string;
    candidateGroups?: string[];
    delegateExpression?: string;
    delegateClass?: string;
    conditionExpression?: string;
    formKey?: string;
    variables?: Record<string, unknown>;
    gatewayType?: 'exclusive' | 'parallel';
}

// Activity History Types
export interface ActivityInstance {
    id: string;
    activityId: string;
    activityName: string;
    activityType: string;
    processInstanceId: string;
    startTime: string;
    endTime?: string;
    durationInMillis?: number;
}

// Process Variable Types
export interface VariableDto {
    name: string;
    value: unknown;
    type: string;
}

// Process Instance with Variables
export interface ProcessInstanceDto {
    id: string;
    processDefinitionId: string;
    processDefinitionKey: string;
    businessKey?: string;
    startTime: string;
    endTime?: string;
    state: 'ACTIVE' | 'COMPLETED' | 'SUSPENDED' | 'EXTERNALLY_TERMINATED';
    variables?: VariableDto[];
}

// Activity Event for SSE Streaming (realtime visualization)
export interface ActivityEvent {
    processInstanceId: string;
    activityId: string;
    activityName: string;
    activityType: string;
    eventType: 'start' | 'end';
    timestamp: string;
    variables: Record<string, unknown>;
    durationMs?: number;
    message?: string;
}

export interface TaskEvent {
    taskId: string;
    type: string;
    status: 'STARTED' | 'COMPLETED' | 'FAILED';
    message: string;
    timestamp: number;
    nodeType?: string;
    routeId?: string;
    processInstanceId?: string;
    activityId?: string;
    activityName?: string;
    result?: unknown;
    error?: string;
    durationMs?: number;
}

// Delegate Discovery Types
export interface ActionInfo {
    name: string;
    displayName: string;
    description: string;
    requiredVariables: Record<string, string>;
}

export interface DelegateInfo {
    name: string;
    displayName: string;
    description: string;
    actions: ActionInfo[];
}

// Camel route info (lightweight for listing/testing)
export interface CamelRouteInfo {
    id: string;
    name?: string;
    description?: string;
    status?: string;
    tenantId?: string;
    nodes?: unknown[];
}
