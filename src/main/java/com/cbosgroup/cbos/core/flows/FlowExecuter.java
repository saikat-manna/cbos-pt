package com.cbosgroup.cbos.core.flows;

import com.cbosgroup.cbos.core.Version;
import com.cbosgroup.cbos.core.actors.Actor;
import com.cbosgroup.cbos.core.flows.FlowExecutionStateData.FlowStatus;
import com.cbosgroup.cbos.core.flows.runtime.FlowInstance;
import com.cbosgroup.cbos.core.flows.runtime.FlowStateInstance;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
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
     * Optional notifier for actor notifications on user tasks
     */
    @Setter
    private ActorNotifier actorNotifier;

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

            // Check if this is a UserTaskState - auto-pause and wait for user input
            if (metadata instanceof UserTaskState userTask) {
                instance.awaitUserInput();
                log.info("Flow awaiting user input at state: {}", metadata.getStateId());

                // Notify eligible actors
                if (actorNotifier != null && userTask.getEligibleRoles() != null) {
                    List<Actor> eligibleActors = instance.getActorsByRoles(userTask.getEligibleRoles());
                    if (!eligibleActors.isEmpty()) {
                        actorNotifier.notifyActors(eligibleActors, userTask, instance);
                        log.info("Notified {} actors for task: {}", eligibleActors.size(), metadata.getStateId());
                    }
                }
                return;
            }

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
     * Submit user response for a flow waiting for input.
     *
     * @param instanceId the flow instance ID
     * @param response the user response data
     * @throws IllegalStateException if flow not found or not awaiting input
     * @throws IllegalArgumentException if response validation fails
     */
    public void submitUserResponse(String instanceId, Map<String, Object> response) {
        FlowInstance instance = activeFlows.get(instanceId);
        if (instance == null) {
            throw new IllegalStateException("Flow instance not found: " + instanceId);
        }

        if (!instance.isAwaitingUserInput()) {
            throw new IllegalStateException("Flow is not awaiting user input. Current status: " +
                    instance.getExecutionData().getFlowStatus());
        }

        FlowStateMetadata metadata = instance.getCurrentState().getMetadata();
        if (!(metadata instanceof UserTaskState userTask)) {
            throw new IllegalStateException("Current state is not a user task");
        }

        // Validate response
        UserTaskState.ValidationResult validation = userTask.validateResponse(response);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid response: " + validation.getErrorMessage());
        }

        log.info("User response received for flow: {}, state: {}", instanceId, metadata.getStateId());

        // Store response in context
        instance.getContext().put("_userResponse_" + metadata.getStateId(), response);

        // Execute the onResponse handler
        String nextStateId = null;
        if (userTask.getOnResponse() != null) {
            nextStateId = userTask.getOnResponse().apply(instance.getContext(), response);
        }

        // Resume flow execution
        instance.getExecutionData().setFlowStatus(FlowStatus.RUNNING);

        if (nextStateId != null && !metadata.isTerminal()) {
            instance.transitionTo(nextStateId);
            executeFlowLoop(instance);
        } else {
            instance.complete();
            log.info("Flow completed after user response at state: {}", metadata.getStateId());
        }
    }

    /**
     * Get the pending user task for a flow awaiting input.
     *
     * @param instanceId the flow instance ID
     * @return the UserTaskState, or null if not awaiting input
     */
    public UserTaskState getPendingUserTask(String instanceId) {
        FlowInstance instance = activeFlows.get(instanceId);
        if (instance == null || !instance.isAwaitingUserInput()) {
            return null;
        }

        FlowStateMetadata metadata = instance.getCurrentState().getMetadata();
        return metadata instanceof UserTaskState ? (UserTaskState) metadata : null;
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
