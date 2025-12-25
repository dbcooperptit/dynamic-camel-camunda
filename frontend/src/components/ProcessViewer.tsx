import { useState, useEffect, useRef, useCallback } from 'react';
import BpmnViewer from 'bpmn-js/lib/NavigatedViewer';
import type { ActivityEvent } from '../types';
import { getProcessDiagram, getProcessActivities, subscribeToActivityStream } from '../api';
import './ProcessViewer.css';

interface Props {
    processInstanceId: string;
    onClose: () => void;
}

export default function ProcessViewer({ processInstanceId, onClose }: Props) {
    const containerRef = useRef<HTMLDivElement>(null);
    const viewerRef = useRef<BpmnViewer | null>(null);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [activities, setActivities] = useState<ActivityEvent[]>([]);
    const [connected, setConnected] = useState(false);
    const [expandedVars, setExpandedVars] = useState<Set<string>>(new Set());

    // Track completed and current activities
    const completedActivities = new Set<string>();
    const currentActivities = new Set<string>();

    activities.forEach(event => {
        if (event.eventType === 'end') {
            completedActivities.add(event.activityId);
            currentActivities.delete(event.activityId);
        } else if (event.eventType === 'start' && !completedActivities.has(event.activityId)) {
            currentActivities.add(event.activityId);
        }
    });

    // Highlight activities in diagram
    const highlightActivities = useCallback(() => {
        const viewer = viewerRef.current;
        if (!viewer) return;

        const canvas = viewer.get('canvas') as {
            addMarker: (id: string, marker: string) => void;
            removeMarker: (id: string, marker: string) => void;
        };

        if (!canvas) return;

        // Clear old markers
        activities.forEach(event => {
            try {
                canvas.removeMarker(event.activityId, 'highlight-completed');
                canvas.removeMarker(event.activityId, 'highlight-current');
            } catch {
                // Ignore if element doesn't exist
            }
        });

        // Add completed markers
        completedActivities.forEach(activityId => {
            try {
                canvas.addMarker(activityId, 'highlight-completed');
            } catch {
                // Ignore if element doesn't exist
            }
        });

        // Add current markers
        currentActivities.forEach(activityId => {
            try {
                canvas.addMarker(activityId, 'highlight-current');
            } catch {
                // Ignore if element doesn't exist
            }
        });
    }, [activities, completedActivities, currentActivities]);

    // Initialize BPMN viewer
    useEffect(() => {
        if (!containerRef.current) return;

        const viewer = new BpmnViewer({
            container: containerRef.current,
        });

        viewerRef.current = viewer;

        // Load diagram
        const loadDiagram = async () => {
            try {
                setLoading(true);
                const xml = await getProcessDiagram(processInstanceId);
                await viewer.importXML(xml);

                // Fit to viewport
                const canvas = viewer.get('canvas') as { zoom: (level: string) => void };
                if (canvas) {
                    canvas.zoom('fit-viewport');
                }

                setLoading(false);
                setError(null);
            } catch (err) {
                console.error('Failed to load diagram:', err);
                setError('Failed to load workflow diagram');
                setLoading(false);
            }
        };

        loadDiagram();

        return () => {
            viewer.destroy();
        };
    }, [processInstanceId]);

    // Load initial activities and subscribe to SSE
    useEffect(() => {
        // Load existing activities
        getProcessActivities(processInstanceId)
            .then(data => {
                setActivities(data);
            })
            .catch(err => {
                console.error('Failed to load activities:', err);
            });

        // Subscribe to realtime updates
        setConnected(true);
        const unsubscribe = subscribeToActivityStream(
            processInstanceId,
            (event) => {
                setActivities(prev => [...prev, event]);
            },
            () => {
                setConnected(false);
            }
        );

        return () => {
            unsubscribe();
        };
    }, [processInstanceId]);

    // Highlight when activities change
    useEffect(() => {
        highlightActivities();
    }, [highlightActivities]);

    // Toggle variable expansion
    const toggleVars = (eventId: string) => {
        setExpandedVars(prev => {
            const next = new Set(prev);
            if (next.has(eventId)) {
                next.delete(eventId);
            } else {
                next.add(eventId);
            }
            return next;
        });
    };

    // Format timestamp
    const formatTime = (timestamp: string) => {
        try {
            const date = new Date(timestamp);
            return date.toLocaleTimeString('vi-VN', {
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
            });
        } catch {
            return timestamp;
        }
    };

    // Get activity type icon
    const getTypeIcon = (type: string) => {
        switch (type) {
            case 'startEvent': return '▶️';
            case 'endEvent': return '⏹️';
            case 'userTask': return '👤';
            case 'serviceTask': return '⚙️';
            case 'exclusiveGateway': return '◇';
            case 'parallelGateway': return '⊕';
            default: return '📌';
        }
    };

    return (
        <div className="process-viewer-overlay" onClick={onClose}>
            <div className="process-viewer" onClick={e => e.stopPropagation()}>
                {/* Header */}
                <div className="process-viewer-header">
                    <h2>
                        <span className="icon">📊</span>
                        Process Visualization
                        <span className="process-id">{processInstanceId}</span>
                    </h2>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                        <div className={`connection-status ${connected ? 'connected' : 'disconnected'}`}>
                            <span className="connection-dot"></span>
                            {connected ? 'Live' : 'Disconnected'}
                        </div>
                        <button className="close-btn" onClick={onClose}>✕</button>
                    </div>
                </div>

                {/* Content */}
                <div className="process-viewer-content">
                    {/* BPMN Diagram */}
                    <div className="diagram-panel">
                        <div className="diagram-container" ref={containerRef}>
                            {loading && (
                                <div className="diagram-loading">
                                    <div className="spinner"></div>
                                    <span>Loading diagram...</span>
                                </div>
                            )}
                            {error && (
                                <div className="diagram-loading">
                                    <span>❌ {error}</span>
                                </div>
                            )}
                        </div>
                        <div className="diagram-legend">
                            <div className="legend-item">
                                <div className="legend-dot current"></div>
                                <span>Current</span>
                            </div>
                            <div className="legend-item">
                                <div className="legend-dot completed"></div>
                                <span>Completed</span>
                            </div>
                            <div className="legend-item">
                                <div className="legend-dot pending"></div>
                                <span>Pending</span>
                            </div>
                        </div>
                    </div>

                    {/* Activity Log */}
                    <div className="log-panel">
                        <div className="log-header">
                            <h3>
                                📋 Activity Log
                                <span className="activity-count">{activities.length}</span>
                            </h3>
                        </div>
                        <div className="log-list">
                            {activities.length === 0 ? (
                                <div className="log-empty">
                                    <span className="icon">⏳</span>
                                    <span>Waiting for activities...</span>
                                    <span style={{ fontSize: '0.8rem' }}>Events will appear here in realtime</span>
                                </div>
                            ) : (
                                [...activities].reverse().map((event, index) => {
                                    const eventId = `${event.activityId}-${event.eventType}-${index}`;
                                    const isCurrent = currentActivities.has(event.activityId) && event.eventType === 'start';

                                    return (
                                        <div
                                            key={eventId}
                                            className={`log-item ${event.eventType} ${isCurrent ? 'current' : ''}`}
                                            onClick={() => toggleVars(eventId)}
                                        >
                                            <div className="log-item-header">
                                                <span className="log-item-type">
                                                    {getTypeIcon(event.activityType)} {event.activityType}
                                                </span>
                                                <span className="log-item-time">{formatTime(event.timestamp)}</span>
                                            </div>
                                            <div className="log-item-name">{event.activityName}</div>
                                            {event.message && (
                                                <div className="log-item-message">{event.message}</div>
                                            )}
                                            {event.durationMs !== undefined && event.durationMs > 0 && (
                                                <div className="log-item-duration">
                                                    ⏱️ {event.durationMs}ms
                                                </div>
                                            )}
                                            {event.variables && Object.keys(event.variables).length > 0 && (
                                                <div className="log-item-variables">
                                                    <div className="variables-toggle">
                                                        {expandedVars.has(eventId) ? '▼' : '▶'} Variables ({Object.keys(event.variables).length})
                                                    </div>
                                                    {expandedVars.has(eventId) && (
                                                        <div className="variables-content">
                                                            <pre>{JSON.stringify(event.variables, null, 2)}</pre>
                                                        </div>
                                                    )}
                                                </div>
                                            )}
                                        </div>
                                    );
                                })
                            )}
                        </div>
                    </div>
                </div>
            </div>

            {/* CSS for BPMN highlighting - inject into page */}
            <style>{`
                .highlight-completed:not(.djs-connection) .djs-visual > :first-child {
                    stroke: #3b82f6 !important;
                    stroke-width: 3px !important;
                    fill: rgba(59, 130, 246, 0.15) !important;
                }
                .highlight-current:not(.djs-connection) .djs-visual > :first-child {
                    stroke: #22c55e !important;
                    stroke-width: 3px !important;
                    fill: rgba(34, 197, 94, 0.15) !important;
                    animation: currentPulse 1.5s ease-in-out infinite;
                }
                @keyframes currentPulse {
                    0%, 100% { stroke-opacity: 1; }
                    50% { stroke-opacity: 0.6; }
                }
            `}</style>
        </div>
    );
}
