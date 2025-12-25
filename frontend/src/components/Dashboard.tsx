import { useState, useEffect } from 'react';
import { listProcessDefinitions, getTasks } from '../api';
import type { ProcessDefinitionDto, TaskDto } from '../types';
import './Dashboard.css';

export default function Dashboard() {
    const [definitions, setDefinitions] = useState<ProcessDefinitionDto[]>([]);
    const [tasks, setTasks] = useState<TaskDto[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setLoading(true);
        try {
            const [defs, allTasks] = await Promise.all([
                listProcessDefinitions(),
                getTasks(),
            ]);
            setDefinitions(defs);
            setTasks(allTasks);
        } catch (error) {
            console.error('Failed to load dashboard:', error);
        } finally {
            setLoading(false);
        }
    };

    const navigate = (tab: string) => {
        window.dispatchEvent(new CustomEvent('navigate', { detail: tab }));
    };

    if (loading) {
        return (
            <div className="dashboard-v2 loading-state">
                <div className="spinner">⏳</div>
                <p>Loading...</p>
            </div>
        );
    }

    const claimedCount = tasks.filter(t => t.assignee).length;
    const pendingCount = tasks.filter(t => !t.assignee).length;

    return (
        <div className="dashboard-v2">
            {/* Header with inline stats */}
            <header className="dash-header">
                <div className="header-left">
                    <h1>📊 Dashboard</h1>
                    <div className="stat-pills">
                        <span className="pill">{definitions.length} Workflows</span>
                        <span className="pill">{tasks.length} Tasks</span>
                        {pendingCount > 0 && <span className="pill warning">{pendingCount} Pending</span>}
                    </div>
                </div>
                <button onClick={loadData} className="refresh-btn">🔄 Refresh</button>
            </header>

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
                            {definitions.map(def => (
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
                            {tasks.slice(0, 5).map(task => (
                                <li key={task.id} className="task-item">
                                    <div className="item-main">
                                        <span className="item-name">{task.name}</span>
                                        <span className="item-time">
                                            {new Date(task.createTime).toLocaleTimeString()}
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
