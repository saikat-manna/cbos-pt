package com.cbosgroup.cbos.core.flows.runtime;

import com.cbosgroup.cbos.core.flows.ForkJoinState;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime instance of a ForkJoinState.
 * Tracks child execution states and results.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForkJoinStateInstance extends FlowStateInstance {

    /**
     * Execution states for pausable/user task children, keyed by state ID
     */
    private final Map<String, FlowStateInstance> childExecutionStates = new HashMap<>();

    /**
     * Results from completed child states, keyed by state ID
     */
    private final Map<String, Object> childResults = new HashMap<>();

    public ForkJoinStateInstance(ForkJoinState metadata) {
        super(metadata);
    }

    /**
     * Get the ForkJoinState metadata
     */
    public ForkJoinState getForkJoinMetadata() {
        return (ForkJoinState) getMetadata();
    }

    /**
     * Store a child's execution state
     */
    public void addChildExecutionState(String stateId, FlowStateInstance instance) {
        childExecutionStates.put(stateId, instance);
    }

    /**
     * Get a child's execution state
     */
    public FlowStateInstance getChildExecutionState(String stateId) {
        return childExecutionStates.get(stateId);
    }

    /**
     * Store a child's result
     */
    public void addChildResult(String stateId, Object result) {
        childResults.put(stateId, result);
        childExecutionStates.remove(stateId);
    }

    /**
     * Check if all child states have completed
     */
    public boolean isAllChildrenCompleted() {
        return childResults.size() == getForkJoinMetadata().getChildCount();
    }

    /**
     * Check if there are pending children
     */
    public boolean hasPendingChildren() {
        return !childExecutionStates.isEmpty();
    }
}
