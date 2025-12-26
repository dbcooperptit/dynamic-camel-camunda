import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { API_BASE_URL, getNotificationHistory, getTasks, listCamelRoutes, listDeployments, listProcessDefinitions } from '../api';
import type { CamelRouteInfo, DeploymentDto, ProcessDefinitionDto, TaskDto, TaskEvent } from '../types';
import './Dashboard.css';

export default function Dashboard() {
    const [definitions, setDefinitions] = useState<ProcessDefinitionDto[]>([]);
    const [tasks, setTasks] = useState<TaskDto[]>([]);
    const [deployments, setDeployments] = useState<DeploymentDto[]>([]);
    const [camelRoutes, setCamelRoutes] = useState<CamelRouteInfo[]>([]);
    const [events, setEvents] = useState<TaskEvent[]>([]);
    const [isInitialLoading, setIsInitialLoading] = useState(true);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [lastUpdatedAt, setLastUpdatedAt] = useState<number | null>(null);

    const eventKeySetRef = useRef<Set<string>>(new Set());

    const addEvents = useCallback((incoming: TaskEvent[] | TaskEvent) => {
        const items = Array.isArray(incoming) ? incoming : [incoming];
        if (!items.length) return;

        setEvents(prev => {
            const next = [...prev];
            for (const e of items) {
                const key = `${e.taskId}|${e.type}|${e.status}|${e.timestamp}`;
                if (eventKeySetRef.current.has(key)) continue;
                eventKeySetRef.current.add(key);
                next.unshift(e);
            }

            // Bound memory + UI.
            const bounded = next.slice(0, 50);
            // If we dropped items, rebuild key set to stay consistent.
            if (bounded.length !== next.length) {
                const s = new Set<string>();
                for (const e of bounded) {
                    s.add(`${e.taskId}|${e.type}|${e.status}|${e.timestamp}`);
                }
                eventKeySetRef.current = s;
            }

            return bounded;
        });
    }, []);

    const loadData = useCallback(async (opts?: { keepUi?: boolean }) => {
        const keepUi = opts?.keepUi ?? false;
        if (keepUi) {
            setIsRefreshing(true);
        } else {
            setIsInitialLoading(true);
        }
        setError(null);
        try {
            const [defs, allTasks, deps, routes, history] = await Promise.all([
                listProcessDefinitions(),
                getTasks(),
                listDeployments(),
                listCamelRoutes(),
                getNotificationHistory(),
            ]);
            setDefinitions(defs);
            setTasks(allTasks);
            setDeployments(deps);
            setCamelRoutes(routes);
            // Reset event key set so refresh reflects latest history accurately.
            eventKeySetRef.current = new Set();
            setEvents([]);
            addEvents(history);
            setLastUpdatedAt(Date.now());
        } catch (error) {
            console.error('Failed to load dashboard:', error);
            setError('Failed to load dashboard data');
        } finally {
            setIsInitialLoading(false);
            setIsRefreshing(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    useEffect(() => {
        const es = new EventSource(`${API_BASE_URL}/api/notifications/stream`);

        const onTaskEvent = (evt: MessageEvent) => {
            try {
                const data = JSON.parse(evt.data) as TaskEvent;
                addEvents(data);
            } catch (err) {
                console.error('Failed to parse notification event:', err);
            }
        };

        es.addEventListener('task-event', onTaskEvent as EventListener);

        return () => {
            es.removeEventListener('task-event', onTaskEvent as EventListener);
            es.close();
        };
    }, [addEvents]);

    const navigate = useCallback((tab: string) => {
        window.dispatchEvent(new CustomEvent('navigate', { detail: tab }));
    }, []);

    const claimedCount = useMemo(() => tasks.filter(t => t.assignee).length, [tasks]);
    const unassignedCount = useMemo(() => tasks.filter(t => !t.assignee).length, [tasks]);
    const recentTasks = useMemo(() => tasks.slice(0, 5), [tasks]);
    const recentDefinitions = useMemo(() => definitions.slice(0, 6), [definitions]);

    const nowMs = useMemo(() => Date.now(), [lastUpdatedAt]);
    const ms24h = 24 * 60 * 60 * 1000;
    const ms7d = 7 * ms24h;

    const deploymentsLast24h = useMemo(() => {
        const cutoff = nowMs - ms24h;
        return deployments.filter(d => {
            const t = Date.parse(d.deploymentTime);
            return Number.isFinite(t) && t >= cutoff;
        }).length;
    }, [deployments, nowMs]);

    const deploymentsLast7d = useMemo(() => {
        const cutoff = nowMs - ms7d;
        return deployments.filter(d => {
            const t = Date.parse(d.deploymentTime);
            return Number.isFinite(t) && t >= cutoff;
        }).length;
    }, [deployments, nowMs]);

    const taskAssigneeStats = useMemo(() => {
        const map = new Map<string, number>();
        for (const t of tasks) {
            const key = t.assignee && t.assignee.trim() ? t.assignee.trim() : 'Unassigned';
            map.set(key, (map.get(key) ?? 0) + 1);
        }
        const items = Array.from(map.entries())
            .map(([assignee, count]) => ({ assignee, count }))
            .sort((a, b) => b.count - a.count);

        return {
            top: items.slice(0, 4),
            uniqueAssignees: items.filter(i => i.assignee !== 'Unassigned').length,
        };
    }, [tasks]);

    const camelRouteStatusStats = useMemo(() => {
        const total = camelRoutes.length;
        const started = camelRoutes.filter(r => (r.status || '').toLowerCase().includes('start')).length;
        const stopped = camelRoutes.filter(r => (r.status || '').toLowerCase().includes('stop')).length;
        return { total, started, stopped };
    }, [camelRoutes]);

    const isCamelEvent = useCallback((e: TaskEvent) => {
        const t = (e.type || '').toUpperCase();
        if (t.includes('CAMEL')) return true;
        // Many Camel notifications include routeId.
        if (e.routeId) return true;
        return false;
    }, []);

    const camelEvents = useMemo(() => events.filter(isCamelEvent), [events, isCamelEvent]);
    const camundaEvents = useMemo(() => events.filter(e => !isCamelEvent(e)), [events, isCamelEvent]);

    const eventStats = useMemo(() => {
        const cutoff1h = nowMs - 60 * 60 * 1000;
        const cutoff24h = nowMs - ms24h;

        const inLast1h = events.filter(e => e.timestamp >= cutoff1h).length;
        const inLast24h = events.filter(e => e.timestamp >= cutoff24h).length;
        const failedLast24h = events.filter(e => e.timestamp >= cutoff24h && e.status === 'FAILED').length;

        const camelFailedLast24h = camelEvents.filter(e => e.timestamp >= cutoff24h && e.status === 'FAILED').length;
        const camundaFailedLast24h = camundaEvents.filter(e => e.timestamp >= cutoff24h && e.status === 'FAILED').length;

        return { inLast1h, inLast24h, failedLast24h, camelFailedLast24h, camundaFailedLast24h };
    }, [events, camelEvents, camundaEvents, nowMs]);

    const formatEventTime = useCallback((timestamp: number) => {
        return new Date(timestamp).toLocaleString([], {
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
        });
    }, []);

    const formatTaskTime = useCallback((createTime: string) => {
        const date = new Date(createTime);
        const now = new Date();
        const isSameDay =
            date.getFullYear() === now.getFullYear() &&
            date.getMonth() === now.getMonth() &&
            date.getDate() === now.getDate();

        if (isSameDay) {
            return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        }

        return date.toLocaleString([], {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
        });
    }, []);

    if (isInitialLoading) {
        return (
            <div className="dashboard-v2 loading-state">
                <div className="dash-spinner" aria-hidden="true"></div>
                <p>Loading...</p>
            </div>
        );
    }

    return (
        <div className="dashboard-v2">
            {/* Header with inline stats */}
            <header className="dash-header">
                <div className="dash-header-left">
                    <h1>📊 Dashboard</h1>
                    <div className="stat-pills">
                        <span className="pill">{deployments.length} Deployments</span>
                        <span className="pill">{definitions.length} Workflows</span>
                        <span className="pill">{camelRoutes.length} Camel Routes</span>
                        <span className="pill">{tasks.length} Tasks</span>
                        {unassignedCount > 0 && <span className="pill warning">{unassignedCount} Unassigned</span>}
                        {claimedCount > 0 && <span className="pill success">{claimedCount} Claimed</span>}
                    </div>
                </div>
                <div className="dash-header-actions">
                    {lastUpdatedAt && (
                        <span className="dash-updated" title={new Date(lastUpdatedAt).toLocaleString()}>
                            Updated {new Date(lastUpdatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </span>
                    )}
                    <button
                        onClick={() => loadData({ keepUi: true })}
                        className="refresh-btn"
                        disabled={isRefreshing}
                        aria-busy={isRefreshing}
                    >
                        {isRefreshing ? '⏳ Refreshing…' : '🔄 Refresh'}
                    </button>
                </div>
            </header>

            {error && <div className="error">❌ {error}</div>}

            {/* KPI Summary */}
            <section className="dash-kpis" aria-label="Summary">
                <div className="kpi-card" role="group" aria-label="Deployments">
                    <div className="kpi-value">{deployments.length}</div>
                    <div className="kpi-label">Deployments</div>
                    <button className="kpi-action" onClick={() => navigate('deployments')}>Open</button>
                </div>
                <div className="kpi-card" role="group" aria-label="Workflows">
                    <div className="kpi-value">{definitions.length}</div>
                    <div className="kpi-label">Workflows</div>
                    <button className="kpi-action" onClick={() => navigate('builder')}>Builder</button>
                </div>
                <div className="kpi-card" role="group" aria-label="Tasks">
                    <div className="kpi-value">{tasks.length}</div>
                    <div className="kpi-label">Tasks (Active)</div>
                    <button className="kpi-action" onClick={() => navigate('runner')}>Runner</button>
                </div>
                <div className="kpi-card" role="group" aria-label="Unassigned tasks">
                    <div className="kpi-value">{unassignedCount}</div>
                    <div className="kpi-label">Unassigned</div>
                    <button className="kpi-action" onClick={() => navigate('runner')}>Claim</button>
                </div>
                <div className="kpi-card" role="group" aria-label="Camel routes">
                    <div className="kpi-value">{camelRoutes.length}</div>
                    <div className="kpi-label">Camel Routes</div>
                    <button className="kpi-action" onClick={() => navigate('camel')}>Open</button>
                </div>
            </section>

            {/* Two Column Content */}
            <div className="dash-grid">
                {/* Left: Workflows */}
                <section className="dash-card">
                    <div className="card-header">
                        <h2>🔧 Workflows</h2>
                        <button onClick={() => navigate('visual-builder')} className="action-link">
                            + Create
                        </button>
                    </div>
                    {definitions.length > 0 ? (
                        <ul className="item-list">
                            {recentDefinitions.map(def => (
                                <li key={def.id} className="workflow-item">
                                    <div className="item-main">
                                        <span className="item-key">{def.key}</span>
                                        {def.name && <span className="item-name">{def.name}</span>}
                                    </div>
                                    <span className="version-badge">v{def.version}</span>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <div className="empty-card">
                            <p>No workflows deployed</p>
                            <button onClick={() => navigate('visual-builder')} className="cta-btn">
                                🎨 Create First Workflow
                            </button>
                        </div>
                    )}
                </section>

                {/* Right: Tasks */}
                <section className="dash-card">
                    <div className="card-header">
                        <h2>📋 Recent Tasks</h2>
                        <button onClick={() => navigate('runner')} className="action-link">
                            View All →
                        </button>
                    </div>
                    {tasks.length > 0 ? (
                        <ul className="item-list">
                            {recentTasks.map(task => (
                                <li key={task.id} className="task-item">
                                    <div className="item-main">
                                        <span className="item-name">{task.name}</span>
                                        <span className="item-time">
                                            {formatTaskTime(task.createTime)}
                                        </span>
                                    </div>
                                    <span className={`assignee-badge ${!task.assignee ? 'unassigned' : ''}`}>
                                        {task.assignee || 'Unassigned'}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <div className="empty-card">
                            <p>No active tasks</p>
                            <button onClick={() => navigate('runner')} className="cta-btn">
                                ▶️ Start a Workflow
                            </button>
                        </div>
                    )}
                </section>
            </div>

            {/* Statistics */}
            <section className="dash-card dash-wide">
                <div className="card-header">
                    <h2>📈 Statistics</h2>
                    <button onClick={() => loadData({ keepUi: true })} className="action-link" disabled={isRefreshing}>
                        Refresh
                    </button>
                </div>
                <div className="stats-grid">
                    <div className="stats-block">
                        <div className="stats-title">Camunda Tasks</div>
                        <div className="stats-row"><span>Active</span><strong>{tasks.length}</strong></div>
                        <div className="stats-row"><span>Unassigned</span><strong>{unassignedCount}</strong></div>
                        <div className="stats-row"><span>Assignees</span><strong>{taskAssigneeStats.uniqueAssignees}</strong></div>
                        {taskAssigneeStats.top.length > 0 && (
                            <div className="stats-sub">
                                {taskAssigneeStats.top.map(i => (
                                    <div key={i.assignee} className="stats-mini">
                                        <span className="stats-mini-label">{i.assignee}</span>
                                        <span className="stats-mini-value">{i.count}</span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    <div className="stats-block">
                        <div className="stats-title">Deployments</div>
                        <div className="stats-row"><span>Total</span><strong>{deployments.length}</strong></div>
                        <div className="stats-row"><span>Last 24h</span><strong>{deploymentsLast24h}</strong></div>
                        <div className="stats-row"><span>Last 7d</span><strong>{deploymentsLast7d}</strong></div>
                        <button className="stats-action" onClick={() => navigate('deployments')}>Open Deployments</button>
                    </div>

                    <div className="stats-block">
                        <div className="stats-title">Activity</div>
                        <div className="stats-row"><span>Events (1h)</span><strong>{eventStats.inLast1h}</strong></div>
                        <div className="stats-row"><span>Events (24h)</span><strong>{eventStats.inLast24h}</strong></div>
                        <div className="stats-row"><span>Failed (24h)</span><strong>{eventStats.failedLast24h}</strong></div>
                        <div className="stats-row"><span>Camel failed</span><strong>{eventStats.camelFailedLast24h}</strong></div>
                        <div className="stats-row"><span>Camunda failed</span><strong>{eventStats.camundaFailedLast24h}</strong></div>
                    </div>

                    <div className="stats-block">
                        <div className="stats-title">Camel Routes</div>
                        <div className="stats-row"><span>Total</span><strong>{camelRouteStatusStats.total}</strong></div>
                        <div className="stats-row"><span>Started</span><strong>{camelRouteStatusStats.started}</strong></div>
                        <div className="stats-row"><span>Stopped</span><strong>{camelRouteStatusStats.stopped}</strong></div>
                        <button className="stats-action" onClick={() => navigate('camel')}>Open Camel</button>
                    </div>
                </div>
            </section>

            {/* Activity Feed */}
            <section className="dash-card dash-wide">
                <div className="card-header">
                    <h2>📡 Activity Feed</h2>
                    <button onClick={() => navigate('runner')} className="action-link">
                        Open Runner →
                    </button>
                </div>
                {events.length > 0 ? (
                    <div className="event-groups">
                        <div className="event-group">
                            <div className="event-group-header">
                                <span className="event-group-title">🐪 Camel</span>
                                <span className="event-group-count">{camelEvents.length}</span>
                            </div>
                            {camelEvents.length > 0 ? (
                                <ul className="event-list">
                                    {camelEvents.slice(0, 8).map((e) => (
                                        <li key={`${e.taskId}-${e.timestamp}-${e.status}`} className="event-item">
                                            <div className="event-main">
                                                <div className="event-top">
                                                    <span className="event-badge">{e.type}</span>
                                                    <span className={`event-badge status-${e.status.toLowerCase()}`}>{e.status}</span>
                                                    {e.routeId && <span className="event-meta">route: <code>{e.routeId}</code></span>}
                                                </div>
                                                <div className="event-message">{e.message}</div>
                                            </div>
                                            <div className="event-time" title={new Date(e.timestamp).toLocaleString()}>
                                                {formatEventTime(e.timestamp)}
                                            </div>
                                        </li>
                                    ))}
                                </ul>
                            ) : (
                                <div className="event-empty">No Camel activity yet</div>
                            )}
                        </div>

                        <div className="event-group">
                            <div className="event-group-header">
                                <span className="event-group-title">⚙️ Camunda</span>
                                <span className="event-group-count">{camundaEvents.length}</span>
                            </div>
                            {camundaEvents.length > 0 ? (
                                <ul className="event-list">
                                    {camundaEvents.slice(0, 8).map((e) => (
                                        <li key={`${e.taskId}-${e.timestamp}-${e.status}`} className="event-item">
                                            <div className="event-main">
                                                <div className="event-top">
                                                    <span className="event-badge">{e.type}</span>
                                                    <span className={`event-badge status-${e.status.toLowerCase()}`}>{e.status}</span>
                                                    {e.processInstanceId && <span className="event-meta">pid: <code>{e.processInstanceId.slice(0, 8)}…</code></span>}
                                                </div>
                                                <div className="event-message">{e.message}</div>
                                            </div>
                                            <div className="event-time" title={new Date(e.timestamp).toLocaleString()}>
                                                {formatEventTime(e.timestamp)}
                                            </div>
                                        </li>
                                    ))}
                                </ul>
                            ) : (
                                <div className="event-empty">No Camunda activity yet</div>
                            )}
                        </div>
                    </div>
                ) : (
                    <div className="empty-card">
                        <p>No recent activity yet</p>
                        <button onClick={() => navigate('runner')} className="cta-btn">
                            ▶️ Start something
                        </button>
                    </div>
                )}
            </section>

            {/* Quick Actions */}
            <footer className="quick-actions">
                <button onClick={() => navigate('visual-builder')} className="action-btn primary">
                    🎨 Create Workflow
                </button>
                <button onClick={() => navigate('runner')} className="action-btn success">
                    ▶️ Run Workflow
                </button>
                <button onClick={() => navigate('deployments')} className="action-btn neutral">
                    📦 Deployments
                </button>
            </footer>
        </div>
    );
}
