package com.cbosgroup.cbos.core.flows;

import com.cbosgroup.cbos.core.Version;
import com.cbosgroup.cbos.core.flows.FlowExecutionStateData.FlowStatus;
import com.cbosgroup.cbos.core.flows.runtime.FlowInstance;
import com.cbosgroup.cbos.core.flows.runtime.FlowStateInstance;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The core engine running a flow.
 * Manages flow execution, state transitions, pause/resume operations.
 */
@Slf4j
public class FlowExecuter {

    /**
     * Active flow instances, keyed by instance ID
     */
    private final Map<String, FlowInstance> activeFlows = new ConcurrentHashMap<>();

    /**
     * Receives a flow design to execute.
     * Creates a new flow instance and runs it to completion or pause.
     *
     * @param template the flow metadata template
     * @param version the version to execute
     * @return the flow instance ID for tracking
     */
    public String runFlow(FlowMetadata template, Version version) {
        // 1. Create a new flow instance
        FlowInstance instance = new FlowInstance(template, version);
        activeFlows.put(instance.getInstanceId(), instance);

        log.info("Starting flow '{}' with instance ID: {}", template.getFlowName(), instance.getInstanceId());

        // 2. Initialize and get the start state
        instance.initialize();

        // 3. Execute the flow
        executeFlowLoop(instance);

        return instance.getInstanceId();
    }

    /**
     * Resume a paused flow by its instance ID.
     *
     * @param instanceId the flow instance ID
     * @throws IllegalStateException if flow not found or not paused
     */
    public void resumeFlow(String instanceId) {
        FlowInstance instance = activeFlows.get(instanceId);
        if (instance == null) {
            throw new IllegalStateException("Flow instance not found: " + instanceId);
        }

        if (!instance.isPaused()) {
            throw new IllegalStateException("Flow is not paused. Current status: " +
                    instance.getExecutionData().getFlowStatus());
        }

        log.info("Resuming flow instance: {}", instanceId);

        // Resume from current state
        instance.getExecutionData().setFlowStatus(FlowStatus.RUNNING);
        FlowStateInstance currentState = instance.getCurrentState();

        // Resume the current state and continue execution
        String nextStateId = currentState.resume(instance.getContext());
        if (nextStateId != null && !currentState.isTerminal()) {
            instance.transitionTo(nextStateId);
            executeFlowLoop(instance);
        } else {
            instance.complete();
        }
    }

    /**
     * Pause a running flow.
     *
     * @param instanceId the flow instance ID
     */
    public void pauseFlow(String instanceId) {
        FlowInstance instance = activeFlows.get(instanceId);
        if (instance == null) {
            throw new IllegalStateException("Flow instance not found: " + instanceId);
        }

        if (instance.isRunning() && instance.getCurrentState().isPausable()) {
            instance.pause();
            log.info("Flow paused at state: {}", instance.getExecutionData().getCurrentStateId());
        } else {
            throw new IllegalStateException("Flow cannot be paused in current state");
        }
    }

    /**
     * Get a flow instance by ID.
     *
     * @param instanceId the flow instance ID
     * @return the flow instance, or null if not found
     */
    public FlowInstance getFlowInstance(String instanceId) {
        return activeFlows.get(instanceId);
    }

    /**
     * Get the status of a flow.
     *
     * @param instanceId the flow instance ID
     * @return the flow status
     */
    public FlowStatus getFlowStatus(String instanceId) {
        FlowInstance instance = activeFlows.get(instanceId);
        return instance != null ? instance.getExecutionData().getFlowStatus() : null;
    }

    /**
     * Main execution loop - runs states until completion, pause, or terminal state.
     */
    private void executeFlowLoop(FlowInstance instance) {
        while (instance.isRunning()) {
            FlowStateInstance currentState = instance.getCurrentState();
            FlowStateMetadata metadata = currentState.getMetadata();

            log.debug("Executing state: {} ({})", metadata.getStateId(), metadata.getStateName());

            // Execute the current state
            String nextStateId = currentState.execute(instance.getContext());

            // Check if we should pause
            if (currentState.isPausable() && shouldPause(instance)) {
                instance.pause();
                log.info("Flow paused at state: {}", metadata.getStateId());
                return;
            }

            // Check if terminal or no next state
            if (currentState.isTerminal() || nextStateId == null) {
                instance.complete();
                log.info("Flow completed at terminal state: {}", metadata.getStateId());
                return;
            }

            // Transition to next state
            instance.transitionTo(nextStateId);
        }
    }

    /**
     * Hook to determine if flow should pause at current state.
     * Can be overridden to implement custom pause logic.
     *
     * @param instance the flow instance
     * @return true if flow should pause
     */
    protected boolean shouldPause(FlowInstance instance) {
        // Default: don't auto-pause, require explicit pause call
        return false;
    }

    /**
     * Remove a completed or cancelled flow from active flows.
     *
     * @param instanceId the flow instance ID
     */
    public void removeFlow(String instanceId) {
        FlowInstance removed = activeFlows.remove(instanceId);
        if (removed != null) {
            log.info("Removed flow instance: {}", instanceId);
        }
    }

    /**
     * Get count of active flows.
     *
     * @return number of active flow instances
     */
    public int getActiveFlowCount() {
        return activeFlows.size();
    }
}
