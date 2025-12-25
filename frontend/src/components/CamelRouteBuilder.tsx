import { useState, useCallback, useRef, useEffect } from 'react';
import {
    ReactFlow,
    Background,
    Controls,
    useNodesState,
    useEdgesState,
    addEdge,
    MiniMap,
    BackgroundVariant,
    Panel,
    MarkerType,
    applyNodeChanges,
    applyEdgeChanges,
    type Connection,
    type Edge,
    type Node,
    type NodeChange,
    type EdgeChange,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { camelNodeTypes, type CamelNodeData } from './nodes/CamelNodes';
import './CamelRouteBuilder.css';
import { API_BASE } from '../api';

// Node templates for palette
const NODE_TEMPLATES = [
    { type: 'fromNode', label: 'From', icon: '📥', nodeType: 'from' as const },
    { type: 'toNode', label: 'To', icon: '📤', nodeType: 'to' as const },
    { type: 'logNode', label: 'Log', icon: '📝', nodeType: 'log' as const },
    { type: 'setBodyNode', label: 'Set Body', icon: '✏️', nodeType: 'setBody' as const },
    { type: 'transformNode', label: 'Transform', icon: '🔄', nodeType: 'transform' as const },
    { type: 'filterNode', label: 'Filter', icon: '🔍', nodeType: 'filter' as const },
    { type: 'choiceNode', label: 'Choice', icon: '❓', nodeType: 'choice' as const },
    { type: 'delayNode', label: 'Delay', icon: '⏱️', nodeType: 'delay' as const },
    { type: 'splitNode', label: 'Split', icon: '✂️', nodeType: 'split' as const },
    { type: 'aggregateNode', label: 'Aggregate', icon: '🔗', nodeType: 'aggregate' as const },
    { type: 'multicastNode', label: 'Multicast', icon: '📡', nodeType: 'multicast' as const },
    { type: 'enrichNode', label: 'Enrich', icon: '➕', nodeType: 'enrich' as const },
    { type: 'trycatchNode', label: 'Try-Catch', icon: '🛡️', nodeType: 'trycatch' as const },
    { type: 'loopNode', label: 'Loop', icon: '🔁', nodeType: 'loop' as const },
    { type: 'throttleNode', label: 'Throttle', icon: '🚦', nodeType: 'throttle' as const },
    { type: 'wiretapNode', label: 'Wire Tap', icon: '📋', nodeType: 'wiretap' as const },
    // Saga nodes
    { type: 'debitNode', label: 'Debit', icon: '💸', nodeType: 'debit' as const },
    { type: 'creditNode', label: 'Credit', icon: '💰', nodeType: 'credit' as const },
    { type: 'sagatransferNode', label: 'Saga Transfer', icon: '🔄', nodeType: 'sagatransfer' as const },
    { type: 'compensateNode', label: 'Compensate', icon: '↩️', nodeType: 'compensate' as const },
];

// Sample Route Templates
export interface RouteTemplate {
    id: string;
    name: string;
    icon: string;
    description: string;
    nodes: Node<CamelNodeData>[];
    edges: Edge[];
    testData?: Record<string, any> | string;
}

// Simple Toast Component
const Toast = ({ message, type, onClose }: { message: string, type: 'success' | 'error' | 'info', onClose: () => void }) => (
    <div style={{
        position: 'fixed',
        bottom: '20px',
        right: '20px',
        backgroundColor: type === 'success' ? '#4caf50' : type === 'error' ? '#f44336' : '#2196f3',
        color: 'white',
        padding: '12px 24px',
        borderRadius: '4px',
        boxShadow: '0 2px 5px rgba(0,0,0,0.2)',
        zIndex: 9999,
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
        animation: 'slideIn 0.3s ease-out'
    }}>
        <span>{message}</span>
        <button onClick={onClose} style={{ background: 'transparent', border: 'none', color: 'white', cursor: 'pointer', fontSize: '16px' }}>&times;</button>
    </div>
);

// Initial nodes
const initialNodes: Node<CamelNodeData>[] = [
    {
        id: 'from-1',
        type: 'fromNode',
        position: { x: 50, y: 200 },
        data: { label: 'Source', nodeType: 'from', uri: 'direct:myRoute' },
    },
];

const initialEdges: Edge[] = [];

let nodeId = 1;
const getNodeId = () => `node-${++nodeId}`;

const ROUTE_TEMPLATES: RouteTemplate[] = [
    {
        id: 'simple-log',
        name: 'Simple Logger',
        icon: '📝',
        description: 'Log incoming messages',
        nodes: [
            { id: 'from-1', type: 'fromNode', position: { x: 50, y: 200 }, data: { label: 'Input', nodeType: 'from', uri: 'direct:dynamic-log' } },
            { id: 'log-1', type: 'logNode', position: { x: 250, y: 200 }, data: { label: 'Log Message', nodeType: 'log', message: '📨 Received: ${body}' } },
            { id: 'to-1', type: 'toNode', position: { x: 450, y: 200 }, data: { label: 'Output', nodeType: 'to', uri: 'log:output' } },
        ],
        edges: [
            { id: 'e1', source: 'from-1', target: 'log-1' },
            { id: 'e2', source: 'log-1', target: 'to-1' },
        ],
        testData: { message: "Hello Camel" },
    },
    {
        id: 'transform-json',
        name: 'JSON Transform',
        icon: '🔄',
        description: 'Transform and enrich JSON data',
        nodes: [
            { id: 'from-1', type: 'fromNode', position: { x: 50, y: 200 }, data: { label: 'Input', nodeType: 'from', uri: 'direct:dynamic-transform' } },
            { id: 'log-1', type: 'logNode', position: { x: 200, y: 200 }, data: { label: 'Log Input', nodeType: 'log', message: 'Input: ${body}' } },
            { id: 'transform-1', type: 'transformNode', position: { x: 350, y: 200 }, data: { label: 'Transform', nodeType: 'transform', expression: '{"result": ${body}, "timestamp": "${date:now:yyyy-MM-dd}"}' } },
            { id: 'log-2', type: 'logNode', position: { x: 530, y: 200 }, data: { label: 'Log Output', nodeType: 'log', message: 'Output: ${body}' } },
        ],
        edges: [
            { id: 'e1', source: 'from-1', target: 'log-1' },
            { id: 'e2', source: 'log-1', target: 'transform-1' },
            { id: 'e3', source: 'transform-1', target: 'log-2' },
        ],
        testData: { "name": "Camunda User", "role": "Developer" },
    },
    {
        id: 'filter-route',
        name: 'Filter Messages',
        icon: '🔍',
        description: 'Filter messages based on content',
        nodes: [
            { id: 'from-1', type: 'fromNode', position: { x: 50, y: 200 }, data: { label: 'Input', nodeType: 'from', uri: 'direct:dynamic-filter' } },
            { id: 'filter-1', type: 'filterNode', position: { x: 220, y: 200 }, data: { label: 'Filter Valid', nodeType: 'filter', expression: '${body} != null && ${body} != ""' } },
            { id: 'log-1', type: 'logNode', position: { x: 400, y: 200 }, data: { label: 'Log Valid', nodeType: 'log', message: '✅ Valid message: ${body}' } },
            { id: 'to-1', type: 'toNode', position: { x: 580, y: 200 }, data: { label: 'Output', nodeType: 'to', uri: 'log:filtered' } },
        ],
        edges: [
            { id: 'e1', source: 'from-1', target: 'filter-1' },
            { id: 'e2', source: 'filter-1', target: 'log-1' },
            { id: 'e3', source: 'log-1', target: 'to-1' },
        ],
        testData: "This is a valid message",
    },
    {
        id: 'http-call',
        name: 'HTTP API Call',
        icon: '🌐',
        description: 'Call external HTTP API',
        nodes: [
            { id: 'from-1', type: 'fromNode', position: { x: 50, y: 200 }, data: { label: 'Trigger', nodeType: 'from', uri: 'direct:dynamic-http' } },
            { id: 'log-1', type: 'logNode', position: { x: 200, y: 200 }, data: { label: 'Log Request', nodeType: 'log', message: '🚀 Calling API...' } },
            { id: 'setbody-1', type: 'setBodyNode', position: { x: 350, y: 200 }, data: { label: 'Set Request', nodeType: 'setBody', expression: '{"query": "test"}', expressionLanguage: 'constant' } },
            { id: 'to-1', type: 'toNode', position: { x: 530, y: 200 }, data: { label: 'API Call', nodeType: 'to', uri: 'https://httpbin.org/post' } },
            { id: 'log-2', type: 'logNode', position: { x: 700, y: 200 }, data: { label: 'Log Response', nodeType: 'log', message: '📥 Response: ${body}' } },
        ],
        edges: [
            { id: 'e1', source: 'from-1', target: 'log-1' },
            { id: 'e2', source: 'log-1', target: 'setbody-1' },
            { id: 'e3', source: 'setbody-1', target: 'to-1' },
            { id: 'e4', source: 'to-1', target: 'log-2' },
        ],
        testData: {},
    },
    {
        id: 'saga-transfer',
        name: 'Saga Money Transfer',
        icon: '💸',
        description: 'Saga pattern with rollback for money transfer',
        nodes: [
            // Source
            { id: 'from-1', type: 'fromNode', position: { x: 30, y: 180 }, data: { label: 'Transfer Request', nodeType: 'from', uri: 'direct:saga-dynamic' } },

            // Validate
            { id: 'log-validate', type: 'logNode', position: { x: 180, y: 180 }, data: { label: 'Validate', nodeType: 'log', message: '📋 Validating: ${header.amount} from ${header.source} to ${header.dest}' } },
            { id: 'filter-1', type: 'filterNode', position: { x: 330, y: 180 }, data: { label: 'Amount > 0', nodeType: 'filter', expression: '${header.amount} > 0' } },

            // Try-Catch wrapper
            { id: 'trycatch-1', type: 'trycatchNode', position: { x: 480, y: 180 }, data: { label: 'Saga Transaction', nodeType: 'trycatch' } },

            // Debit (Try path)
            { id: 'log-debit', type: 'logNode', position: { x: 630, y: 120 }, data: { label: 'Debit Source', nodeType: 'log', message: '💳 Debiting ${header.amount} from ${header.source}' } },
            { id: 'setbody-debit', type: 'setBodyNode', position: { x: 780, y: 120 }, data: { label: 'Debit Result', nodeType: 'setBody', expression: '{"step": "DEBIT", "account": "${header.source}", "amount": ${header.amount}}', expressionLanguage: 'simple' } },

            // Credit
            { id: 'log-credit', type: 'logNode', position: { x: 930, y: 120 }, data: { label: 'Credit Dest', nodeType: 'log', message: '💵 Crediting ${header.amount} to ${header.dest}' } },
            { id: 'transform-1', type: 'transformNode', position: { x: 1080, y: 120 }, data: { label: 'Success', nodeType: 'transform', expression: '{"status": "SUCCESS", "txId": "${date:now:yyyyMMddHHmmss}", "amount": ${header.amount}}' } },

            // Success output
            { id: 'log-success', type: 'logNode', position: { x: 1230, y: 120 }, data: { label: 'Complete', nodeType: 'log', message: '✅ Transfer complete: ${body}' } },

            // Catch path - Rollback
            { id: 'log-error', type: 'logNode', position: { x: 630, y: 280 }, data: { label: 'Error Caught', nodeType: 'log', message: '❌ Error: Rolling back...' } },
            { id: 'setbody-rollback', type: 'setBodyNode', position: { x: 780, y: 280 }, data: { label: 'Rollback Debit', nodeType: 'setBody', expression: '{"step": "ROLLBACK", "account": "${header.source}", "amount": ${header.amount}}', expressionLanguage: 'simple' } },
            { id: 'log-rollback', type: 'logNode', position: { x: 930, y: 280 }, data: { label: 'Rollback Done', nodeType: 'log', message: '🔄 Rolled back ${header.amount} to ${header.source}' } },
            { id: 'transform-fail', type: 'transformNode', position: { x: 1080, y: 280 }, data: { label: 'Failed', nodeType: 'transform', expression: '{"status": "FAILED", "rollback": true, "message": "Transaction rolled back"}' } },
        ],
        edges: [
            // Main flow
            { id: 'e1', source: 'from-1', target: 'log-validate' },
            { id: 'e2', source: 'log-validate', target: 'filter-1' },
            { id: 'e3', source: 'filter-1', target: 'trycatch-1' },

            // Try path (success)
            { id: 'e4', source: 'trycatch-1', target: 'log-debit', sourceHandle: 'try' },
            { id: 'e5', source: 'log-debit', target: 'setbody-debit' },
            { id: 'e6', source: 'setbody-debit', target: 'log-credit' },
            { id: 'e7', source: 'log-credit', target: 'transform-1' },
            { id: 'e8', source: 'transform-1', target: 'log-success' },

            // Catch path (rollback)
            { id: 'e9', source: 'trycatch-1', target: 'log-error', sourceHandle: 'catch' },
            { id: 'e10', source: 'log-error', target: 'setbody-rollback' },
            { id: 'e11', source: 'setbody-rollback', target: 'log-rollback' },
            { id: 'e12', source: 'log-rollback', target: 'transform-fail' },
        ],
        testData: {
            "source": "1001111",
            "dest": "2002222",
            "amount": 500
        },
    },
    {
        id: 'split-aggregate',
        name: 'Split & Process',
        icon: '✂️',
        description: 'Split message, process each, then aggregate',
        nodes: [
            { id: 'from-1', type: 'fromNode', position: { x: 50, y: 200 }, data: { label: 'Input List', nodeType: 'from', uri: 'direct:split-demo' } },
            { id: 'log-1', type: 'logNode', position: { x: 200, y: 200 }, data: { label: 'Log Input', nodeType: 'log', message: '📋 Processing list: ${body}' } },
            { id: 'split-1', type: 'splitNode', position: { x: 350, y: 200 }, data: { label: 'Split Items', nodeType: 'split', expression: '${body}' } },
            { id: 'log-2', type: 'logNode', position: { x: 500, y: 200 }, data: { label: 'Process Item', nodeType: 'log', message: '🔄 Item: ${body}' } },
            { id: 'transform-1', type: 'transformNode', position: { x: 650, y: 200 }, data: { label: 'Transform', nodeType: 'transform', expression: '{"processed": "${body}", "time": "${date:now}"}' } },
            { id: 'aggregate-1', type: 'aggregateNode', position: { x: 800, y: 200 }, data: { label: 'Aggregate', nodeType: 'aggregate', expression: '${header.CamelSplitComplete}' } },
            { id: 'log-3', type: 'logNode', position: { x: 950, y: 200 }, data: { label: 'Done', nodeType: 'log', message: '✅ All processed' } },
        ],
        edges: [
            { id: 'e1', source: 'from-1', target: 'log-1' },
            { id: 'e2', source: 'log-1', target: 'split-1' },
            { id: 'e3', source: 'split-1', target: 'log-2' },
            { id: 'e4', source: 'log-2', target: 'transform-1' },
            { id: 'e5', source: 'transform-1', target: 'aggregate-1' },
            { id: 'e6', source: 'aggregate-1', target: 'log-3' },
        ],
        testData: "A,B,C,D,E",
    },
    {
        id: 'real-saga-transfer',
        name: 'Real Saga Transfer',
        icon: '💵',
        description: 'Actual saga transfer with database operations',
        nodes: [
            { id: 'from-1', type: 'fromNode', position: { x: 50, y: 200 }, data: { label: 'Transfer Request', nodeType: 'from', uri: 'direct:real-saga' } },
            { id: 'log-1', type: 'logNode', position: { x: 220, y: 200 }, data: { label: 'Log Request', nodeType: 'log', message: '💵 Transfer: ${header.sourceAccount} → ${header.destAccount} (${header.amount})' } },
            { id: 'saga-1', type: 'sagatransferNode', position: { x: 400, y: 200 }, data: { label: 'Execute Transfer', nodeType: 'sagatransfer', sourceAccount: '1001234567', destAccount: '1009876543', amount: '1000000' } },
            { id: 'log-2', type: 'logNode', position: { x: 600, y: 200 }, data: { label: 'Transfer Done', nodeType: 'log', message: '✅ Transfer completed! TxnId: ${header.transactionId}' } },
            { id: 'to-1', type: 'toNode', position: { x: 780, y: 200 }, data: { label: 'Output', nodeType: 'to', uri: 'log:saga-result' } },
        ],
        edges: [
            { id: 'e1', source: 'from-1', target: 'log-1' },
            { id: 'e2', source: 'log-1', target: 'saga-1' },
            { id: 'e3', source: 'saga-1', target: 'log-2' },
            { id: 'e4', source: 'log-2', target: 'to-1' },
        ],
        testData: {
            "sourceAccount": "1001234567",
            "destAccount": "1009876543",
            "amount": 1000000,
            "transactionId": "TX-999-TEST"
        }
    },
    {
        id: 'debit-credit-manual',
        name: 'Manual Debit/Credit',
        icon: '🔀',
        description: 'Step-by-step debit then credit with compensation',
        nodes: [
            { id: 'from-1', type: 'fromNode', position: { x: 50, y: 180 }, data: { label: 'Start', nodeType: 'from', uri: 'direct:manual-saga' } },
            { id: 'debit-1', type: 'debitNode', position: { x: 200, y: 180 }, data: { label: 'Debit Source', nodeType: 'debit', accountNumber: '1001234567' } },
            { id: 'log-1', type: 'logNode', position: { x: 350, y: 180 }, data: { label: 'Debit OK', nodeType: 'log', message: '💸 Debited ${header.amount} from source' } },
            { id: 'credit-1', type: 'creditNode', position: { x: 500, y: 180 }, data: { label: 'Credit Dest', nodeType: 'credit', accountNumber: '1009876543' } },
            { id: 'log-2', type: 'logNode', position: { x: 650, y: 180 }, data: { label: 'Credit OK', nodeType: 'log', message: '💰 Credited ${header.amount} to destination' } },
            { id: 'to-1', type: 'toNode', position: { x: 800, y: 180 }, data: { label: 'Done', nodeType: 'to', uri: 'log:manual-result' } },
            // Compensation path
            { id: 'compensate-1', type: 'compensateNode', position: { x: 500, y: 300 }, data: { label: 'Rollback Debit', nodeType: 'compensate' } },
            { id: 'log-error', type: 'logNode', position: { x: 650, y: 300 }, data: { label: 'Rollback Done', nodeType: 'log', message: '↩️ Rolled back debit due to error' } },
        ],
        edges: [
            { id: 'e1', source: 'from-1', target: 'debit-1' },
            { id: 'e2', source: 'debit-1', target: 'log-1' },
            { id: 'e3', source: 'log-1', target: 'credit-1' },
            { id: 'e4', source: 'credit-1', target: 'log-2' },
            { id: 'e5', source: 'log-2', target: 'to-1' },
            { id: 'e-err1', source: 'credit-1', target: 'compensate-1' },
            { id: 'e-err2', source: 'compensate-1', target: 'log-error' },
        ],
        testData: {
            "headers": {
                "amount": 5000
            }
        }
    },
];

interface DeployedRoute {
    id: string;
    name: string;
    status: string;
}

export default function CamelRouteBuilder() {
    const reactFlowWrapper = useRef<HTMLDivElement>(null);
    const [nodes, setNodes, onNodesChange] = useNodesState<Node<CamelNodeData>>(initialNodes);
    const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
    const [selectedNode, setSelectedNode] = useState<Node<CamelNodeData> | null>(null);

    // Route metadata
    const [routeId, setRouteId] = useState('my-route');
    const [routeName, setRouteName] = useState('My Dynamic Route');
    const [routeDescription, setRouteDescription] = useState('My dynamic Camel route');
    const [testPayload, setTestPayload] = useState('{}');

    // UI state
    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState<{ type: 'success' | 'error' | 'info', text: string } | null>(null);
    const [deployedRoutes, setDeployedRoutes] = useState<DeployedRoute[]>([]);

    // SSE State - store events for event log panel
    const [taskEvents, setTaskEvents] = useState<any[]>([]);
    const [showEventLog, setShowEventLog] = useState(true);

    useEffect(() => {
        // Connect to SSE stream
        const eventSource = new EventSource(`${API_BASE}/api/notifications/stream`);

        eventSource.onopen = () => {
            console.log("SSE Connected");
        };

        eventSource.addEventListener("task-event", (event: MessageEvent) => {
            try {
                const data = JSON.parse(event.data);
                console.log("SSE Event:", data);

                // Add to events list (keep last 50)
                setTaskEvents(prev => [
                    { ...data, id: Date.now() + Math.random() },
                    ...prev.slice(0, 49)
                ]);

                // Show toast with more details
                const durationInfo = data.durationMs ? ` (${data.durationMs}ms)` : '';
                const nodeInfo = data.nodeType ? `[${data.nodeType}] ` : '';
                setMessage({
                    type: data.status === 'FAILED' ? 'error' : 'success',
                    text: `${nodeInfo}${data.message}${durationInfo}`
                });

                // Clear after 4s
                setTimeout(() => setMessage(null), 4000);

            } catch (e) {
                console.error("Failed to parse SSE event", e);
            }
        });

        eventSource.onerror = (e) => {
            console.error("SSE Error", e);
            eventSource.close();
        };

        return () => {
            eventSource.close();
        };
    }, []);

    // Load deployed routes on mount
    useEffect(() => {
        loadDeployedRoutes();
    }, []);

    // Clear message after 3s
    useEffect(() => {
        if (message) {
            const timer = setTimeout(() => setMessage(null), 3000);
            return () => clearTimeout(timer);
        }
    }, [message]);

    const loadDeployedRoutes = async () => {
        try {
            const res = await fetch(`${API_BASE}/api/camel-routes`);
            if (res.ok) {
                const data = await res.json();
                setDeployedRoutes(data);
            }
        } catch (e) {
            console.error('Failed to load routes', e);
        }
    };

    // Handle connections
    const onConnect = useCallback(
        (params: Connection) => {
            setEdges((eds) =>
                addEdge(
                    {
                        ...params,
                        markerEnd: { type: MarkerType.ArrowClosed },
                        style: { strokeWidth: 2 },
                    },
                    eds
                )
            );
        },
        [setEdges]
    );

    // Handle node selection
    const onNodeClick = useCallback((_: React.MouseEvent, node: Node<CamelNodeData>) => {
        setSelectedNode(node);
    }, []);

    // Drag & Drop from palette
    const onDragStart = (event: React.DragEvent, template: typeof NODE_TEMPLATES[0]) => {
        event.dataTransfer.setData('application/reactflow', JSON.stringify(template));
        event.dataTransfer.effectAllowed = 'move';
    };

    const onDragOver = useCallback((event: React.DragEvent) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
    }, []);

    const onDrop = useCallback(
        (event: React.DragEvent) => {
            event.preventDefault();

            const data = event.dataTransfer.getData('application/reactflow');
            if (!data) return;

            const template = JSON.parse(data);
            const bounds = reactFlowWrapper.current?.getBoundingClientRect();
            if (!bounds) return;

            const position = {
                x: event.clientX - bounds.left - 75,
                y: event.clientY - bounds.top - 30,
            };

            const newNode: Node<CamelNodeData> = {
                id: getNodeId(),
                type: template.type,
                position,
                data: {
                    label: template.label,
                    nodeType: template.nodeType,
                    uri: template.nodeType === 'from' ? 'direct:newRoute' :
                        template.nodeType === 'to' ? 'log:output' : undefined,
                    message: template.nodeType === 'log' ? 'Processing: ${body}' : undefined,
                    expression: ['setBody', 'transform', 'filter'].includes(template.nodeType)
                        ? '${body}' : undefined,
                },
            };

            setNodes((nds) => [...nds, newNode]);
        },
        [setNodes]
    );

    // Update node data
    const updateNodeData = (nodeId: string, updates: Partial<CamelNodeData>) => {
        setNodes((nds) =>
            nds.map((n) =>
                n.id === nodeId ? { ...n, data: { ...n.data, ...updates } } : n
            )
        );
        if (selectedNode?.id === nodeId) {
            setSelectedNode((prev) => prev ? { ...prev, data: { ...prev.data, ...updates } } : null);
        }
    };

    // Delete selected node
    const deleteSelectedNode = () => {
        if (!selectedNode) return;
        setNodes((nds) => nds.filter((n) => n.id !== selectedNode.id));
        setEdges((eds) => eds.filter((e) => e.source !== selectedNode.id && e.target !== selectedNode.id));
        setSelectedNode(null);
    };

    // Handle node deletion from keyboard (Backspace/Delete)
    const onNodesDelete = useCallback(
        (deletedNodes: Node[]) => {
            if (selectedNode && deletedNodes.some((n) => n.id === selectedNode.id)) {
                setSelectedNode(null);
            }
        },
        [selectedNode]
    );

    // Convert to API format
    const convertToApiFormat = () => {
        return {
            id: routeId,
            name: routeName,
            description: routeDescription,
            nodes: nodes.map((n) => ({
                id: n.id,
                type: n.data.nodeType,
                uri: n.data.uri,
                message: n.data.message,
                expression: n.data.expression,
                expressionLanguage: n.data.expressionLanguage || 'simple',
                positionX: n.position.x,
                positionY: n.position.y,
            })),
            edges: edges.map((e) => ({
                id: e.id,
                source: e.source,
                target: e.target,
            })),
        };
    };

    // Deploy route
    const deployRoute = async () => {
        setLoading(true);
        try {
            const payload = convertToApiFormat();
            const res = await fetch(`${API_BASE}/api/camel-routes`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            const data = await res.json();
            if (data.success) {
                setMessage({ type: 'success', text: `Route "${routeName}" deployed!` });
                loadDeployedRoutes();
            } else {
                setMessage({ type: 'error', text: data.error || 'Deploy failed' });
            }
        } catch (e) {
            setMessage({ type: 'error', text: 'Deploy failed: ' + (e as Error).message });
        } finally {
            setLoading(false);
        }
    };

    // Test route
    const testRoute = async () => {
        setLoading(true);
        try {
            let bodyPayload = {};
            try {
                bodyPayload = JSON.parse(testPayload);
            } catch (e) {
                setMessage({ type: 'error', text: 'Invalid JSON in Test Data' });
                setLoading(false);
                return;
            }

            const res = await fetch(`${API_BASE}/api/camel-routes/${routeId}/test`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(bodyPayload),
            });
            const data = await res.json();
            if (data.success) {
                setMessage({ type: 'success', text: `Test passed! Output: ${JSON.stringify(data.output)}` });
            } else {
                setMessage({ type: 'error', text: data.error || 'Test failed' });
            }
        } catch (e) {
            setMessage({ type: 'error', text: 'Test failed: ' + (e as Error).message });
        } finally {
            setLoading(false);
        }
    };

    // Clear canvas
    const clearCanvas = () => {
        setNodes([initialNodes[0]]);
        setEdges([]);
        setSelectedNode(null);
    };

    // Load template
    const loadTemplate = (template: RouteTemplate) => {
        setNodes(template.nodes.map(n => ({ ...n })));
        setEdges(template.edges.map(e => ({ ...e, markerEnd: { type: MarkerType.ArrowClosed }, style: { strokeWidth: 2 } })));
        setRouteId(template.id);
        setRouteName(template.name);
        setSelectedNode(null);

        // Pre-fill test data
        if (template.testData) {
            setTestPayload(JSON.stringify(template.testData, null, 2));
        } else {
            setTestPayload('{}');
        }

        setMessage({ type: 'success', text: `Loaded template: ${template.name}` });
    };

    return (
        <div className="camel-route-builder">
            {/* Toast */}
            {message && (
                <Toast
                    message={message.text}
                    type={message.type}
                    onClose={() => setMessage(null)}
                />
            )}

            {/* Left Sidebar - Palette */}
            <div className="camel-sidebar">
                <h3>🧩 Components</h3>
                <div className="camel-palette">
                    {NODE_TEMPLATES.map((template) => (
                        <div
                            key={template.type}
                            className="camel-palette-item"
                            draggable
                            onDragStart={(e) => onDragStart(e, template)}
                        >
                            <span className="palette-icon">{template.icon}</span>
                            <span className="palette-label">{template.label}</span>
                        </div>
                    ))}
                </div>

                <h3>📄 Sample Routes</h3>
                <div className="camel-palette">
                    {ROUTE_TEMPLATES.map((template) => (
                        <div
                            key={template.id}
                            className="camel-palette-item sample-route"
                            onClick={() => loadTemplate(template)}
                            title={template.description}
                        >
                            <span className="palette-icon">{template.icon}</span>
                            <span className="palette-label">{template.name}</span>
                        </div>
                    ))}
                </div>

                <div className="route-metadata">
                    <h3>📋 Route Info</h3>
                    <label>Route ID</label>
                    <input value={routeId} onChange={(e) => setRouteId(e.target.value)} />
                    <label>Name</label>
                    <input value={routeName} onChange={(e) => setRouteName(e.target.value)} />

                    <label>Test Data (JSON)</label>
                    <textarea
                        className="test-payload-input"
                        value={testPayload}
                        onChange={(e) => setTestPayload(e.target.value)}
                        placeholder='{"key": "value"}'
                        rows={5}
                        style={{ width: '100%', fontSize: '0.8rem', fontFamily: 'monospace' }}
                    />
                </div>

                <div className="camel-actions">
                    <button className="btn-deploy" onClick={deployRoute} disabled={loading}>
                        {loading ? '...' : '🚀 Deploy'}
                    </button>
                    <button className="btn-test" onClick={testRoute} disabled={loading}>
                        ▶️ Test
                    </button>
                </div>

                {deployedRoutes.length > 0 && (
                    <div className="deployed-routes">
                        <h4>Deployed Routes</h4>
                        <div className="route-list">
                            {deployedRoutes.map((r) => (
                                <div key={r.id} className="route-item">
                                    <span className="route-name">{r.name || r.id}</span>
                                    <span className={`route-status ${r.status?.toLowerCase()}`}>
                                        {r.status}
                                    </span>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>

            {/* Center - Canvas */}
            <div className="camel-canvas" ref={reactFlowWrapper}>
                <ReactFlow
                    nodes={nodes}
                    edges={edges}
                    onNodesChange={onNodesChange}
                    onEdgesChange={onEdgesChange}
                    onConnect={onConnect}
                    onNodeClick={onNodeClick}
                    onNodesDelete={onNodesDelete}
                    onDrop={onDrop}
                    onDragOver={onDragOver}
                    nodeTypes={camelNodeTypes}
                    fitView
                    snapToGrid
                    snapGrid={[15, 15]}
                >
                    <Background variant={BackgroundVariant.Dots} gap={20} size={1} />
                    <Controls />
                    <MiniMap
                        nodeColor={(n) => {
                            const type = n.data?.nodeType;
                            if (type === 'from') return '#22c55e';
                            if (type === 'to') return '#ef4444';
                            if (type === 'log') return '#3b82f6';
                            return '#6366f1';
                        }}
                    />
                    <Panel position="top-right">
                        <button onClick={clearCanvas} className="btn-clear">
                            🗑️ Clear
                        </button>
                    </Panel>
                </ReactFlow>

                {/* Event Log Panel */}
                {showEventLog && taskEvents.length > 0 && (
                    <div style={{
                        position: 'absolute',
                        bottom: '10px',
                        right: '10px',
                        width: '350px',
                        maxHeight: '200px',
                        backgroundColor: 'rgba(30, 41, 59, 0.95)',
                        borderRadius: '8px',
                        border: '1px solid #334155',
                        overflow: 'hidden',
                        boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
                        zIndex: 10
                    }}>
                        <div style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            padding: '8px 12px',
                            borderBottom: '1px solid #334155',
                            backgroundColor: '#1e293b'
                        }}>
                            <span style={{ fontWeight: 'bold', fontSize: '0.85rem' }}>📋 Event Log</span>
                            <div>
                                <button
                                    onClick={() => setTaskEvents([])}
                                    style={{
                                        background: 'transparent',
                                        border: 'none',
                                        color: '#94a3b8',
                                        cursor: 'pointer',
                                        marginRight: '8px',
                                        fontSize: '0.75rem'
                                    }}
                                >
                                    Clear
                                </button>
                                <button
                                    onClick={() => setShowEventLog(false)}
                                    style={{
                                        background: 'transparent',
                                        border: 'none',
                                        color: '#94a3b8',
                                        cursor: 'pointer',
                                        fontSize: '1rem'
                                    }}
                                >
                                    ×
                                </button>
                            </div>
                        </div>
                        <div style={{
                            maxHeight: '150px',
                            overflowY: 'auto',
                            padding: '4px'
                        }}>
                            {taskEvents.map((evt) => (
                                <div key={evt.id} style={{
                                    display: 'flex',
                                    alignItems: 'flex-start',
                                    padding: '6px 8px',
                                    borderBottom: '1px solid #1e293b',
                                    fontSize: '0.75rem',
                                    gap: '8px'
                                }}>
                                    <span style={{
                                        width: '8px',
                                        height: '8px',
                                        borderRadius: '50%',
                                        backgroundColor: evt.status === 'FAILED' ? '#ef4444' : '#22c55e',
                                        flexShrink: 0,
                                        marginTop: '4px'
                                    }} />
                                    <div style={{ flex: 1 }}>
                                        <div style={{
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            marginBottom: '2px'
                                        }}>
                                            <span style={{
                                                color: evt.type === 'CAMUNDA_TASK' ? '#a78bfa' : '#60a5fa',
                                                fontWeight: 'bold'
                                            }}>
                                                {evt.nodeType || evt.type}
                                            </span>
                                            <span style={{ color: '#64748b', fontSize: '0.65rem' }}>
                                                {evt.durationMs ? `${evt.durationMs}ms` : ''}
                                            </span>
                                        </div>
                                        <div style={{ color: '#cbd5e1', wordBreak: 'break-word' }}>
                                            {evt.message}
                                        </div>
                                        {evt.error && (
                                            <div style={{ color: '#f87171', marginTop: '2px' }}>
                                                ⚠️ {evt.error}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {/* Toggle Event Log button when hidden */}
                {!showEventLog && (
                    <button
                        onClick={() => setShowEventLog(true)}
                        style={{
                            position: 'absolute',
                            bottom: '10px',
                            right: '10px',
                            padding: '8px 16px',
                            backgroundColor: '#1e293b',
                            color: '#94a3b8',
                            border: '1px solid #334155',
                            borderRadius: '6px',
                            cursor: 'pointer',
                            zIndex: 10
                        }}
                    >
                        📋 Show Events ({taskEvents.length})
                    </button>
                )}
            </div>

            {/* Right - Properties Panel */}
            {selectedNode && (
                <div className="camel-properties">
                    <h3>
                        ⚙️ Properties
                    </h3>

                    <div className="property-group">
                        <label>Label</label>
                        <input
                            value={selectedNode.data.label || ''}
                            onChange={(e) => updateNodeData(selectedNode.id, { label: e.target.value })}
                        />
                    </div>

                    {['from', 'to'].includes(selectedNode.data.nodeType) && (
                        <div className="property-group">
                            <label>URI</label>
                            <input
                                value={selectedNode.data.uri || ''}
                                onChange={(e) => updateNodeData(selectedNode.id, { uri: e.target.value })}
                                placeholder="direct:myEndpoint"
                            />
                        </div>
                    )}

                    {selectedNode.data.nodeType === 'log' && (
                        <div className="property-group">
                            <label>Message</label>
                            <textarea
                                value={selectedNode.data.message || ''}
                                onChange={(e) => updateNodeData(selectedNode.id, { message: e.target.value })}
                                placeholder="Processing: ${body}"
                            />
                        </div>
                    )}

                    {['setBody', 'transform', 'filter'].includes(selectedNode.data.nodeType) && (
                        <>
                            <div className="property-group">
                                <label>Expression</label>
                                <textarea
                                    value={selectedNode.data.expression || ''}
                                    onChange={(e) => updateNodeData(selectedNode.id, { expression: e.target.value })}
                                    placeholder="${body}"
                                />
                            </div>
                            <div className="property-group">
                                <label>Language</label>
                                <select
                                    value={selectedNode.data.expressionLanguage || 'simple'}
                                    onChange={(e) =>
                                        updateNodeData(selectedNode.id, {
                                            expressionLanguage: e.target.value as 'simple' | 'constant' | 'jsonpath'
                                        })
                                    }
                                >
                                    <option value="simple">Simple</option>
                                    <option value="constant">Constant</option>
                                    <option value="jsonpath">JSONPath</option>
                                </select>
                            </div>
                        </>
                    )}

                    {/* Saga Node Properties */}
                    {['debit', 'credit'].includes(selectedNode.data.nodeType) && (
                        <div className="property-group">
                            <label>Account Number</label>
                            <input
                                value={selectedNode.data.accountNumber || ''}
                                onChange={(e) => updateNodeData(selectedNode.id, { accountNumber: e.target.value })}
                                placeholder="1001234567"
                            />
                        </div>
                    )}

                    {selectedNode.data.nodeType === 'sagatransfer' && (
                        <>
                            <div className="property-group">
                                <label>Source Account</label>
                                <input
                                    value={selectedNode.data.sourceAccount || ''}
                                    onChange={(e) => updateNodeData(selectedNode.id, { sourceAccount: e.target.value })}
                                    placeholder="1001234567"
                                />
                            </div>
                            <div className="property-group">
                                <label>Destination Account</label>
                                <input
                                    value={selectedNode.data.destAccount || ''}
                                    onChange={(e) => updateNodeData(selectedNode.id, { destAccount: e.target.value })}
                                    placeholder="1009876543"
                                />
                            </div>
                            <div className="property-group">
                                <label>Amount</label>
                                <input
                                    type="number"
                                    value={selectedNode.data.amount || ''}
                                    onChange={(e) => updateNodeData(selectedNode.id, { amount: e.target.value })}
                                    placeholder="1000000"
                                />
                            </div>
                        </>
                    )}

                    <div className="property-group" style={{ marginTop: '1.5rem' }}>
                        <button
                            className="btn-delete"
                            onClick={deleteSelectedNode}
                            style={{
                                width: '100%',
                                padding: '0.75rem',
                                backgroundColor: '#ef4444',
                                color: 'white',
                                border: 'none',
                                borderRadius: '6px',
                                cursor: 'pointer',
                                fontWeight: 'bold'
                            }}
                        >
                            🗑️ Delete Node
                        </button>
                    </div>

                    <div className="property-group" style={{ marginTop: '1rem', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        <strong>Node ID:</strong> {selectedNode.id}<br />
                        <strong>Type:</strong> {selectedNode.data.nodeType}
                    </div>
                </div>
            )}
        </div>
    );
}
