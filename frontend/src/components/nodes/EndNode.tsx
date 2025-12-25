import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import './NodeStyles.css';

interface EndNodeProps {
    data: {
        label?: string;
    };
}

function EndNode({ data }: EndNodeProps) {
    return (
        <div className="bpmn-node end-node">
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-icon">⏹️</div>
            <div className="node-label">{data.label || 'End'}</div>
        </div>
    );
}

export default memo(EndNode);
