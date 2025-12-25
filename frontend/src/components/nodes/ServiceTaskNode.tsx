import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import './NodeStyles.css';

interface ServiceTaskNodeProps {
    data: {
        label?: string;
        delegateExpression?: string;
    };
    selected?: boolean;
}

function ServiceTaskNode({ data, selected }: ServiceTaskNodeProps) {
    return (
        <div className={`bpmn-node service-task-node ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header">
                <span className="node-icon">⚙️</span>
                <span className="node-type">Service Task</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'Service Task'}</div>
                {data.delegateExpression && (
                    <div className="node-meta">
                        <span className="meta-icon">🔧</span>
                        <span className="delegate-expr">{data.delegateExpression}</span>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}

export default memo(ServiceTaskNode);
