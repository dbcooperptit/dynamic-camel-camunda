import { useState, useCallback, useRef, useEffect } from 'react';
import {
    ReactFlow,
    Controls,
    Background,
    MiniMap,
    addEdge,
    useNodesState,
    useEdgesState,
    type Node,
    type Edge,
    type Connection,
    type NodeTypes,
    BackgroundVariant,
    Panel,
    MarkerType,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { StartNode, EndNode, UserTaskNode, ServiceTaskNode, GatewayNode } from './nodes';
import type { StepDefinition, StepType, WorkflowDefinitionRequest, BpmnNodeData, DelegateInfo } from '../types';
import { deployWorkflow, previewWorkflow, getDelegates } from '../api';
import { WORKFLOW_TEMPLATES, convertTemplateToNodes } from '../workflowTemplates';
import './VisualWorkflowBuilder.css';

// Custom node types
const nodeTypes: NodeTypes = {
    startNode: StartNode,
    endNode: EndNode,
    userTask: UserTaskNode,
    serviceTask: ServiceTaskNode,
    gateway: GatewayNode,
};

// Node templates for drag & drop
const NODE_TEMPLATES = [
    { type: 'startNode', label: 'Start', icon: '▶️', stepType: 'START_EVENT' as StepType },
    { type: 'userTask', label: 'User Task', icon: '👤', stepType: 'USER_TASK' as StepType },
    { type: 'serviceTask', label: 'Service Task', icon: '⚙️', stepType: 'SERVICE_TASK' as StepType },
    { type: 'gateway', label: 'Gateway', icon: '◇', stepType: 'EXCLUSIVE_GATEWAY' as StepType },
    { type: 'endNode', label: 'End', icon: '⏹️', stepType: 'END_EVENT' as StepType },
];

// Workflow templates - now imported from shared workflowTemplates.ts

interface Props {
    onDeploySuccess?: () => void;
}

// Initial nodes
const initialNodes: Node<BpmnNodeData>[] = [
    {
        id: 'start',
        type: 'startNode',
        position: { x: 50, y: 200 },
        data: { label: 'Start', stepType: 'START_EVENT' },
    },
    {
        id: 'end',
        type: 'endNode',
        position: { x: 600, y: 200 },
        data: { label: 'End', stepType: 'END_EVENT' },
    },
];

const initialEdges: Edge[] = [];

let nodeId = 0;
const getNextId = () => `node_${++nodeId}`;

export default function VisualWorkflowBuilder({ onDeploySuccess }: Props) {
    const reactFlowWrapper = useRef<HTMLDivElement>(null);
    const [nodes, setNodes, onNodesChange] = useNodesState<Node<BpmnNodeData>>(initialNodes);
    const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
    const [selectedNode, setSelectedNode] = useState<Node<BpmnNodeData> | null>(null);

    // Workflow metadata
    const [processKey, setProcessKey] = useState('my-workflow');
    const [processName, setProcessName] = useState('My Workflow');
    const [description, setDescription] = useState('');

    // UI state
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [result, setResult] = useState<{ message?: string; bpmnXml?: string } | null>(null);
    const [showXml, setShowXml] = useState(false);

    // Delegate registry for dynamic selection
    const [delegates, setDelegates] = useState<DelegateInfo[]>([]);

    // Fetch delegates on component mount
    useEffect(() => {
        getDelegates()
            .then(setDelegates)
            .catch((err) => console.error('Failed to load delegates:', err));
    }, []);

    // Connect nodes
    const onConnect = useCallback(
        (params: Connection) => {
            setEdges((eds) =>
                addEdge(
                    {
                        ...params,
                        type: 'smoothstep',
                        animated: true,
                        markerEnd: { type: MarkerType.ArrowClosed },
                        style: { stroke: '#6366f1', strokeWidth: 2 },
                    },
                    eds
                )
            );
        },
        [setEdges]
    );

    // Handle node selection
    const onNodeClick = useCallback((_: React.MouseEvent, node: Node<BpmnNodeData>) => {
        setSelectedNode(node);
    }, []);

    // Handle pane click (deselect)
    const onPaneClick = useCallback(() => {
        setSelectedNode(null);
    }, []);

    // Handle drag from sidebar
    const onDragStart = useCallback((event: React.DragEvent, nodeType: string, stepType: StepType) => {
        event.dataTransfer.setData('application/reactflow-type', nodeType);
        event.dataTransfer.setData('application/reactflow-steptype', stepType);
        event.dataTransfer.effectAllowed = 'move';
    }, []);

    // Handle drop on canvas
    const onDrop = useCallback(
        (event: React.DragEvent) => {
            event.preventDefault();

            const type = event.dataTransfer.getData('application/reactflow-type');
            const stepType = event.dataTransfer.getData('application/reactflow-steptype') as StepType;

            if (!type || !reactFlowWrapper.current) return;

            const bounds = reactFlowWrapper.current.getBoundingClientRect();
            const position = {
                x: event.clientX - bounds.left - 75,
                y: event.clientY - bounds.top - 25,
            };

            const newNode: Node<BpmnNodeData> = {
                id: getNextId(),
                type,
                position,
                data: {
                    label: `New ${type}`,
                    stepType,
                    ...(stepType === 'USER_TASK' && { assignee: 'admin' }),
                    ...(stepType === 'SERVICE_TASK' && { delegateExpression: '${genericServiceDelegate}' }),
                    ...(stepType === 'EXCLUSIVE_GATEWAY' && { gatewayType: 'exclusive' as const }),
                },
            };

            setNodes((nds) => [...nds, newNode]);
            setSelectedNode(newNode);
        },
        [setNodes]
    );

    const onDragOver = useCallback((event: React.DragEvent) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
    }, []);

    // Update selected node properties
    const updateNodeData = useCallback(
        (key: keyof BpmnNodeData, value: unknown) => {
            if (!selectedNode) return;

            setNodes((nds) =>
                nds.map((node) =>
                    node.id === selectedNode.id
                        ? { ...node, data: { ...node.data, [key]: value } }
                        : node
                )
            );
            setSelectedNode((prev) =>
                prev ? { ...prev, data: { ...prev.data, [key]: value } } : null
            );
        },
        [selectedNode, setNodes]
    );

    // Delete selected node
    const deleteSelectedNode = useCallback(() => {
        if (!selectedNode) return;
        setNodes((nds) => nds.filter((n) => n.id !== selectedNode.id));
        setEdges((eds) => eds.filter((e) => e.source !== selectedNode.id && e.target !== selectedNode.id));
        setSelectedNode(null);
    }, [selectedNode, setNodes, setEdges]);

    // Convert React Flow nodes/edges to StepDefinition[]
    const convertToSteps = useCallback((): StepDefinition[] => {
        return nodes
            .filter((n) => n.type !== 'startNode' && n.type !== 'endNode')
            .map((node) => {
                const outgoingEdges = edges.filter((e) => e.source === node.id);
                const nextSteps = outgoingEdges.map((e) => {
                    // Map to end node or actual step
                    const targetNode = nodes.find((n) => n.id === e.target);
                    if (targetNode?.type === 'endNode') return ''; // Will be filtered
                    return e.target;
                }).filter(Boolean);

                return {
                    id: node.id,
                    name: node.data.label,
                    type: node.data.stepType,
                    assignee: node.data.assignee,
                    delegateExpression: node.data.delegateExpression,
                    conditionExpression: node.data.conditionExpression,
                    nextSteps,
                    formKey: node.data.formKey,
                    variables: node.data.variables,
                };
            });
    }, [nodes, edges]);

    // Deploy workflow
    const handleDeploy = async () => {
        setLoading(true);
        setError(null);
        try {
            const steps = convertToSteps();
            const request: WorkflowDefinitionRequest = {
                processKey,
                processName,
                description,
                historyTimeToLive: 180,
                steps,
            };
            const response = await deployWorkflow(request);
            setResult(response);
            onDeploySuccess?.();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Deployment failed');
        } finally {
            setLoading(false);
        }
    };

    // Preview XML
    const handlePreview = async () => {
        setLoading(true);
        setError(null);
        try {
            const steps = convertToSteps();
            const request: WorkflowDefinitionRequest = {
                processKey,
                processName,
                description,
                historyTimeToLive: 180,
                steps,
            };
            const response = await previewWorkflow(request);
            setResult(response);
            setShowXml(true);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Preview failed');
        } finally {
            setLoading(false);
        }
    };

    // Apply template - load full workflow with nodes and edges
    const applyTemplate = useCallback((templateKey: string) => {
        const template = WORKFLOW_TEMPLATES[templateKey as keyof typeof WORKFLOW_TEMPLATES];
        if (!template) return;

        setProcessKey(template.processKey);
        setProcessName(template.processName);
        setDescription(template.description);

        // Convert template steps to nodes and edges
        const { nodes: newNodes, edges: newEdges } = convertTemplateToNodes(template);
        setNodes(newNodes);
        setEdges(newEdges);
        setSelectedNode(null);
        nodeId = newNodes.length;
    }, [setNodes, setEdges]);

    // Clear canvas
    const clearCanvas = useCallback(() => {
        setNodes(initialNodes);
        setEdges([]);
        setSelectedNode(null);
        nodeId = 0;
    }, [setNodes, setEdges]);

    // Export workflow to JSON
    const exportWorkflow = useCallback(() => {
        const workflow = {
            processKey,
            processName,
            description,
            nodes: nodes.map(n => ({
                id: n.id,
                type: n.type,
                position: n.position,
                data: n.data
            })),
            edges: edges.map(e => ({
                id: e.id,
                source: e.source,
                target: e.target
            }))
        };
        const blob = new Blob([JSON.stringify(workflow, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${processKey || 'workflow'}.json`;
        a.click();
        URL.revokeObjectURL(url);
    }, [nodes, edges, processKey, processName, description]);

    // Import workflow from JSON
    const fileInputRef = useRef<HTMLInputElement>(null);
    const importWorkflow = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = (e) => {
            try {
                const workflow = JSON.parse(e.target?.result as string);
                if (workflow.processKey) setProcessKey(workflow.processKey);
                if (workflow.processName) setProcessName(workflow.processName);
                if (workflow.description) setDescription(workflow.description);
                if (workflow.nodes) {
                    setNodes(workflow.nodes);
                    nodeId = workflow.nodes.length;
                }
                if (workflow.edges) setEdges(workflow.edges);
            } catch {
                setError('Invalid workflow JSON file');
            }
        };
        reader.readAsText(file);
        event.target.value = '';
    }, [setNodes, setEdges]);

    return (
        <div className="visual-workflow-builder">
            {/* Sidebar */}
            <div className="builder-sidebar">
                <div className="sidebar-section">
                    <h3>📦 BPMN Elements</h3>
                    <div className="node-palette">
                        {NODE_TEMPLATES.map((template) => (
                            <div
                                key={template.type}
                                className="palette-item"
                                draggable
                                onDragStart={(e) => onDragStart(e, template.type, template.stepType)}
                            >
                                <span className="palette-icon">{template.icon}</span>
                                <span className="palette-label">{template.label}</span>
                            </div>
                        ))}
                    </div>
                </div>

                <div className="sidebar-section">
                    <h3>🚀 Quick Start</h3>
                    <div className="template-buttons">
                        {Object.entries(WORKFLOW_TEMPLATES).map(([key, template]) => (
                            <button
                                key={key}
                                className="template-btn"
                                onClick={() => applyTemplate(key)}
                            >
                                {template.name}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="sidebar-section">
                    <h3>📋 Workflow Info</h3>
                    <div className="form-field">
                        <label>Process Key:</label>
                        <input
                            type="text"
                            value={processKey}
                            onChange={(e) => setProcessKey(e.target.value)}
                            placeholder="my-workflow"
                        />
                    </div>
                    <div className="form-field">
                        <label>Process Name:</label>
                        <input
                            type="text"
                            value={processName}
                            onChange={(e) => setProcessName(e.target.value)}
                            placeholder="My Workflow"
                        />
                    </div>
                    <div className="form-field">
                        <label>Description:</label>
                        <textarea
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            placeholder="Optional description..."
                            rows={2}
                        />
                    </div>
                </div>
            </div>

            {/* Canvas */}
            <div className="builder-canvas" ref={reactFlowWrapper}>
                <ReactFlow
                    nodes={nodes}
                    edges={edges}
                    onNodesChange={onNodesChange}
                    onEdgesChange={onEdgesChange}
                    onConnect={onConnect}
                    onNodeClick={onNodeClick}
                    onPaneClick={onPaneClick}
                    onDrop={onDrop}
                    onDragOver={onDragOver}
                    nodeTypes={nodeTypes}
                    fitView
                    snapToGrid
                    snapGrid={[15, 15]}
                    defaultEdgeOptions={{
                        type: 'smoothstep',
                        animated: true,
                        markerEnd: { type: MarkerType.ArrowClosed },
                    }}
                >
                    <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="#334155" />
                    <Controls position="bottom-left" />
                    <MiniMap
                        nodeColor={(node) => {
                            switch (node.type) {
                                case 'startNode':
                                    return '#22c55e';
                                case 'endNode':
                                    return '#ef4444';
                                case 'userTask':
                                    return '#3b82f6';
                                case 'serviceTask':
                                    return '#f59e0b';
                                case 'gateway':
                                    return '#a855f7';
                                default:
                                    return '#64748b';
                            }
                        }}
                        style={{ background: '#0f172a' }}
                    />
                    <Panel position="top-right" className="canvas-actions">
                        <button onClick={exportWorkflow} className="action-btn export" title="Export to JSON">
                            📥 Export
                        </button>
                        <button onClick={() => fileInputRef.current?.click()} className="action-btn import" title="Import from JSON">
                            📤 Import
                        </button>
                        <input
                            type="file"
                            ref={fileInputRef}
                            onChange={importWorkflow}
                            accept=".json"
                            style={{ display: 'none' }}
                        />
                        <button onClick={handlePreview} disabled={loading} className="action-btn preview">
                            👁️ Preview
                        </button>
                        <button onClick={handleDeploy} disabled={loading} className="action-btn deploy">
                            🚀 Deploy
                        </button>
                        <button onClick={clearCanvas} className="action-btn clear">
                            🗑️ Clear
                        </button>
                    </Panel>
                </ReactFlow>
            </div>

            {/* Properties Panel */}
            {selectedNode && (
                <div className="properties-panel">
                    <div className="panel-header">
                        <h3>🔧 Properties</h3>
                        <button className="close-btn" onClick={() => setSelectedNode(null)}>×</button>
                    </div>

                    <div className="panel-content">
                        <div className="form-field">
                            <label>Label:</label>
                            <input
                                type="text"
                                value={selectedNode.data.label}
                                onChange={(e) => updateNodeData('label', e.target.value)}
                            />
                        </div>

                        {selectedNode.data.stepType === 'USER_TASK' && (
                            <>
                                <div className="form-field">
                                    <label>Assignee:</label>
                                    <input
                                        type="text"
                                        value={selectedNode.data.assignee || ''}
                                        onChange={(e) => updateNodeData('assignee', e.target.value)}
                                        placeholder="admin"
                                    />
                                </div>
                                <div className="form-field">
                                    <label>Form Key:</label>
                                    <input
                                        type="text"
                                        value={selectedNode.data.formKey || ''}
                                        onChange={(e) => updateNodeData('formKey', e.target.value)}
                                        placeholder="embedded:app:forms/myform.html"
                                    />
                                </div>
                            </>
                        )}

                        {selectedNode.data.stepType === 'SERVICE_TASK' && (
                            <>
                                <div className="form-field">
                                    <label>Delegate:</label>
                                    <select
                                        value={selectedNode.data.delegateExpression?.replace(/\$\{|\}/g, '') || ''}
                                        onChange={(e) => {
                                            const delegateName = e.target.value;
                                            updateNodeData('delegateExpression', delegateName ? `\${${delegateName}}` : '');
                                            // Reset action when delegate changes
                                            updateNodeData('variables', { ...selectedNode.data.variables, action: '' });
                                        }}
                                    >
                                        <option value="">-- Select Delegate --</option>
                                        {delegates.map((d) => (
                                            <option key={d.name} value={d.name}>
                                                {d.displayName}
                                            </option>
                                        ))}
                                    </select>
                                </div>
                                {(() => {
                                    const delegateName = selectedNode.data.delegateExpression?.replace(/\$\{|\}/g, '') || '';
                                    const delegate = delegates.find((d) => d.name === delegateName);
                                    if (!delegate || delegate.actions.length === 0) return null;
                                    const currentAction = (selectedNode.data.variables as Record<string, unknown>)?.action as string || '';
                                    return (
                                        <div className="form-field">
                                            <label>Action:</label>
                                            <select
                                                value={currentAction}
                                                onChange={(e) => {
                                                    updateNodeData('variables', {
                                                        ...selectedNode.data.variables,
                                                        action: e.target.value,
                                                    });
                                                }}
                                            >
                                                <option value="">-- Select Action --</option>
                                                {delegate.actions.map((a) => (
                                                    <option key={a.name} value={a.name}>
                                                        {a.displayName}
                                                    </option>
                                                ))}
                                            </select>
                                            {delegate.actions.find((a) => a.name === currentAction)?.description && (
                                                <small className="action-desc">
                                                    {delegate.actions.find((a) => a.name === currentAction)?.description}
                                                </small>
                                            )}
                                        </div>
                                    );
                                })()}
                            </>
                        )}

                        {(selectedNode.data.stepType === 'EXCLUSIVE_GATEWAY' ||
                            selectedNode.data.stepType === 'PARALLEL_GATEWAY') && (
                                <div className="form-field">
                                    <label>Gateway Type:</label>
                                    <select
                                        value={selectedNode.data.gatewayType || 'exclusive'}
                                        onChange={(e) => updateNodeData('gatewayType', e.target.value)}
                                    >
                                        <option value="exclusive">Exclusive (XOR)</option>
                                        <option value="parallel">Parallel (AND)</option>
                                    </select>
                                </div>
                            )}

                        <div className="panel-actions">
                            <button className="delete-btn" onClick={deleteSelectedNode}>
                                🗑️ Delete Node
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Status Messages */}
            {loading && <div className="status-overlay">⏳ Processing...</div>}
            {error && <div className="error-toast">❌ {error}</div>}

            {/* XML Preview Modal */}
            {result && showXml && result.bpmnXml && (
                <div className="xml-modal" onClick={() => setShowXml(false)}>
                    <div className="xml-content" onClick={(e) => e.stopPropagation()}>
                        <div className="xml-header">
                            <h3>✅ {result.message}</h3>
                            <button onClick={() => setShowXml(false)}>×</button>
                        </div>
                        <pre>{result.bpmnXml}</pre>
                    </div>
                </div>
            )}
        </div>
    );
}
