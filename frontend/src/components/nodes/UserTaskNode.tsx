import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import './NodeStyles.css';

interface UserTaskNodeProps {
    data: {
        label?: string;
        assignee?: string;
    };
    selected?: boolean;
}

function UserTaskNode({ data, selected }: UserTaskNodeProps) {
    return (
        <div className={`bpmn-node user-task-node ${selected ? 'selected' : ''}`}>
            <Handle type="target" position={Position.Left} className="handle" />
            <div className="node-header">
                <span className="node-icon">👤</span>
                <span className="node-type">User Task</span>
            </div>
            <div className="node-body">
                <div className="node-label">{data.label || 'User Task'}</div>
                {data.assignee && (
                    <div className="node-meta">
                        <span className="meta-icon">👨‍💼</span>
                        <span>{data.assignee}</span>
                    </div>
                )}
            </div>
            <Handle type="source" position={Position.Right} className="handle" />
        </div>
    );
}

export default memo(UserTaskNode);
