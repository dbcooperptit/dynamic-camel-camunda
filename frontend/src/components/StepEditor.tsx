import { useEffect, useState } from 'react';
import type { StepDefinition, StepType, DelegateInfo } from '../types';
import { getDelegates } from '../api';
import './StepEditor.css';

interface Props {
    step: StepDefinition;
    allSteps: StepDefinition[];
    onUpdate: (updates: Partial<StepDefinition>) => void;
    onRemove: () => void;
    canRemove: boolean;
}

const STEP_TYPES: { value: StepType; label: string; icon: string }[] = [
    { value: 'USER_TASK', label: 'User Task', icon: '👤' },
    { value: 'SERVICE_TASK', label: 'Service Task', icon: '⚙️' },
    { value: 'EXCLUSIVE_GATEWAY', label: 'Exclusive Gateway', icon: '◇' },
    { value: 'PARALLEL_GATEWAY', label: 'Parallel Gateway', icon: '▣' },
];

export default function StepEditor({ step, allSteps, onUpdate, onRemove, canRemove }: Props) {
    const [delegates, setDelegates] = useState<DelegateInfo[]>([]);

    useEffect(() => {
        getDelegates()
            .then(setDelegates)
            .catch((err) => console.error('Failed to load delegates:', err));
    }, []);

    const getTypeIcon = (type: StepType) => {
        return STEP_TYPES.find(t => t.value === type)?.icon || '📌';
    };

    // Extract delegate name from expression like "${moneyTransferDelegate}"
    const getDelegateName = () => {
        return step.delegateExpression?.replace(/\$\{|\}/g, '') || '';
    };

    const getCurrentAction = () => {
        return (step.variables as Record<string, unknown>)?.action as string || '';
    };

    const getSelectedDelegate = () => {
        return delegates.find(d => d.name === getDelegateName());
    };

    return (
        <div className={`step-editor step-type-${step.type.toLowerCase()}`}>
            <div className="step-header">
                <span className="step-icon">{getTypeIcon(step.type)}</span>
                <input
                    type="text"
                    value={step.name}
                    onChange={e => onUpdate({ name: e.target.value })}
                    placeholder="Step name"
                    className="step-name-input"
                />
                {canRemove && (
                    <button className="remove-btn" onClick={onRemove} title="Remove step">×</button>
                )}
            </div>

            <div className="step-body">
                <div className="form-field">
                    <label>Type:</label>
                    <select
                        value={step.type}
                        onChange={e => onUpdate({ type: e.target.value as StepType })}
                    >
                        {STEP_TYPES.map(t => (
                            <option key={t.value} value={t.value}>{t.icon} {t.label}</option>
                        ))}
                    </select>
                </div>

                {step.type === 'USER_TASK' && (
                    <>
                        <div className="form-field">
                            <label>Assignee:</label>
                            <input
                                type="text"
                                value={step.assignee || ''}
                                onChange={e => onUpdate({ assignee: e.target.value })}
                                placeholder="admin"
                            />
                        </div>
                        <div className="form-field">
                            <label>Form Key:</label>
                            <input
                                type="text"
                                value={step.formKey || ''}
                                onChange={e => onUpdate({ formKey: e.target.value })}
                                placeholder="embedded:app:forms/myform.html"
                            />
                        </div>
                    </>
                )}

                {step.type === 'SERVICE_TASK' && (
                    <>
                        <div className="form-field">
                            <label>Delegate:</label>
                            <select
                                value={getDelegateName()}
                                onChange={e => {
                                    const delegateName = e.target.value;
                                    onUpdate({
                                        delegateExpression: delegateName ? `\${${delegateName}}` : '',
                                        variables: { ...step.variables, action: '' }
                                    });
                                }}
                            >
                                <option value="">-- Select Delegate --</option>
                                {delegates.map(d => (
                                    <option key={d.name} value={d.name}>{d.displayName}</option>
                                ))}
                            </select>
                        </div>
                        {getSelectedDelegate() && getSelectedDelegate()!.actions.length > 0 && (
                            <div className="form-field">
                                <label>Action:</label>
                                <select
                                    value={getCurrentAction()}
                                    onChange={e => {
                                        onUpdate({
                                            variables: { ...step.variables, action: e.target.value }
                                        });
                                    }}
                                >
                                    <option value="">-- Select Action --</option>
                                    {getSelectedDelegate()!.actions.map(a => (
                                        <option key={a.name} value={a.name}>{a.displayName}</option>
                                    ))}
                                </select>
                            </div>
                        )}
                    </>
                )}

                {(step.type === 'EXCLUSIVE_GATEWAY' || step.type === 'PARALLEL_GATEWAY') && (
                    <div className="form-field">
                        <label>Condition:</label>
                        <input
                            type="text"
                            value={step.conditionExpression || ''}
                            onChange={e => onUpdate({ conditionExpression: e.target.value })}
                            placeholder="${approved == true}"
                        />
                    </div>
                )}

                <div className="form-field">
                    <label>Next Steps:</label>
                    <select
                        multiple
                        value={step.nextSteps}
                        onChange={e => {
                            const selected = Array.from(e.target.selectedOptions, opt => opt.value);
                            onUpdate({ nextSteps: selected });
                        }}
                    >
                        {allSteps
                            .filter(s => s.id !== step.id)
                            .map(s => (
                                <option key={s.id} value={s.id}>{s.name || s.id}</option>
                            ))}
                    </select>
                </div>
            </div>

            <div className="step-id">ID: {step.id}</div>
        </div>
    );
}
