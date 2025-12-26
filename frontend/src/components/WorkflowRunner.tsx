import { useState, useEffect, useCallback, useMemo } from 'react';
import type { TaskDto, ProcessStartResponse, ProcessDefinitionDto, TaskEvent, CamelRouteInfo } from '../types';
import {
    listProcessDefinitions,
    deployWorkflow,
    startProcess,
    getTasks,
    completeTask,
    getProcessStatus,
    getProcessVariables,
    claimTask,
    unclaimTask,
    getNotificationHistory,
    listCamelRoutes,
    testCamelRoute,
    deployCamelDemoRoutes
} from '../api';
import { API_BASE_URL } from '../api';
import ProcessViewer from './ProcessViewer';
import './WorkflowRunner.css';
import { WORKFLOW_TEMPLATES } from '../workflowTemplates';

export default function WorkflowRunner() {
    const [activePanel, setActivePanel] = useState<'camunda' | 'camel'>('camunda');

    const [processDefinitions, setProcessDefinitions] = useState<ProcessDefinitionDto[]>([]);
    const [selectedProcess, setSelectedProcess] = useState('');
    const [assignee, setAssignee] = useState('admin');
    const [tasks, setTasks] = useState<TaskDto[]>([]);
    const [runningProcesses, setRunningProcesses] = useState<ProcessStartResponse[]>([]);
    const [variables, setVariables] = useState<Record<string, string>>({});
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [message, setMessage] = useState<string | null>(null);
    const [taskEvents, setTaskEvents] = useState<TaskEvent[]>([]);
    const [showEventLog, setShowEventLog] = useState(true);
    const [camelEvents, setCamelEvents] = useState<TaskEvent[]>([]);
    const [showCamelLog, setShowCamelLog] = useState(true);

    // Camel routes
    const [camelRoutes, setCamelRoutes] = useState<CamelRouteInfo[]>([]);
    const [selectedCamelRoute, setSelectedCamelRoute] = useState('');
    const [camelPayload, setCamelPayload] = useState<string>('{}');
    const [camelResult, setCamelResult] = useState<string>('');
    const [camelLoading, setCamelLoading] = useState(false);

    // Task completion modal
    const [completingTask, setCompletingTask] = useState<TaskDto | null>(null);
    const [taskVariables, setTaskVariables] = useState<Record<string, unknown>>({});

    // Process variables panel
    const [selectedProcessInstance, setSelectedProcessInstance] = useState<string | null>(null);
    const [processVariablesData, setProcessVariablesData] = useState<Record<string, unknown>>({});
    const [showVariablesPanel, setShowVariablesPanel] = useState(false);

    // Search
    const [searchQuery, setSearchQuery] = useState('');

    // Process Viewer
    const [viewingProcessId, setViewingProcessId] = useState<string | null>(null);

    // Quick fill presets
    const QUICK_FILL_PRESETS: Record<string, Record<string, string>> = {
        'money-transfer': {
            sourceAccount: '1001234567',
            destAccount: '1009876543',
            amount: '1000000',
            description: 'Chuyển tiền test'
        },
        'leave-request': {
            employeeName: 'Nguyen Van A',
            fromDate: '2025-01-01',
            toDate: '2025-01-05',
            reason: 'Nghỉ phép năm'
        },
        'camel-integration': {
            demoType: 'integration',
            priority: 'high',
            inputData: '{"name":"John","amount":1500}'
        },
        'camel-routing': {
            demoType: 'routing',
            priority: 'high',
            inputData: '{"message":"Test routing"}'
        },
        'camel-transform': {
            demoType: 'transform',
            priority: 'low',
            inputData: '{"firstName":"John","lastName":"Doe","email":"john@example.com"}'
        },
        'camel-orchestrate': {
            demoType: 'orchestrate',
            priority: 'medium',
            inputData: '{}'
        }
    };

    const CAMEL_SAMPLE_PAYLOADS: Record<string, unknown> = {
        // === Dynamic Camel Route Builder templates ===
        'simple-log': { message: 'Hello Camel from Runner!' },
        'transform-json': { name: 'Camunda User', role: 'Developer', email: 'user@example.com' },
        'filter-route': { amount: 1500, description: 'Test filter - amount > 1000 passes' },
        'http-call': { endpoint: 'httpbin.org', action: 'get' },
        'split-aggregate': { items: ['A', 'B', 'C', 'D', 'E'], separator: ',' },

        // === Saga Money Transfer routes (body-based extraction) ===
        'saga-transfer': {
            sourceAccount: '1001234567',
            destAccount: '1009876543',
            amount: 500,
            description: 'Chuyển khoản test',
            transactionId: 'TX-' + Date.now()
        },
        'real-saga-transfer': {
            sourceAccount: '1001234567',
            destAccount: '1009876543',
            amount: 1000000,
            description: 'Chuyển khoản thực',
            transactionId: 'TX-REAL-001'
        },
        'debit-credit-manual': {
            sourceAccount: '1001234567',
            destAccount: '1009876543',
            amount: 5000,
            description: 'Ghi nợ/ghi có thủ công'
        },

        // === Integration routes ===
        callExternalApi: { message: 'Hello from Camel Runner!' },
        callApiWithParams: { userId: '42', action: 'fetch' },
        postToExternalApi: { title: 'Test Post', body: 'Sample post content', userId: 1 },

        // === Routing routes ===
        routeMessage: { priority: 'high', amount: 1500, message: 'Route me based on priority' },
        filterMessage: { amount: 1200, description: 'Filter test - amount >= 1000' },
        multicast: { message: 'Broadcast to all handlers', timestamp: new Date().toISOString() },

        // === Transform routes ===
        transformJson: { firstName: 'John', lastName: 'Doe', email: 'john@example.com' },
        mapToXmlStructure: { customer: { id: 'C001', name: 'Alice', email: 'alice@example.com' } },
        mapFields: { fName: 'John', lName: 'Doe', mail: 'john@example.com' },

        // === Orchestration routes ===
        orchestrate: { orderId: 'ORD-1001', userId: '42', items: ['item1', 'item2'] },
        pipeline: { items: [1, 2, 3], action: 'process', mode: 'sequential' },
        resilientCall: { id: 'test', retryCount: 3, timeout: 5000 },
    };

    // Filter tasks
    const filteredTasks = useMemo(() => {
        if (!searchQuery.trim()) return tasks;
        const query = searchQuery.toLowerCase();
        return tasks.filter(task =>
            task.name.toLowerCase().includes(query) ||
            task.id.toLowerCase().includes(query) ||
            (task.assignee?.toLowerCase().includes(query))
        );
    }, [tasks, searchQuery]);

    // Load process definitions
    useEffect(() => {
        loadProcessDefinitions();
    }, []);

    // Load Camel routes once
    useEffect(() => {
        listCamelRoutes()
            .then(setCamelRoutes)
            .catch(() => setCamelRoutes([]));
    }, []);

    const loadProcessDefinitions = async () => {
        try {
            const data = await listProcessDefinitions();
            setProcessDefinitions(data);
        } catch {
            setError('Failed to load process definitions');
        }
    };

    const handleDeployCamelIntegrationDemo = async () => {
        setLoading(true);
        setError(null);
        try {
            const tpl = WORKFLOW_TEMPLATES['camel-integration'];
            await deployWorkflow({
                processKey: tpl.processKey,
                processName: tpl.processName,
                description: tpl.description,
                historyTimeToLive: 180,
                steps: tpl.steps,
            });
            await loadProcessDefinitions();
            setSelectedProcess(tpl.processKey);
            setMessage(`✅ Deployed demo process: ${tpl.processKey}`);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to deploy demo process');
        } finally {
            setLoading(false);
        }
    };

    const handleDeployCamelDemoRoutes = async () => {
        setCamelLoading(true);
        setError(null);
        try {
            const result = await deployCamelDemoRoutes();
            await listCamelRoutes().then(setCamelRoutes).catch(() => setCamelRoutes([]));
            const deployedCount = result.deployed?.length ?? 0;
            setMessage(`✅ Deployed ${deployedCount} Camel demo routes`);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to deploy Camel demo routes');
        } finally {
            setCamelLoading(false);
        }
    };

    const loadTasks = useCallback(async () => {
        try {
            const data = await getTasks(assignee || undefined);
            setTasks(data);
        } catch {
            setError('Failed to load tasks');
        }
    }, [assignee]);

    useEffect(() => {
        loadTasks();
    }, [loadTasks]);

    // Auto-refresh every 5s
    useEffect(() => {
        const interval = setInterval(loadTasks, 5000);
        return () => clearInterval(interval);
    }, [loadTasks]);

    // Clear messages after 3s
    useEffect(() => {
        if (message) {
            const timer = setTimeout(() => setMessage(null), 3000);
            return () => clearTimeout(timer);
        }
    }, [message]);

    // Preload Camel run history and listen to backend task-event SSE
    useEffect(() => {
        getNotificationHistory({ type: 'CAMEL_NODE' })
            .then(events => setCamelEvents(events.slice(0, 50)))
            .catch(err => console.error('Failed to load Camel history', err));

        const eventSource = new EventSource(`${API_BASE_URL}/api/notifications/stream`);

        const handler = (event: MessageEvent) => {
            try {
                const data = JSON.parse(event.data) as TaskEvent;
                const statusIcon = data.status === 'FAILED' ? '❌' : '✅';
                const nodeInfo = data.nodeType ? `[${data.nodeType}] ` : '';
                const label = data.message || data.activityName || data.taskId || 'Task';
                const duration = data.durationMs ? ` (${data.durationMs}ms)` : '';
                setMessage(`${statusIcon} ${nodeInfo}${label}${duration}`);

                // Keep a short in-page log for recent events
                setTaskEvents(prev => {
                    const next = [{ ...data }, ...prev];
                    return next.slice(0, 50);
                });

                if (data.type === 'CAMEL_NODE') {
                    setCamelEvents(prev => {
                        const deduped = prev.filter(ev => ev.timestamp !== data.timestamp || ev.taskId !== data.taskId);
                        return [{ ...data }, ...deduped].slice(0, 50);
                    });
                }
            } catch (err) {
                console.error('Failed to parse task-event', err);
            }
        };

        eventSource.addEventListener('task-event', handler as EventListener);
        eventSource.onerror = (e) => {
            console.error('Task-event SSE error', e);
        };

        return () => {
            eventSource.removeEventListener('task-event', handler as EventListener);
            eventSource.close();
        };
    }, []);

    useEffect(() => {
        if (error) {
            const timer = setTimeout(() => setError(null), 5000);
            return () => clearTimeout(timer);
        }
    }, [error]);

    const handleStartProcess = async () => {
        if (!selectedProcess) return;
        setLoading(true);
        setError(null);
        try {
            const response = await startProcess(selectedProcess, variables);
            setRunningProcesses(prev => [...prev, response]);
            setMessage(`✅ Process started: ${response.processInstanceId.slice(0, 8)}...`);
            await loadTasks();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to start process');
        } finally {
            setLoading(false);
        }
    };

    const handleCompleteTask = async (taskId: string, vars: Record<string, unknown>) => {
        setLoading(true);
        setError(null);
        try {
            await completeTask(taskId, vars);
            setMessage(`✅ Task completed!`);
            setCompletingTask(null);
            setTaskVariables({});
            await loadTasks();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to complete task');
        } finally {
            setLoading(false);
        }
    };

    const handleClaimTask = async (taskId: string) => {
        setLoading(true);
        try {
            await claimTask(taskId, assignee);
            setMessage(`✅ Task claimed`);
            await loadTasks();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to claim');
        } finally {
            setLoading(false);
        }
    };

    const handleUnclaimTask = async (taskId: string) => {
        setLoading(true);
        try {
            await unclaimTask(taskId);
            setMessage(`✅ Task unclaimed`);
            await loadTasks();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to unclaim');
        } finally {
            setLoading(false);
        }
    };

    const handleViewVariables = async (processInstanceId: string) => {
        try {
            const vars = await getProcessVariables(processInstanceId);
            setProcessVariablesData(vars);
            setSelectedProcessInstance(processInstanceId);
            setShowVariablesPanel(true);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to get variables');
        }
    };

    const handleCheckStatus = async (processInstanceId: string) => {
        try {
            const status = await getProcessStatus(processInstanceId);
            setMessage(`Process: ${status.status}`);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to get status');
        }
    };

    const handleRunCamelRoute = async () => {
        if (!selectedCamelRoute) return;
        setCamelLoading(true);
        setCamelResult('');
        try {
            let payload: unknown = {};
            if (camelPayload.trim()) {
                try {
                    payload = JSON.parse(camelPayload);
                } catch (e) {
                    setError('Payload phải là JSON hợp lệ');
                    setCamelLoading(false);
                    return;
                }
            }

            const res = await testCamelRoute(selectedCamelRoute, payload);
            setCamelResult(JSON.stringify(res, null, 2));
            setMessage('✅ Camel route executed');
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to run Camel route');
        } finally {
            setCamelLoading(false);
        }
    };

    const applyCamelSample = () => {
        if (!selectedCamelRoute) return;

        const normalizedRouteId = selectedCamelRoute.includes('::')
            ? selectedCamelRoute.split('::').slice(1).join('::')
            : selectedCamelRoute;

        const sample = CAMEL_SAMPLE_PAYLOADS[normalizedRouteId] ?? CAMEL_SAMPLE_PAYLOADS[selectedCamelRoute];
        if (sample !== undefined) {
            setCamelPayload(JSON.stringify(sample, null, 2));
            return;
        }

        // Fallback sample so user can see it changed.
        setCamelPayload(JSON.stringify({ routeId: normalizedRouteId, message: 'Test message' }, null, 2));
    };

    const applyQuickFill = (preset: string) => {
        if (QUICK_FILL_PRESETS[preset]) {
            setVariables(QUICK_FILL_PRESETS[preset]);
        }
    };

    return (
        <div className="workflow-runner-v2">
            {/* Toast Messages */}
            <div className="toast-container">
                {message && <div className="toast success">{message}</div>}
                {error && <div className="toast error">❌ {error}</div>}
            </div>

            {/* Mode Switch */}
            <div className="runner-tabs" role="tablist" aria-label="Runner mode">
                <button
                    type="button"
                    className={`runner-tab ${activePanel === 'camunda' ? 'active' : ''}`}
                    onClick={() => setActivePanel('camunda')}
                    role="tab"
                    aria-selected={activePanel === 'camunda'}
                >
                    ⚙️ Camunda
                </button>
                <button
                    type="button"
                    className={`runner-tab ${activePanel === 'camel' ? 'active' : ''}`}
                    onClick={() => setActivePanel('camel')}
                    role="tab"
                    aria-selected={activePanel === 'camel'}
                >
                    🐪 Camel
                </button>
            </div>

            {/* Zone 1: Start Process - Compact Horizontal */}
            {activePanel === 'camunda' && (
            <section className="start-zone">
                <div className="start-row">
                    <div className="start-left">
                        <span className="zone-label">▶️ Start Process</span>
                        <select
                            value={selectedProcess}
                            onChange={e => setSelectedProcess(e.target.value)}
                            className="process-select"
                        >
                            <option value="">-- Select workflow --</option>
                            {processDefinitions.map(pd => (
                                <option key={pd.id} value={pd.key}>
                                    {pd.key} {pd.name ? `(${pd.name})` : ''} v{pd.version}
                                </option>
                            ))}
                        </select>
                        <div className="quick-fill-group">
                            <button onClick={() => applyQuickFill('money-transfer')} className="quick-btn" title="Money Transfer">
                                💸
                            </button>
                            <button onClick={() => applyQuickFill('leave-request')} className="quick-btn" title="Leave Request">
                                🏖️
                            </button>
                            <button onClick={() => applyQuickFill('camel-integration')} className="quick-btn" title="Camel: API Integration">
                                🔗
                            </button>
                            <button onClick={() => applyQuickFill('camel-routing')} className="quick-btn" title="Camel: Message Routing">
                                🔀
                            </button>
                            <button onClick={() => applyQuickFill('camel-transform')} className="quick-btn" title="Camel: Data Transform">
                                🔄
                            </button>
                            <button onClick={() => applyQuickFill('camel-orchestrate')} className="quick-btn" title="Camel: Orchestration">
                                🎭
                            </button>

                            <button
                                type="button"
                                onClick={handleDeployCamelIntegrationDemo}
                                className="quick-btn"
                                title="Deploy demo process: Camel Integration"
                                disabled={loading}
                            >
                                📦🐪
                            </button>
                        </div>
                    </div>
                    <button
                        onClick={handleStartProcess}
                        disabled={!selectedProcess || loading}
                        className="start-btn"
                    >
                        {loading ? '...' : '🚀 Start'}
                    </button>
                </div>

                {/* Variables inline */}
                {Object.keys(variables).length > 0 && (
                    <div className="variables-row">
                        {Object.entries(variables).map(([key, value]) => (
                            <div key={key} className="var-chip">
                                <span className="var-key">{key}:</span>
                                <input
                                    type="text"
                                    value={value}
                                    onChange={e => setVariables(prev => ({ ...prev, [key]: e.target.value }))}
                                />
                                <button onClick={() => {
                                    const { [key]: _, ...rest } = variables;
                                    setVariables(rest);
                                }}>×</button>
                            </div>
                        ))}
                        <button
                            className="add-var-chip"
                            onClick={() => {
                                const key = prompt('Variable name:');
                                if (key) setVariables(prev => ({ ...prev, [key]: '' }));
                            }}
                        >+ Add</button>
                    </div>
                )}

                {/* Running processes - compact */}
                {runningProcesses.length > 0 && (
                    <div className="running-row">
                        <span className="running-label">Active:</span>
                        {runningProcesses.slice(-3).map(proc => (
                            <span key={proc.processInstanceId} className="process-chip">
                                {proc.processKey}
                                <button onClick={() => setViewingProcessId(proc.processInstanceId)} title="View Progress">👁️</button>
                                <button onClick={() => handleViewVariables(proc.processInstanceId)} title="Variables">📊</button>
                                <button onClick={() => handleCheckStatus(proc.processInstanceId)} title="Status">📈</button>
                            </span>
                        ))}
                    </div>
                )}
            </section>
            )}

            {/* Camel Runner */}
            {activePanel === 'camel' && (
                <section className="start-zone camel-runner-zone">
                    <div className="start-row">
                        <div className="start-left">
                            <span className="zone-label">🐪 Camel Runner</span>

                            <button
                                type="button"
                                className="btn-secondary"
                                onClick={handleDeployCamelDemoRoutes}
                                disabled={camelLoading}
                            >
                                📦 Deploy demo routes
                            </button>

                            <select
                                value={selectedCamelRoute}
                                onChange={e => setSelectedCamelRoute(e.target.value)}
                                className="process-select"
                            >
                                <option value="">-- Select Camel route --</option>
                                {camelRoutes.map(r => (
                                    <option key={r.id} value={r.id}>
                                        {r.name || r.id} {r.status ? `(${r.status})` : ''}
                                    </option>
                                ))}
                            </select>
                            <textarea
                                className="camel-payload"
                                value={camelPayload}
                                onChange={e => setCamelPayload(e.target.value)}
                                placeholder="JSON payload"
                            />
                            <button
                                type="button"
                                className="btn-secondary"
                                onClick={applyCamelSample}
                                disabled={!selectedCamelRoute}
                            >
                                Sample
                            </button>
                        </div>
                        <button
                            onClick={handleRunCamelRoute}
                            disabled={!selectedCamelRoute || camelLoading}
                            className="start-btn"
                        >
                            {camelLoading ? '...' : '▶️ Run Camel'}
                        </button>
                    </div>
                    {camelResult && (
                        <pre className="camel-result">{camelResult}</pre>
                    )}
                </section>
            )}

            {/* Task Event Log */}
            {activePanel === 'camunda' && taskEvents.length > 0 && (
                <section className="task-event-log" style={{ marginTop: '24px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <h3>🔔 Task Events ({taskEvents.length})</h3>
                        <div style={{ display: 'flex', gap: '8px' }}>
                            <button onClick={() => setShowEventLog(s => !s)}>
                                {showEventLog ? 'Hide' : 'Show'}
                            </button>
                            <button onClick={() => setTaskEvents([])}>Clear</button>
                        </div>
                    </div>
                    {showEventLog && (
                        <div className="task-event-log-list" style={{ marginTop: '12px', display: 'grid', gap: '8px' }}>
                            {taskEvents.map((evt, idx) => {
                                const statusIcon = evt.status === 'FAILED' ? '❌' : '✅';
                                const nodeInfo = evt.nodeType ? `[${evt.nodeType}] ` : '';
                                const time = new Date(evt.timestamp).toLocaleTimeString('vi-VN', { hour12: false });
                                return (
                                    <div key={`${evt.taskId || evt.activityId || 'evt'}-${evt.timestamp}-${idx}`}
                                        style={{
                                            padding: '10px 12px',
                                            border: '1px solid #e0e0e0',
                                            borderRadius: '8px',
                                            background: evt.status === 'FAILED' ? '#fff4f4' : '#f7f9ff'
                                        }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                            <div>
                                                <strong>{statusIcon} {nodeInfo}{evt.message || evt.activityName || evt.taskId || 'Task'}</strong>
                                                {evt.durationMs ? <span style={{ marginLeft: '6px', color: '#666' }}>({evt.durationMs}ms)</span> : null}
                                            </div>
                                            <span style={{ color: '#777', fontSize: '12px' }}>{time}</span>
                                        </div>
                                        <div style={{ marginTop: '4px', color: '#555', fontSize: '13px' }}>
                                            {evt.processInstanceId ? `PI: ${evt.processInstanceId}` : ''}
                                            {evt.activityId ? ` • Activity: ${evt.activityId}` : ''}
                                            {evt.error ? ` • Error: ${evt.error}` : ''}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </section>
            )}

            {/* Camel Run Log */}
            {activePanel === 'camel' && camelEvents.length > 0 && (
                <section className="task-event-log" style={{ marginTop: '16px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <h3>🐪 Camel Runs ({camelEvents.length})</h3>
                        <div style={{ display: 'flex', gap: '8px' }}>
                            <button onClick={() => setShowCamelLog(s => !s)}>
                                {showCamelLog ? 'Hide' : 'Show'}
                            </button>
                            <button onClick={() => setCamelEvents([])}>Clear</button>
                        </div>
                    </div>
                    {showCamelLog && (
                        <div className="task-event-log-list" style={{ marginTop: '12px', display: 'grid', gap: '8px' }}>
                            {camelEvents.map((evt, idx) => {
                                const statusIcon = evt.status === 'FAILED' ? '❌' : '✅';
                                const nodeInfo = evt.nodeType ? `[${evt.nodeType}] ` : '';
                                const time = new Date(evt.timestamp).toLocaleTimeString('vi-VN', { hour12: false });
                                return (
                                    <div key={`${evt.taskId || evt.activityId || 'camel'}-${evt.timestamp}-${idx}`}
                                        style={{
                                            padding: '10px 12px',
                                            border: '1px solid #e0e0e0',
                                            borderRadius: '8px',
                                            background: evt.status === 'FAILED' ? '#fff4f4' : '#f6fff7'
                                        }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                            <div>
                                                <strong>{statusIcon} {nodeInfo}{evt.message || evt.taskId || 'Camel step'}</strong>
                                                {evt.durationMs ? <span style={{ marginLeft: '6px', color: '#666' }}>({evt.durationMs}ms)</span> : null}
                                            </div>
                                            <span style={{ color: '#777', fontSize: '12px' }}>{time}</span>
                                        </div>
                                        <div style={{ marginTop: '4px', color: '#555', fontSize: '13px' }}>
                                            {evt.routeId ? `Route: ${evt.routeId}` : ''}
                                            {evt.processInstanceId ? ` • PI: ${evt.processInstanceId}` : ''}
                                            {evt.error ? ` • Error: ${evt.error}` : ''}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </section>
            )}

            {/* Zone 2: Tasks Table */}
            {activePanel === 'camunda' && (
            <section className="tasks-zone">
                <div className="tasks-header">
                    <h2>📋 Tasks <span className="count-badge">{filteredTasks.length}</span></h2>
                    <div className="tasks-controls">
                        <div className="search-input">
                            <input
                                type="text"
                                value={searchQuery}
                                onChange={e => setSearchQuery(e.target.value)}
                                placeholder="🔍 Search..."
                            />
                        </div>
                        <div className="assignee-input">
                            <input
                                type="text"
                                value={assignee}
                                onChange={e => setAssignee(e.target.value)}
                                placeholder="👤 Assignee"
                            />
                        </div>
                        <button onClick={loadTasks} className="refresh-btn">🔄</button>
                    </div>
                </div>

                {filteredTasks.length > 0 ? (
                    <table className="tasks-table">
                        <thead>
                            <tr>
                                <th>Task Name</th>
                                <th>Process</th>
                                <th>Assignee</th>
                                <th>Created</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {filteredTasks.map(task => (
                                <tr key={task.id}>
                                    <td className="task-name">{task.name}</td>
                                    <td><code>{task.processInstanceId.slice(0, 8)}</code></td>
                                    <td>
                                        <span className={`assignee ${!task.assignee ? 'unassigned' : ''}`}>
                                            {task.assignee || 'Unassigned'}
                                        </span>
                                    </td>
                                    <td>{new Date(task.createTime).toLocaleTimeString()}</td>
                                    <td className="actions">
                                        <button onClick={() => setViewingProcessId(task.processInstanceId)} title="View Progress">
                                            👁️
                                        </button>
                                        <button onClick={() => handleViewVariables(task.processInstanceId)} title="View Data">
                                            📊
                                        </button>
                                        {!task.assignee ? (
                                            <button onClick={() => handleClaimTask(task.id)} disabled={loading} className="claim" title="Claim">
                                                🔒
                                            </button>
                                        ) : (
                                            <button onClick={() => handleUnclaimTask(task.id)} disabled={loading} title="Unclaim">
                                                🔓
                                            </button>
                                        )}
                                        <button
                                            onClick={() => {
                                                setCompletingTask(task);
                                                setTaskVariables({});
                                            }}
                                            disabled={loading}
                                            className="complete"
                                            title="Complete"
                                        >
                                            ✅
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                ) : (
                    <div className="empty-state">
                        <span>📭</span>
                        <p>No tasks found {assignee ? `for "${assignee}"` : ''}</p>
                    </div>
                )}
            </section>
            )}

            {/* Complete Task Modal */}
            {activePanel === 'camunda' && completingTask && (
                <div className="modal-overlay" onClick={() => setCompletingTask(null)}>
                    <div className="modal-box" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h3>✅ Complete: {completingTask.name}</h3>
                            <button onClick={() => setCompletingTask(null)}>×</button>
                        </div>
                        <div className="modal-body">
                            <div className="quick-decisions">
                                <button onClick={() => setTaskVariables(v => ({ ...v, approved: true }))} className="approve">
                                    👍 Approve
                                </button>
                                <button onClick={() => setTaskVariables(v => ({ ...v, approved: false }))} className="reject">
                                    👎 Reject
                                </button>
                            </div>
                            <div className="task-vars">
                                {Object.entries(taskVariables).map(([k, v]) => (
                                    <div key={k} className="var-row">
                                        <span>{k}:</span>
                                        <input
                                            value={String(v)}
                                            onChange={e => setTaskVariables(prev => ({ ...prev, [k]: e.target.value }))}
                                        />
                                        <button onClick={() => {
                                            const { [k]: _, ...rest } = taskVariables;
                                            setTaskVariables(rest);
                                        }}>×</button>
                                    </div>
                                ))}
                                <button onClick={() => {
                                    const key = prompt('Variable name:');
                                    if (key) setTaskVariables(prev => ({ ...prev, [key]: '' }));
                                }} className="add-var">+ Add Variable</button>
                            </div>
                        </div>
                        <div className="modal-footer">
                            <button onClick={() => setCompletingTask(null)} className="cancel">Cancel</button>
                            <button onClick={() => handleCompleteTask(completingTask.id, taskVariables)} disabled={loading} className="submit">
                                ✅ Complete Task
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Variables Panel Modal */}
            {activePanel === 'camunda' && showVariablesPanel && (
                <div className="modal-overlay" onClick={() => setShowVariablesPanel(false)}>
                    <div className="modal-box" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h3>📊 Process Variables</h3>
                            <button onClick={() => setShowVariablesPanel(false)}>×</button>
                        </div>
                        <div className="modal-body">
                            <p><strong>Process:</strong> <code>{selectedProcessInstance?.slice(0, 12)}...</code></p>
                            {Object.keys(processVariablesData).length > 0 ? (
                                <table className="vars-table">
                                    <thead>
                                        <tr><th>Name</th><th>Value</th><th>Type</th></tr>
                                    </thead>
                                    <tbody>
                                        {Object.entries(processVariablesData).map(([k, v]) => (
                                            <tr key={k}>
                                                <td><strong>{k}</strong></td>
                                                <td><code>{JSON.stringify(v)}</code></td>
                                                <td>{typeof v}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            ) : (
                                <p>No variables</p>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* Process Viewer Modal */}
            {activePanel === 'camunda' && viewingProcessId && (
                <ProcessViewer
                    processInstanceId={viewingProcessId}
                    onClose={() => setViewingProcessId(null)}
                />
            )}
        </div>
    );
}
