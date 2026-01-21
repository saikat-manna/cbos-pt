package com.cbosgroup.cbos.core.flows.runtime;

import com.cbosgroup.cbos.core.Version;
import com.cbosgroup.cbos.core.flows.FlowExecutionStateData;
import com.cbosgroup.cbos.core.flows.FlowExecutionStateData.FlowStatus;
import com.cbosgroup.cbos.core.flows.FlowMetadata;
import com.cbosgroup.cbos.core.flows.FlowStateMetadata;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime instance of a flow execution
 */
@Data
public class FlowInstance {

    private String instanceId;

    private FlowMetadata flowMeta;

    private Version version;

    private FlowStateInstance currentState;

    private FlowExecutionHistory history;

    private FlowExecutionStateData executionData;

    public FlowInstance(FlowMetadata flowMeta, Version version) {
        this.instanceId = UUID.randomUUID().toString();
        this.flowMeta = flowMeta;
        this.version = version;
        this.history = new FlowExecutionHistory();
        this.executionData = FlowExecutionStateData.builder()
                .context(new HashMap<>())
                .flowStatus(FlowStatus.NOT_STARTED)
                .build();
    }

    /**
     * Initialize and move to the start state
     */
    public void initialize() {
        FlowStateMetadata startMeta = flowMeta.getStartState();
        if (startMeta == null) {
            throw new IllegalStateException("Flow has no start state defined");
        }
        this.currentState = new FlowStateInstance(startMeta);
        this.executionData.setCurrentStateId(startMeta.getStateId());
        this.executionData.setCurrentState(currentState);
        this.executionData.setFlowStatus(FlowStatus.RUNNING);
        history.addEntry(startMeta.getStateId(), "INITIALIZED", "Flow started");
    }

    /**
     * Transition to the next state by ID
     */
    public void transitionTo(String nextStateId) {
        FlowStateMetadata nextMeta = flowMeta.getState(nextStateId);
        if (nextMeta == null) {
            throw new IllegalStateException("Unknown state: " + nextStateId);
        }
        this.currentState = new FlowStateInstance(nextMeta);
        this.executionData.setCurrentStateId(nextStateId);
        this.executionData.setCurrentState(currentState);
        history.addEntry(nextStateId, "TRANSITIONED", "Moved to state: " + nextMeta.getStateName());
    }

    /**
     * Mark flow as completed
     */
    public void complete() {
        this.executionData.setFlowStatus(FlowStatus.COMPLETED);
        history.addEntry(executionData.getCurrentStateId(), "COMPLETED", "Flow completed");
    }

    /**
     * Pause the flow
     */
    public void pause() {
        this.executionData.setFlowStatus(FlowStatus.PAUSED);
        history.addEntry(executionData.getCurrentStateId(), "PAUSED", "Flow paused");
    }

    /**
     * Check if flow is running
     */
    public boolean isRunning() {
        return executionData.getFlowStatus() == FlowStatus.RUNNING;
    }

    /**
     * Check if flow is paused
     */
    public boolean isPaused() {
        return executionData.getFlowStatus() == FlowStatus.PAUSED;
    }

    /**
     * Get the context map
     */
    public Map<String, Object> getContext() {
        return executionData.getContext();
    }
}
