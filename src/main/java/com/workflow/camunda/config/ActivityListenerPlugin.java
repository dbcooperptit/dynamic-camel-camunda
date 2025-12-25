package com.workflow.camunda.config;

import com.workflow.camunda.listener.ActivityEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.camunda.bpm.spring.boot.starter.configuration.Ordering;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * Camunda Process Engine Plugin that registers the global activity event
 * listener
 * for SSE-based realtime workflow visualization.
 */
@Configuration
@Order(Ordering.DEFAULT_ORDER + 1)
@RequiredArgsConstructor
@Slf4j
public class ActivityListenerPlugin extends AbstractProcessEnginePlugin {

    private final ActivityEventListener activityEventListener;

    @Override
    public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
        log.info("🔧 Registering Activity Event Listener Plugin for realtime visualization");

        List<BpmnParseListener> listeners = processEngineConfiguration.getCustomPreBPMNParseListeners();
        if (listeners == null) {
            listeners = new ArrayList<>();
            processEngineConfiguration.setCustomPreBPMNParseListeners(listeners);
        }

        listeners.add(new ActivityListenerBpmnParseListener(activityEventListener));
    }

    /**
     * BPMN Parse Listener that adds our activity event listener to all activities
     */
    private static class ActivityListenerBpmnParseListener extends AbstractBpmnParseListener {

        private final ExecutionListener listener;

        public ActivityListenerBpmnParseListener(ExecutionListener listener) {
            this.listener = listener;
        }

        private void addListeners(ActivityImpl activity) {
            activity.addListener(ExecutionListener.EVENTNAME_START, listener);
            activity.addListener(ExecutionListener.EVENTNAME_END, listener);
        }

        @Override
        public void parseStartEvent(Element startEventElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseEndEvent(Element endEventElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseUserTask(Element userTaskElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseServiceTask(Element serviceTaskElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseExclusiveGateway(Element exclusiveGwElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseParallelGateway(Element parallelGwElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseInclusiveGateway(Element inclusiveGwElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseScriptTask(Element scriptTaskElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseSendTask(Element sendTaskElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseReceiveTask(Element receiveTaskElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseManualTask(Element manualTaskElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseBusinessRuleTask(Element businessRuleTaskElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseCallActivity(Element callActivityElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseSubProcess(Element subProcessElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseTask(Element taskElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseIntermediateThrowEvent(Element intermediateEventElement, ScopeImpl scope,
                ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseIntermediateCatchEvent(Element intermediateEventElement, ScopeImpl scope,
                ActivityImpl activity) {
            addListeners(activity);
        }

        @Override
        public void parseBoundaryEvent(Element boundaryEventElement, ScopeImpl scope, ActivityImpl activity) {
            addListeners(activity);
        }
    }
}
