import { useState, useEffect } from 'react';
import type { DeploymentDto } from '../types';
import { listDeployments, deleteDeployment } from '../api';
import './DeploymentList.css';

interface Props {
    refreshTrigger?: number;
}

export default function DeploymentList({ refreshTrigger }: Props) {
    const [deployments, setDeployments] = useState<DeploymentDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const loadDeployments = async () => {
        setLoading(true);
        try {
            const data = await listDeployments();
            setDeployments(data);
        } catch (err) {
            setError('Failed to load deployments');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadDeployments();
    }, [refreshTrigger]);

    const handleDelete = async (id: string, name: string) => {
        if (!confirm(`Delete deployment "${name}"? This will also remove all process instances.`)) {
            return;
        }
        setLoading(true);
        try {
            await deleteDeployment(id);
            await loadDeployments();
        } catch (err) {
            setError('Failed to delete deployment');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="deployment-list">
            <div className="list-header">
                <h2>📦 Deployed Workflows</h2>
                <button onClick={loadDeployments} disabled={loading}>🔄 Refresh</button>
            </div>

            {loading && <div className="loading">Loading...</div>}
            {error && <div className="error">❌ {error}</div>}

            {deployments.length > 0 ? (
                <table className="deployments-table">
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Deployment ID</th>
                            <th>Deployed At</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        {deployments.map(d => (
                            <tr key={d.id}>
                                <td className="deployment-name">{d.name || 'N/A'}</td>
                                <td><code>{d.id}</code></td>
                                <td>{new Date(d.deploymentTime).toLocaleString()}</td>
                                <td>
                                    <button
                                        onClick={() => handleDelete(d.id, d.name || d.id)}
                                        className="delete-btn"
                                        disabled={loading}
                                    >
                                        🗑️ Delete
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            ) : (
                <p className="no-deployments">No deployments found</p>
            )}
        </div>
    );
}
