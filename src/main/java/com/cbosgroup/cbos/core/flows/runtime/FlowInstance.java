package com.cbosgroup.cbos.core.flows.runtime;

import com.cbosgroup.cbos.core.Version;
import com.cbosgroup.cbos.core.actors.Actor;
import com.cbosgroup.cbos.core.flows.FlowExecutionStateData;
import com.cbosgroup.cbos.core.flows.FlowExecutionStateData.FlowStatus;
import com.cbosgroup.cbos.core.flows.FlowMetadata;
import com.cbosgroup.cbos.core.flows.FlowStateMetadata;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Runtime instance of a flow execution
 */
@Data
public class FlowInstance {

    private String instanceId;

    private FlowMetadata flowMeta;

    private Version version;


    private FlowExecutionHistory history;

    private FlowExecutionStateData executionData;

    /**
     * Actors participating in this flow instance
     */
    private List<Actor> actors = new ArrayList<>();

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
        FlowStateInstance currentInstance  = new FlowStateInstance(startMeta);
        this.executionData.setCurrentStateId(startMeta.getStateId());
        this.executionData.setCurrentState(currentInstance);
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
        FlowStateInstance currentInstance  = new FlowStateInstance(nextMeta);
        this.executionData.setCurrentStateId(nextStateId);
        this.executionData.setCurrentState(currentInstance);
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
     * Set flow to await user input (for UserTaskState)
     */
    public void awaitUserInput() {
        this.executionData.setFlowStatus(FlowStatus.AWAITING_USER_INPUT);
        history.addEntry(executionData.getCurrentStateId(), "AWAITING_USER_INPUT", "Waiting for user response");
    }

    /**
     * Check if flow is awaiting user input
     */
    public boolean isAwaitingUserInput() {
        return executionData.getFlowStatus() == FlowStatus.AWAITING_USER_INPUT;
    }

    /**
     * Get the context map
     */
    public Map<String, Object> getContext() {
        return executionData.getContext();
    }

    /**
     * Get actors filtered by eligible roles
     * @param eligibleRoles set of roles that are eligible
     * @return actors whose role matches any of the eligible roles
     */
    public List<Actor> getActorsByRoles(Set<String> eligibleRoles) {
        if (actors == null || eligibleRoles == null || eligibleRoles.isEmpty()) {
            return Collections.emptyList();
        }
        return actors.stream()
                .filter(a -> a.getRole() != null && eligibleRoles.contains(a.getRole()))
                .collect(Collectors.toList());
    }

	public FlowStateInstance getCurrentState() {
		return executionData.getCurrentState();
	}
}
