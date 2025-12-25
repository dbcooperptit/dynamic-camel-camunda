import type { StepDefinition } from './types';
import type { Node, Edge } from '@xyflow/react';
import type { BpmnNodeData } from './types';

export interface WorkflowTemplate {
    name: string;
    description: string;
    processKey: string;
    processName: string;
    steps: StepDefinition[];
}

// Shared workflow templates used by both Legacy and Visual Builder
export const WORKFLOW_TEMPLATES: Record<string, WorkflowTemplate> = {
    'money-transfer': {
        name: '💸 Money Transfer',
        processKey: 'money-transfer-v2',
        processName: 'Money Transfer Process',
        description: 'Quy trình chuyển tiền: Tạo yêu cầu → Xác thực → Kiểm tra số dư → Phê duyệt (nếu > 50 triệu) → Chuyển tiền → Thông báo',
        steps: [
            { id: 'create_request', name: 'Tạo yêu cầu chuyển tiền', type: 'USER_TASK', assignee: 'admin', nextSteps: ['validate_source'] },
            { id: 'validate_source', name: 'Xác thực TK nguồn', type: 'SERVICE_TASK', delegateExpression: '${moneyTransferDelegate}', nextSteps: ['validate_dest'], variables: { action: 'validateSourceAccount' } },
            { id: 'validate_dest', name: 'Xác thực TK đích', type: 'SERVICE_TASK', delegateExpression: '${moneyTransferDelegate}', nextSteps: ['check_balance'], variables: { action: 'validateDestAccount' } },
            { id: 'check_balance', name: 'Kiểm tra số dư', type: 'SERVICE_TASK', delegateExpression: '${moneyTransferDelegate}', nextSteps: ['balance_gateway'], variables: { action: 'checkBalance' } },
            { id: 'balance_gateway', name: 'Số dư đủ?', type: 'EXCLUSIVE_GATEWAY', nextSteps: ['approval_gateway', 'notify_insufficient'], nextStepConditions: { 'approval_gateway': '${balanceSufficient == true}', 'notify_insufficient': '${balanceSufficient == false}' } },
            { id: 'notify_insufficient', name: 'Thông báo số dư không đủ', type: 'USER_TASK', assignee: 'admin', nextSteps: [] },
            { id: 'approval_gateway', name: 'Cần phê duyệt?', type: 'EXCLUSIVE_GATEWAY', nextSteps: ['manager_approval', 'execute_transfer'], nextStepConditions: { 'manager_approval': '${amount > 50000000}', 'execute_transfer': '${amount <= 50000000}' } },
            { id: 'manager_approval', name: 'Phê duyệt quản lý', type: 'USER_TASK', assignee: 'manager', nextSteps: ['approved_gateway'] },
            { id: 'approved_gateway', name: 'Được duyệt?', type: 'EXCLUSIVE_GATEWAY', nextSteps: ['execute_transfer', 'notify_rejected'], nextStepConditions: { 'execute_transfer': '${approved == true}', 'notify_rejected': '${approved == false}' } },
            { id: 'notify_rejected', name: 'Thông báo từ chối', type: 'USER_TASK', assignee: 'admin', nextSteps: [] },
            { id: 'execute_transfer', name: 'Thực hiện chuyển tiền', type: 'SERVICE_TASK', delegateExpression: '${moneyTransferDelegate}', nextSteps: ['send_notification'], variables: { action: 'executeTransfer' } },
            { id: 'send_notification', name: 'Gửi thông báo', type: 'SERVICE_TASK', delegateExpression: '${moneyTransferDelegate}', nextSteps: ['confirm'], variables: { action: 'sendNotification' } },
            { id: 'confirm', name: 'Xác nhận hoàn thành', type: 'USER_TASK', assignee: 'admin', nextSteps: [] },
        ],
    },
    'money-transfer-auto': {
        name: '🚀 Money Transfer (Auto)',
        processKey: 'money-transfer-auto',
        processName: 'Money Transfer Process (Auto)',
        description: '🚀 Quy trình chuyển tiền TỰ ĐỘNG - Chạy từ đầu đến cuối không cần User Task',
        steps: [
            { id: 'validate_source', name: 'Xác thực TK nguồn', type: 'SERVICE_TASK', delegateExpression: '${moneyTransferDelegate}', nextSteps: ['validate_dest'], variables: { action: 'validateSourceAccount' } },
            { id: 'validate_dest', name: 'Xác thực TK đích', type: 'SERVICE_TASK', delegateExpression: '${moneyTransferDelegate}', nextSteps: ['check_balance'], variables: { action: 'validateDestAccount' } },
            { id: 'check_balance', name: 'Kiểm tra số dư', type: 'SERVICE_TASK', delegateExpression: '${moneyTransferDelegate}', nextSteps: ['balance_gateway'], variables: { action: 'checkBalance' } },
            { id: 'balance_gateway', name: 'Số dư đủ?', type: 'EXCLUSIVE_GATEWAY', nextSteps: ['execute_transfer', 'log_insufficient'], nextStepConditions: { 'execute_transfer': '${balanceSufficient == true}', 'log_insufficient': '${balanceSufficient == false}' } },
            { id: 'log_insufficient', name: 'Log: Số dư không đủ', type: 'SERVICE_TASK', delegateExpression: '${genericServiceDelegate}', nextSteps: [], variables: { action: 'log', logMessage: 'Insufficient balance' } },
            { id: 'execute_transfer', name: 'Thực hiện chuyển tiền', type: 'SERVICE_TASK', delegateExpression: '${moneyTransferDelegate}', nextSteps: ['send_notification'], variables: { action: 'executeTransfer' } },
            { id: 'send_notification', name: 'Gửi thông báo', type: 'SERVICE_TASK', delegateExpression: '${moneyTransferDelegate}', nextSteps: [], variables: { action: 'sendNotification' } },
        ],
    },
    'leave-request': {
        name: '🏖️ Leave Request',
        processKey: 'leave-request',
        processName: 'Leave Request Process',
        description: 'Quy trình xin nghỉ phép: Gửi đơn → Quản lý duyệt → HR xác nhận',
        steps: [
            { id: 'submit', name: 'Gửi đơn xin nghỉ', type: 'USER_TASK', assignee: 'employee', nextSteps: ['manager_review'] },
            { id: 'manager_review', name: 'Quản lý xem xét', type: 'USER_TASK', assignee: 'manager', nextSteps: ['decision_gateway'] },
            { id: 'decision_gateway', name: 'Được duyệt?', type: 'EXCLUSIVE_GATEWAY', nextSteps: ['hr_confirm', 'notify_reject'] },
            { id: 'hr_confirm', name: 'HR xác nhận', type: 'USER_TASK', assignee: 'hr', nextSteps: [] },
            { id: 'notify_reject', name: 'Thông báo từ chối', type: 'SERVICE_TASK', delegateExpression: '${genericServiceDelegate}', nextSteps: [], variables: { action: 'notify' } },
        ],
    },
    'camel-integration': {
        name: '🐪 Camel Integration Demo',
        processKey: 'camel-integration-dynamic',
        processName: 'Apache Camel Integration Demo',
        description: 'Demo tích hợp Camunda + Camel: Chọn loại demo → Gọi Camel Route → Xem kết quả. Hỗ trợ: HTTP API, Message Routing, JSON Transform, Orchestration',
        steps: [
            { id: 'choose_demo', name: 'Chọn loại Demo', type: 'USER_TASK', assignee: 'demo', nextSteps: ['demo_gateway'] },
            { id: 'demo_gateway', name: 'Route?', type: 'EXCLUSIVE_GATEWAY', nextSteps: ['call_api', 'route_message', 'transform_json', 'orchestrate'], nextStepConditions: { 'call_api': "${demoType == 'integration'}", 'route_message': "${demoType == 'routing'}", 'transform_json': "${demoType == 'transform'}", 'orchestrate': "${demoType == 'orchestrate'}" } },
            { id: 'call_api', name: 'Call External API', type: 'SERVICE_TASK', delegateExpression: '${camelDelegate}', nextSteps: ['merge_gateway'], variables: { action: 'callExternalApi', camelRoute: 'callExternalApi' } },
            { id: 'route_message', name: 'Route Message', type: 'SERVICE_TASK', delegateExpression: '${camelDelegate}', nextSteps: ['merge_gateway'], variables: { action: 'routeMessage', camelRoute: 'routeMessage' } },
            { id: 'transform_json', name: 'Transform JSON', type: 'SERVICE_TASK', delegateExpression: '${camelDelegate}', nextSteps: ['merge_gateway'], variables: { action: 'transformJson', camelRoute: 'transformJson' } },
            { id: 'orchestrate', name: 'Orchestrate Services', type: 'SERVICE_TASK', delegateExpression: '${camelDelegate}', nextSteps: ['merge_gateway'], variables: { action: 'routeMessage', camelRoute: 'orchestrate' } },
            { id: 'merge_gateway', name: 'Merge', type: 'EXCLUSIVE_GATEWAY', nextSteps: ['review_result'] },
            { id: 'review_result', name: 'Xem kết quả', type: 'USER_TASK', assignee: 'demo', nextSteps: [] },
        ],
    },
};

// Convert StepDefinition[] to React Flow nodes and edges for Visual Builder
export function convertTemplateToNodes(template: WorkflowTemplate): { nodes: Node<BpmnNodeData>[]; edges: Edge[] } {
    const nodes: Node<BpmnNodeData>[] = [];
    const edges: Edge[] = [];

    // Start node
    nodes.push({
        id: 'start',
        type: 'startNode',
        position: { x: 50, y: 200 },
        data: { label: 'Start', stepType: 'START_EVENT' },
    });

    // Convert steps to nodes
    let xPos = 200;
    const yBase = 200;
    const xSpacing = 200;

    // Track gateway branches for layout
    const stepToNode: Record<string, Node<BpmnNodeData>> = {};

    template.steps.forEach((step, index) => {
        const nodeType = step.type === 'USER_TASK' ? 'userTask'
            : step.type === 'SERVICE_TASK' ? 'serviceTask'
                : step.type === 'EXCLUSIVE_GATEWAY' || step.type === 'PARALLEL_GATEWAY' ? 'gateway'
                    : 'serviceTask';

        // Calculate position (simple linear layout, could be improved)
        const yOffset = step.type === 'EXCLUSIVE_GATEWAY' ? 0 : ((index % 3) - 1) * 30;

        const node: Node<BpmnNodeData> = {
            id: step.id,
            type: nodeType,
            position: { x: xPos, y: yBase + yOffset },
            data: {
                label: step.name,
                stepType: step.type,
                assignee: step.assignee,
                delegateExpression: step.delegateExpression,
                variables: step.variables,
            },
        };

        nodes.push(node);
        stepToNode[step.id] = node;
        xPos += xSpacing;
    });

    // End node
    nodes.push({
        id: 'end',
        type: 'endNode',
        position: { x: xPos, y: yBase },
        data: { label: 'End', stepType: 'END_EVENT' },
    });

    // Create edges - connect start to first step
    if (template.steps.length > 0) {
        edges.push({
            id: `start-${template.steps[0].id}`,
            source: 'start',
            target: template.steps[0].id,
        });
    }

    // Create edges between steps
    template.steps.forEach(step => {
        if (step.nextSteps.length === 0) {
            // Connect to end node
            edges.push({
                id: `${step.id}-end`,
                source: step.id,
                target: 'end',
            });
        } else {
            step.nextSteps.forEach(nextId => {
                edges.push({
                    id: `${step.id}-${nextId}`,
                    source: step.id,
                    target: nextId,
                });
            });
        }
    });

    return { nodes, edges };
}
