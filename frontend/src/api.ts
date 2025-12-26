import type {
    WorkflowDefinitionRequest,
    DeploymentResponse,
    DeploymentDto,
    ProcessDefinitionDto,
    TaskDto,
    ProcessStartResponse,
    ProcessStatusResponse,
    ActivityInstance,
    ActivityEvent,
    TaskEvent,
    DelegateInfo,
    ActionInfo,
    CamelRouteInfo
} from './types';

function normalizeBaseUrl(url: string): string {
    return url.replace(/\/+$/, '');
}

export const API_BASE_URL = normalizeBaseUrl(
    (import.meta.env.VITE_API_BASE_URL && import.meta.env.VITE_API_BASE_URL.trim())
        ? import.meta.env.VITE_API_BASE_URL.trim()
        : 'http://localhost:8080'
);

// Backwards-compatible export used across components.
// Prefer API_BASE_URL for new code.
export const API_BASE = API_BASE_URL;

type HeaderValue = string;

export function buildApiHeaders(extra: Record<string, HeaderValue> = {}): Record<string, HeaderValue> {
    const headers: Record<string, HeaderValue> = { ...extra };

    // Optional multi-tenant header
    const tenantId = import.meta.env.VITE_TENANT_ID;
    if (tenantId && tenantId.trim()) {
        headers['X-Tenant-Id'] = tenantId.trim();
    }

    // Optional API key header for protecting /api/camel-routes
    const apiKey = import.meta.env.VITE_API_KEY;
    if (apiKey && apiKey.trim()) {
        headers['X-API-Key'] = apiKey.trim();
    }

    return headers;
}

// Process Builder API
export async function deployWorkflow(request: WorkflowDefinitionRequest): Promise<DeploymentResponse> {
    const response = await fetch(`${API_BASE_URL}/api/process-builder/deploy`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
    });
    if (!response.ok) {
        const error = await response.text();
        throw new Error(error || 'Failed to deploy workflow');
    }
    return response.json();
}

export async function previewWorkflow(request: WorkflowDefinitionRequest): Promise<DeploymentResponse> {
    const response = await fetch(`${API_BASE_URL}/api/process-builder/preview`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
    });
    if (!response.ok) {
        const error = await response.text();
        throw new Error(error || 'Failed to preview workflow');
    }
    return response.json();
}

export async function listDeployments(): Promise<DeploymentDto[]> {
    const response = await fetch(`${API_BASE_URL}/api/process-builder/deployments`);
    if (!response.ok) throw new Error('Failed to fetch deployments');
    return response.json();
}

export async function listProcessDefinitions(): Promise<ProcessDefinitionDto[]> {
    const response = await fetch(`${API_BASE_URL}/api/workflow/definitions`);
    if (!response.ok) throw new Error('Failed to fetch process definitions');
    return response.json();
}

export async function deleteDeployment(deploymentId: string, cascade = true): Promise<void> {
    const response = await fetch(
        `${API_BASE_URL}/api/process-builder/deployments/${deploymentId}?cascade=${cascade}`,
        { method: 'DELETE' }
    );
    if (!response.ok) throw new Error('Failed to delete deployment');
}

// Workflow API
export async function startProcess(processKey: string, variables: Record<string, unknown> = {}): Promise<ProcessStartResponse> {
    const response = await fetch(`${API_BASE_URL}/api/workflow/start/${processKey}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ variables }),
    });
    if (!response.ok) {
        const error = await response.text();
        throw new Error(error || 'Failed to start process');
    }
    return response.json();
}

export async function getTasks(assignee?: string): Promise<TaskDto[]> {
    const url = assignee
        ? `${API_BASE_URL}/api/workflow/tasks?assignee=${encodeURIComponent(assignee)}`
        : `${API_BASE_URL}/api/workflow/tasks`;
    const response = await fetch(url);
    if (!response.ok) throw new Error('Failed to fetch tasks');
    return response.json();
}

export async function completeTask(taskId: string, variables: Record<string, unknown> = {}): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/api/workflow/tasks/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ variables }),
    });
    if (!response.ok) throw new Error('Failed to complete task');
}

export async function getProcessStatus(processInstanceId: string): Promise<ProcessStatusResponse> {
    const response = await fetch(`${API_BASE_URL}/api/workflow/process/${processInstanceId}/status`);
    if (!response.ok) throw new Error('Failed to get process status');
    return response.json();
}


// Get process instance variables (backend returns Map<String, Object>)
export async function getProcessVariables(processInstanceId: string): Promise<Record<string, unknown>> {
    const response = await fetch(`${API_BASE_URL}/api/workflow/process/${processInstanceId}/variables`);
    if (!response.ok) throw new Error('Failed to get process variables');
    return response.json();
}

// Get process activity history
export async function getProcessHistory(processInstanceId: string): Promise<ActivityInstance[]> {
    const response = await fetch(`${API_BASE_URL}/api/workflow/process/${processInstanceId}/activities`);
    if (!response.ok) throw new Error('Failed to get process history');
    return response.json();
}

// Claim a task
export async function claimTask(taskId: string, assignee: string): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/api/workflow/tasks/${taskId}/claim`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ assignee }),
    });
    if (!response.ok) throw new Error('Failed to claim task');
}

// Unclaim a task (release assignment)
export async function unclaimTask(taskId: string): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/api/workflow/tasks/${taskId}/unclaim`, {
        method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to unclaim task');
}

// Get BPMN diagram XML for a process definition
export async function getBpmnDiagram(processDefinitionId: string): Promise<string> {
    const response = await fetch(`${API_BASE_URL}/api/workflow/definitions/${processDefinitionId}/xml`);
    if (!response.ok) throw new Error('Failed to get BPMN diagram');
    return response.text();
}

// ============ Process Visualization APIs ============

// Get BPMN diagram for a running process instance
export async function getProcessDiagram(processInstanceId: string): Promise<string> {
    const response = await fetch(`${API_BASE_URL}/api/workflow/process/${processInstanceId}/diagram`);
    if (!response.ok) throw new Error('Failed to get process diagram');
    return response.text();
}

// Get activity history for a process instance
export async function getProcessActivities(processInstanceId: string): Promise<ActivityEvent[]> {
    const response = await fetch(`${API_BASE_URL}/api/workflow/process/${processInstanceId}/activities`);
    if (!response.ok) throw new Error('Failed to get activities');
    return response.json();
}

// Subscribe to SSE activity stream for realtime updates
export function subscribeToActivityStream(
    processInstanceId: string,
    onEvent: (event: ActivityEvent) => void,
    onError?: (error: Error) => void
): () => void {
    const eventSource = new EventSource(
        `${API_BASE_URL}/api/workflow/process/${processInstanceId}/activities/stream`
    );

    eventSource.addEventListener('activity', (e: MessageEvent) => {
        try {
            const data = JSON.parse(e.data) as ActivityEvent;
            onEvent(data);
        } catch (err) {
            console.error('Failed to parse activity event:', err);
        }
    });

    eventSource.onerror = (e) => {
        console.error('SSE connection error:', e);
        onError?.(new Error('SSE connection failed'));
    };

    // Return cleanup function
    return () => {
        eventSource.close();
    };
}

// ============ Notification History (Camel / Saga / Tasks) ============

export async function getNotificationHistory(params: { type?: string; routeId?: string } = {}): Promise<TaskEvent[]> {
    const search = new URLSearchParams();
    if (params.type) search.append('type', params.type);
    if (params.routeId) search.append('routeId', params.routeId);

    const qs = search.toString();
    const url = `${API_BASE_URL}/api/notifications/history${qs ? `?${qs}` : ''}`;

    const response = await fetch(url);
    // Backwards compatibility: older backend builds may not expose this endpoint yet.
    // In that case, fall back to "live-only" (SSE stream) behavior.
    if (response.status === 404) return [];
    if (!response.ok) throw new Error('Failed to fetch notification history');
    return response.json();
}

// ============ Delegate Discovery APIs ============

// Get all available delegates
export async function getDelegates(): Promise<DelegateInfo[]> {
    const response = await fetch(`${API_BASE_URL}/api/delegates`);
    if (!response.ok) throw new Error('Failed to fetch delegates');
    return response.json();
}

// Get a specific delegate by name
export async function getDelegate(name: string): Promise<DelegateInfo> {
    const response = await fetch(`${API_BASE_URL}/api/delegates/${name}`);
    if (!response.ok) throw new Error('Failed to fetch delegate');
    return response.json();
}

// Get actions for a specific delegate
export async function getDelegateActions(name: string): Promise<ActionInfo[]> {
    const response = await fetch(`${API_BASE_URL}/api/delegates/${name}/actions`);
    if (!response.ok) throw new Error('Failed to fetch delegate actions');
    return response.json();
}

// ============ Camel Routes ============

export async function listCamelRoutes(): Promise<CamelRouteInfo[]> {
    const response = await fetch(`${API_BASE_URL}/api/camel-routes`, {
        headers: buildApiHeaders(),
    });
    if (!response.ok) throw new Error('Failed to fetch Camel routes');
    return response.json();
}

export async function testCamelRoute(routeId: string, payload: unknown): Promise<unknown> {
    const response = await fetch(`${API_BASE_URL}/api/camel-routes/${encodeURIComponent(routeId)}/test`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...buildApiHeaders() },
        body: JSON.stringify(payload ?? {}),
    });
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || 'Failed to run Camel route');
    }
    return response.json();
}

export async function deployCamelDemoRoutes(routeIds?: string[]): Promise<{ success: boolean; deployed: string[]; errors?: Record<string, string> }> {
    const response = await fetch(`${API_BASE_URL}/api/camel/demo/deploy`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...buildApiHeaders() },
        body: JSON.stringify(routeIds && routeIds.length ? { routeIds } : {}),
    });
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || 'Failed to deploy Camel demo routes');
    }
    return response.json();
}
