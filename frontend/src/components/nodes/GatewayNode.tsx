import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import './NodeStyles.css';

interface GatewayNodeProps {
    data: {
        label?: string;
        gatewayType?: 'exclusive' | 'parallel';
    };
    selected?: boolean;
}

function GatewayNode({ data, selected }: GatewayNodeProps) {
    const isExclusive = data.gatewayType === 'exclusive';

    return (
        <div className={`bpmn-node gateway-node ${isExclusive ? 'exclusive' : 'parallel'} ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="gateway-diamond">
                <span className="gateway-symbol">{isExclusive ? '✕' : '+'}</span>
            </div>
            <div className="node-label">{data.label || (isExclusive ? 'Exclusive' : 'Parallel')}</div>
            <Handle type="source" position={Position.Right} className="handle handle-top" id="a" />
            <Handle type="source" position={Position.Bottom} className="handle" id="b" />
        </div>
    );
}

export default memo(GatewayNode);
