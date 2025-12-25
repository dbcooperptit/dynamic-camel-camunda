import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import './NodeStyles.css';

interface StartNodeProps {
    data: {
        label?: string;
    };
}

function StartNode({ data }: StartNodeProps) {
    return (
        <div className="bpmn-node start-node">
            <div className="node-icon">▶️</div>
            <div className="node-label">{data.label || 'Start'}</div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}

export default memo(StartNode);
