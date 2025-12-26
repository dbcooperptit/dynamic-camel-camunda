import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';

// ====== Camel Node Data Types ======
export interface CamelNodeData extends Record<string, unknown> {
    label: string;
    nodeType: 'from' | 'to' | 'log' | 'setBody' | 'transform' | 'filter' | 'choice' | 'delay'
    | 'split' | 'aggregate' | 'multicast' | 'enrich' | 'trycatch' | 'loop' | 'throttle' | 'wiretap'
    | 'debit' | 'credit' | 'sagatransfer' | 'compensate';
    uri?: string;
    message?: string;
    expression?: string;
    expressionLanguage?: 'simple' | 'constant' | 'jsonpath';
    // Saga-specific properties
    accountNumber?: string;
    sourceAccount?: string;
    destAccount?: string;
    amount?: string;

    // Runtime execution trace (populated from SSE notifications)
    execStatus?: 'STARTED' | 'COMPLETED' | 'FAILED';
    lastDurationMs?: number;
    lastExecutedAt?: number;
}

function getExecClass(data: CamelNodeData): string {
    switch (data.execStatus) {
        case 'STARTED':
            return 'exec-started';
        case 'COMPLETED':
            return 'exec-completed';
        case 'FAILED':
            return 'exec-failed';
        default:
            return '';
    }
}

function ExecMeta({ data }: { data: CamelNodeData }) {
    if (!data.execStatus) return null;

    const label = data.execStatus === 'STARTED'
        ? 'RUNNING'
        : data.execStatus === 'COMPLETED'
            ? 'OK'
            : 'FAILED';

    return (
        <div className="node-exec-meta">
            <span className={`node-exec-badge ${getExecClass(data)}`}>{label}</span>
            {typeof data.lastDurationMs === 'number' && data.execStatus !== 'STARTED' && (
                <span className="node-exec-duration">{data.lastDurationMs}ms</span>
            )}
        </div>
    );
}

// ====== From Node ======
interface FromNodeProps extends NodeProps {
    data: CamelNodeData;
}

function FromNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node from-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <div className="node-header from">
                <span className="node-icon">📥</span>
                <span className="node-type">From</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Source'}</div>
                <ExecMeta data={data} />
                {data.uri && (
                    <div className="node-uri">
                        <code>{data.uri}</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const FromNode = memo(FromNodeComponent);

// ====== To Node ======
function ToNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node to-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header to">
                <span className="node-icon">📤</span>
                <span className="node-type">To</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Destination'}</div>
                <ExecMeta data={data} />
                {data.uri && (
                    <div className="node-uri">
                        <code>{data.uri}</code>
                    </div>
                )}
            </div>
        </div>
    );
}
export const ToNode = memo(ToNodeComponent);

// ====== Log Node ======
function LogNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node log-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header log">
                <span className="node-icon">📝</span>
                <span className="node-type">Log</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Log'}</div>
                <ExecMeta data={data} />
                {data.message && (
                    <div className="node-message">
                        <code>{data.message}</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const LogNode = memo(LogNodeComponent);

// ====== SetBody Node ======
function SetBodyNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node setbody-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header setbody">
                <span className="node-icon">✏️</span>
                <span className="node-type">Set Body</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Set Body'}</div>
                <ExecMeta data={data} />
                {data.expression && (
                    <div className="node-expr">
                        <code>{data.expression}</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const SetBodyNode = memo(SetBodyNodeComponent);

// ====== Transform Node ======
function TransformNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node transform-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header transform">
                <span className="node-icon">🔄</span>
                <span className="node-type">Transform</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Transform'}</div>
                <ExecMeta data={data} />
                {data.expression && (
                    <div className="node-expr">
                        <code>{data.expression}</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const TransformNode = memo(TransformNodeComponent);

// ====== Filter Node ======
function FilterNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node filter-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header filter">
                <span className="node-icon">🔍</span>
                <span className="node-type">Filter</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Filter'}</div>
                <ExecMeta data={data} />
                {data.expression && (
                    <div className="node-expr">
                        <code>{data.expression}</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const FilterNode = memo(FilterNodeComponent);

// ====== Choice Node ======
function ChoiceNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node choice-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="choice-diamond">
                <span className="choice-symbol">?</span>
            </div>
            <div className="node-label">{data.label || 'Choice'}</div>
            <ExecMeta data={data} />
            <Handle type="source" position={Position.Right} className="handle" id="when" />
            <Handle type="source" position={Position.Bottom} className="handle" id="otherwise" />
        </div>
    );
}
export const ChoiceNode = memo(ChoiceNodeComponent);

// ====== Delay Node ======
function DelayNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node delay-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header delay">
                <span className="node-icon">⏱️</span>
                <span className="node-type">Delay</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Delay'}</div>
                <ExecMeta data={data} />
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const DelayNode = memo(DelayNodeComponent);

// ====== Split Node ======
function SplitNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node split-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header split">
                <span className="node-icon">✂️</span>
                <span className="node-type">Split</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Split'}</div>
                <ExecMeta data={data} />
                {data.expression && (
                    <div className="node-expr">
                        <code>{data.expression}</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const SplitNode = memo(SplitNodeComponent);

// ====== Aggregate Node ======
function AggregateNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node aggregate-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header aggregate">
                <span className="node-icon">🔗</span>
                <span className="node-type">Aggregate</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Aggregate'}</div>
                <ExecMeta data={data} />
                {data.expression && (
                    <div className="node-expr">
                        <code>{data.expression}</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const AggregateNode = memo(AggregateNodeComponent);

// ====== Multicast Node ======
function MulticastNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node multicast-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="multicast-icon">
                <span>📡</span>
            </div>
            <div className="node-label">{data.label || 'Multicast'}</div>
            <ExecMeta data={data} />
            <Handle type="source" position={Position.Right} className="handle" id="out1" />
            <Handle type="source" position={Position.Bottom} className="handle" id="out2" style={{ left: '70%' }} />
        </div>
    );
}
export const MulticastNode = memo(MulticastNodeComponent);

// ====== Enrich Node ======
function EnrichNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node enrich-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header enrich">
                <span className="node-icon">➕</span>
                <span className="node-type">Enrich</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Enrich'}</div>
                <ExecMeta data={data} />
                {data.uri && (
                    <div className="node-uri">
                        <code>{data.uri}</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const EnrichNode = memo(EnrichNodeComponent);

// ====== Try-Catch Node ======
function TryCatchNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node trycatch-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header trycatch">
                <span className="node-icon">🛡️</span>
                <span className="node-type">Try-Catch</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Error Handler'}</div>
                <ExecMeta data={data} />
            </div>
            <Handle type="source" position={Position.Right} className="handle" id="try" />
            <Handle type="source" position={Position.Bottom} className="handle" id="catch" />
        </div>
    );
}
export const TryCatchNode = memo(TryCatchNodeComponent);

// ====== Loop Node ======
function LoopNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node loop-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header loop">
                <span className="node-icon">🔁</span>
                <span className="node-type">Loop</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Loop'}</div>
                <ExecMeta data={data} />
                {data.expression && (
                    <div className="node-expr">
                        <code>{data.expression}</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const LoopNode = memo(LoopNodeComponent);

// ====== Throttle Node ======
function ThrottleNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node throttle-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header throttle">
                <span className="node-icon">🚦</span>
                <span className="node-type">Throttle</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Throttle'}</div>
                <ExecMeta data={data} />
                {data.expression && (
                    <div className="node-expr">
                        <code>{data.expression} msg/s</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const ThrottleNode = memo(ThrottleNodeComponent);

// ====== WireTap Node ======
function WireTapNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node wiretap-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header wiretap">
                <span className="node-icon">📋</span>
                <span className="node-type">Wire Tap</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Wire Tap'}</div>
                <ExecMeta data={data} />
                {data.uri && (
                    <div className="node-uri">
                        <code>{data.uri}</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const WireTapNode = memo(WireTapNodeComponent);

// ====== SAGA NODES ======

// ====== Debit Node ======
function DebitNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node debit-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header debit">
                <span className="node-icon">💸</span>
                <span className="node-type">Debit</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Debit Account'}</div>
                <ExecMeta data={data} />
                {(data as any).accountNumber && (
                    <div className="node-uri">
                        <code>Account: {(data as any).accountNumber}</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const DebitNode = memo(DebitNodeComponent);

// ====== Credit Node ======
function CreditNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node credit-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header credit">
                <span className="node-icon">💰</span>
                <span className="node-type">Credit</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Credit Account'}</div>
                <ExecMeta data={data} />
                {(data as any).accountNumber && (
                    <div className="node-uri">
                        <code>Account: {(data as any).accountNumber}</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const CreditNode = memo(CreditNodeComponent);

// ====== Saga Transfer Node ======
function SagaTransferNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node sagatransfer-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header sagatransfer">
                <span className="node-icon">🔄</span>
                <span className="node-type">Saga Transfer</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Transfer Money'}</div>
                <ExecMeta data={data} />
                {(data as any).sourceAccount && (data as any).destAccount && (
                    <div className="node-uri">
                        <code>{(data as any).sourceAccount} → {(data as any).destAccount}</code>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const SagaTransferNode = memo(SagaTransferNodeComponent);

// ====== Compensate Node ======
function CompensateNodeComponent({ data, selected }: FromNodeProps) {
    return (
        <div className={`camel-node compensate-node ${getExecClass(data)} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header compensate">
                <span className="node-icon">↩️</span>
                <span className="node-type">Compensate</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Rollback'}</div>
                <ExecMeta data={data} />
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}
export const CompensateNode = memo(CompensateNodeComponent);

// Export node types for React Flow
export const camelNodeTypes = {
    fromNode: FromNode,
    toNode: ToNode,
    logNode: LogNode,
    setBodyNode: SetBodyNode,
    transformNode: TransformNode,
    filterNode: FilterNode,
    choiceNode: ChoiceNode,
    delayNode: DelayNode,
    splitNode: SplitNode,
    aggregateNode: AggregateNode,
    multicastNode: MulticastNode,
    enrichNode: EnrichNode,
    trycatchNode: TryCatchNode,
    loopNode: LoopNode,
    throttleNode: ThrottleNode,
    wiretapNode: WireTapNode,
    // Saga nodes
    debitNode: DebitNode,
    creditNode: CreditNode,
    sagatransferNode: SagaTransferNode,
    compensateNode: CompensateNode,
};

