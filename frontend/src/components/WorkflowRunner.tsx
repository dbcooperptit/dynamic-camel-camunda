import { useState, useEffect, useCallback, useMemo } from 'react';
import type { TaskDto, ProcessStartResponse, ProcessDefinitionDto } from '../types';
import {
    listProcessDefinitions,
    startProcess,
    getTasks,
    completeTask,
    getProcessStatus,
    getProcessVariables,
    claimTask,
    unclaimTask
} from '../api';
import ProcessViewer from './ProcessViewer';
import './WorkflowRunner.css';

export default function WorkflowRunner() {
    const [processDefinitions, setProcessDefinitions] = useState<ProcessDefinitionDto[]>([]);
    const [selectedProcess, setSelectedProcess] = useState('');
    const [assignee, setAssignee] = useState('admin');
    const [tasks, setTasks] = useState<TaskDto[]>([]);
    const [runningProcesses, setRunningProcesses] = useState<ProcessStartResponse[]>([]);
    const [variables, setVariables] = useState<Record<string, string>>({});
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [message, setMessage] = useState<string | null>(null);

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

    const loadProcessDefinitions = async () => {
        try {
            const data = await listProcessDefinitions();
            setProcessDefinitions(data);
        } catch {
            setError('Failed to load process definitions');
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

            {/* Zone 1: Start Process - Compact Horizontal */}
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

            {/* Zone 2: Tasks Table */}
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

            {/* Complete Task Modal */}
            {completingTask && (
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
            {showVariablesPanel && (
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
            {viewingProcessId && (
                <ProcessViewer
                    processInstanceId={viewingProcessId}
                    onClose={() => setViewingProcessId(null)}
                />
            )}
        </div>
    );
}
