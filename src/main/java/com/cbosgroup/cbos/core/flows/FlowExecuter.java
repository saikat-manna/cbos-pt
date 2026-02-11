package com.cbosgroup.cbos.core.flows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.cbosgroup.cbos.core.Version;
import com.cbosgroup.cbos.core.actors.Actor;
import com.cbosgroup.cbos.core.flows.FlowExecutionStateData.FlowStatus;
import com.cbosgroup.cbos.core.flows.repository.PrebuiltFlowsResposirty;
import com.cbosgroup.cbos.core.flows.runtime.FlowInstance;
import com.cbosgroup.cbos.core.flows.runtime.BaseFlowNodeInstance;
import com.cbosgroup.cbos.core.flows.runtime.ForkJoinStateInstance;
import com.cbosgroup.cbos.core.flows.runtime.SubflowInstance;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * The core engine running a flow. Manages flow execution, state transitions,
 * pause/resume operations.
 */
@Slf4j
@RequiredArgsConstructor
public class FlowExecuter {

	@NonNull
	private PrebuiltFlowsResposirty flowResposirty;

	/**
	 * Active flow instances, keyed by instance ID
	 */
	private final Map<String, ConcurrentLinkedDeque<FlowInstance>> activeFlows = new ConcurrentHashMap<>();

	/**
	 * Global thread pool for parallel execution
	 */
	private final ExecutorService threadPool = Executors.newCachedThreadPool();

	/**
	 * Optional notifier for actor notifications on user tasks
	 */
	@Setter
	private ActorNotifier actorNotifier;

	/**
	 * Receives a flow design to execute. Creates a new flow instance and runs it to
	 * completion or pause.
	 *
	 * @param template the flow metadata template
	 * @param version  the version to execute
	 * @return the flow instance ID for tracking
	 */
	public String runFlow(FlowMetadata template, Version version) {
		// 1. Create a new flow instance
		FlowInstance instance = new FlowInstance(template, version);
		putCurrentRunningFlow(instance);

		log.info("Starting flow '{}' with instance ID: {}", template.getFlowName(), instance.getInstanceId());

		// 2. Initialize and get the start state
		instance.initialize();

		// 3. Execute the flow
		executeFlowLoop(instance);

		return instance.getInstanceId();
	}

	private FlowInstance getCurrentRunningFlow(String instanceId) {
		var flowStack = activeFlows.get(instanceId);
		return flowStack.peek();
	}

	private void putCurrentRunningFlow(FlowInstance instance) {
		var stackForThisId = activeFlows.get(instance.getInstanceId());
		if (stackForThisId == null) {
			stackForThisId = new ConcurrentLinkedDeque<FlowInstance>();
			activeFlows.put(instance.getInstanceId(), stackForThisId);
		}
		stackForThisId.push(instance);
	}

	/**
	 * Resume a paused flow by its instance ID.
	 *
	 * @param instanceId the flow instance ID
	 * @throws IllegalStateException if flow not found or not paused
	 */
	public void resumeFlow(String instanceId) {
		FlowInstance instance = getCurrentRunningFlow(instanceId);
		if (instance == null) {
			throw new IllegalStateException("Flow instance not found: " + instanceId);
		}

		if (!instance.isPaused()) {
			throw new IllegalStateException(
					"Flow is not paused. Current status: " + instance.getExecutionData().getFlowStatus());
		}

		log.info("Resuming flow instance: {}", instanceId);

		// Resume from current state
		instance.getExecutionData().setFlowStatus(FlowStatus.RUNNING);
		BaseFlowNodeInstance currentState = instance.getCurrentState();

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
		FlowInstance instance = getCurrentRunningFlow(instanceId);
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
	 * Main execution loop - runs states until completion, pause, or terminal state.
	 */
	private void executeFlowLoop(FlowInstance instance) {
		while (instance.isRunning()) {
			BaseFlowNodeInstance currentState = instance.getCurrentState();
			BaseFlowNode metadata = currentState.getMetadata();

			log.debug("Executing state: {} ({})", metadata.getStateId(), metadata.getStateName());

			// Check if this is a UserTaskState - auto-pause and wait for user input
			if (metadata instanceof UserTaskNode userTask) {
				instance.awaitUserInput();
				log.info("Flow awaiting user input at state: {}", metadata.getStateId());
				notifyActorsForUserTask(instance, userTask);
				return;
			}

			// Check if this is a ForkJoinState
			if (metadata instanceof ForkJoinNode forkJoinMeta) {
				executeForkJoin(instance, forkJoinMeta);
				return;
			}

			// subflow
			if (metadata instanceof SubflowNode) {
				// subflow execution
				// 1. Get the flowMeta from the repository
				FlowMetadata subflowMetadata = flowResposirty.getFlowByName(((SubflowNode) metadata).getFlowId());
				if (subflowMetadata == null) {
					throw new RuntimeException(
							"There is no flow named " + ((SubflowNode) metadata).getFlowId() + " In repository");
				}
				// we have got the subflow meta , now lets execute this
				FlowInstance subflowInstance = new SubflowInstance(subflowMetadata, instance);
				// pause the parent instance
				instance.pause();
				// change the stack
				continueWithSubflow(subflowInstance);
				return;
			}

			// Check if we should pause, deal with pausable state
			if (currentState.isPausable() && shouldPause(instance)) {
				instance.pause();
				log.info("Flow paused at state: {}", metadata.getStateId());
				return;
			}

			// Execute the current state
			String nextStateId = currentState.execute(instance.getContext());
			if (nextStateId == null) {
				// nowwhere to go
				if (iSubflow(instance)) {
					throw new RuntimeException("Subflow ended with nowhere to go");
				}
				if (!currentState.isTerminal()) {
					throw new RuntimeException("No result on non terminal state");
				}
				log.info("Flow concluded!!!");
				return;
			}

			// we have next state Id
			if (iSubflow(instance)) {
				if (currentState.isTerminal()) {
					// we have a completetd subflow with result
					SubflowInstance currentSubflowInstance = (SubflowInstance) instance;
					resumeParatentflowAfterSubflowCompletion();
					// 1. Pop the current subflow

				}
			}

			// regular flow with result . move on
			// Transition to next state
			instance.transitionTo(nextStateId);

		}
	}

	private boolean iSubflow(FlowInstance instance) {
		return instance instanceof SubflowInstance;
	}

	private void resumeParatentflowAfterSubflowCompletion() {
		// TODO Auto-generated method stub

	}

	/**
	 * Continues as subflow
	 * 
	 * @param subflowInstance
	 */
	private void continueWithSubflow(FlowInstance subflowInstance) {
		executeFlowLoop(subflowInstance);
	}

	/**
	 * Execute a fork-join state - runs children in parallel.
	 */
	private void executeForkJoin(FlowInstance instance, ForkJoinNode forkJoinMeta) {
		String forkStateId = forkJoinMeta.getStateId();
		log.info("Executing fork-join state: {}", forkStateId);

		ForkJoinStateInstance forkInstance = new ForkJoinStateInstance(forkJoinMeta);
		instance.getExecutionData().setCurrentState(forkInstance);

		List<CompletableFuture<Void>> syncFutures = new ArrayList<>();

		for (BaseFlowNode childMeta : forkJoinMeta.getChildStates()) {
			String childStateId = childMeta.getStateId();

			if (childMeta instanceof UserTaskNode userTask) {
				// UserTask: store in pending, notify actors
				BaseFlowNodeInstance childInstance = BaseFlowNodeInstance.createInstance(childMeta);
				forkInstance.addChildExecutionState(childStateId, childInstance);
				log.info("Fork-join child {} awaiting user input", childStateId);
				notifyActorsForUserTask(instance, userTask);
			} else if (childMeta.isPausable()) {
				// Pausable: execute and store instance
				BaseFlowNodeInstance childInstance = BaseFlowNodeInstance.createInstance(childMeta);
				childInstance.execute(instance.getContext());
				forkInstance.addChildExecutionState(childStateId, childInstance);
				log.info("Fork-join child {} paused", childStateId);
			} else {
				// Sync: execute in parallel via thread pool
				CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
					BaseFlowNodeInstance childInstance = BaseFlowNodeInstance.createInstance(childMeta);
					String result = childInstance.execute(instance.getContext());
					forkInstance.addChildResult(childStateId, result);
					log.info("Fork-join child {} completed with result: {}", childStateId, result);
				}, threadPool);
				syncFutures.add(future);
			}
		}

		// Wait for all sync futures to complete
		if (!syncFutures.isEmpty()) {
			CompletableFuture.allOf(syncFutures.toArray(new CompletableFuture[0])).join();
		}

		// Check if all children completed (only sync states, no pending)
		if (forkInstance.isAllChildrenCompleted()) {
			completeForkJoin(instance, forkInstance);
		} else {
			// Has pending children - pause the flow
			instance.pause();
			log.info("Fork-join {} waiting for {} pending children", forkStateId,
					forkInstance.getChildExecutionStates().size());
		}
	}

	/**
	 * Complete a fork-join state - call merge function and continue.
	 */
	private void completeForkJoin(FlowInstance instance, ForkJoinStateInstance forkInstance) {
		ForkJoinNode forkJoinMeta = forkInstance.getForkJoinMetadata();
		String forkStateId = forkJoinMeta.getStateId();

		log.info("Fork-join {} all children completed, calling merge function", forkStateId);

		// Call merge function
		String nextStateId = null;
		if (forkJoinMeta.getMergeFunction() != null) {
			nextStateId = forkJoinMeta.getMergeFunction().apply(instance.getContext(), forkInstance.getChildResults());
		}

		// Continue flow
		if (nextStateId != null && !forkJoinMeta.isTerminal()) {
			instance.getExecutionData().setFlowStatus(FlowStatus.RUNNING);
			instance.transitionTo(nextStateId);
			executeFlowLoop(instance);
		} else {
			instance.complete();
			log.info("Flow completed after fork-join: {}", forkStateId);
		}
	}

	/**
	 * Resume a paused child within a fork-join state.
	 */
	public void resumeForkChild(String instanceId, String childStateId) {
		FlowInstance instance = getCurrentRunningFlow(instanceId);
		if (instance == null) {
			throw new IllegalStateException("Flow instance not found: " + instanceId);
		}

		BaseFlowNodeInstance currentState = instance.getCurrentState();
		if (!(currentState instanceof ForkJoinStateInstance forkInstance)) {
			throw new IllegalStateException("Current state is not a fork-join");
		}

		FlowStateInstance childInstance = forkInstance.getChildExecutionState(childStateId);
		if (childInstance == null) {
			throw new IllegalStateException("Child state not found: " + childStateId);
		}

		log.info("Resuming fork-join child: {}", childStateId);

		// Resume and store result
		String result = childInstance.resume(instance.getContext());
		forkInstance.addChildResult(childStateId, result);

		// Check if all completed
		if (forkInstance.isAllChildrenCompleted()) {
			completeForkJoin(instance, forkInstance);
		}
	}

	/**
	 * Submit user response for a user task within a fork-join state.
	 */
	public void submitForkUserResponse(String instanceId, String childStateId, Map<String, Object> response) {
		FlowInstance instance = getCurrentRunningFlow(instanceId);
		if (instance == null) {
			throw new IllegalStateException("Flow instance not found: " + instanceId);
		}

		BaseFlowNodeInstance currentState = instance.getCurrentState();
		if (!(currentState instanceof ForkJoinStateInstance forkInstance)) {
			throw new IllegalStateException("Current state is not a fork-join");
		}

		FlowStateInstance childInstance = forkInstance.getChildExecutionState(childStateId);
		if (childInstance == null) {
			throw new IllegalStateException("Child state not found: " + childStateId);
		}

		BaseFlowNode childMeta = childInstance.getMetadata();
		if (!(childMeta instanceof UserTaskNode userTask)) {
			throw new IllegalStateException("Child state is not a user task: " + childStateId);
		}

		String result = processUserTaskResponse(instance, userTask, response);
		forkInstance.addChildResult(childStateId, result);

		// Check if all completed
		if (forkInstance.isAllChildrenCompleted()) {
			completeForkJoin(instance, forkInstance);
		}
	}

	/**
	 * Submit user response for a flow waiting for input.
	 *
	 * @param instanceId the flow instance ID
	 * @param response   the user response data
	 * @throws IllegalStateException    if flow not found or not awaiting input
	 * @throws IllegalArgumentException if response validation fails
	 */
	public void resumeWithInput(String instanceId, FlowResuptionInput input) {
		FlowInstance instance = getCurrentRunningFlow(instanceId);
		if (instance == null) {
			throw new IllegalStateException("Flow instance not found: " + instanceId);
		}

		// the flow can be resumed only in certain conditions

		BaseFlowNode metadata = instance.getCurrentState().getMetadata();

		if (metadata instanceof UserTaskNode) {

			String nextStateId = processUserTaskResponse(instance, (UserTaskNode) metadata, input);
		} else if (metadata instanceof SubflowNode) {

		} else if (instance.getCurrentState() instanceof ForkJoinStateInstance) {

		}
		if (!(metadata instanceof UserTaskNode userTask)) {
			throw new IllegalStateException("Current state is not a user task");
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
	 * Process user task response - validate, store, and execute handler. Common
	 * method used by both regular user tasks and fork-join user tasks.
	 */
	private String processUserTaskResponse(FlowInstance instance, UserTaskNode userTask,
			Map<String, Object> response) {
		// Validate response
		UserTaskNode.ValidationResult validation = userTask.validateResponse(response);
		if (!validation.isValid()) {
			throw new IllegalArgumentException("Invalid response: " + validation.getErrorMessage());
		}

		log.info("User response received for state: {}", userTask.getStateId());

		// Store response in context
		instance.getContext().put("_userResponse_" + userTask.getStateId(), response);

		// Execute the onResponse handler
		if (userTask.getOnResponse() != null) {
			return userTask.getOnResponse().apply(instance.getContext(), response);
		}
		return null;
	}

	/**
	 * Notify actors for a user task.
	 */
	private void notifyActorsForUserTask(FlowInstance instance, UserTaskNode userTask) {
		if (actorNotifier != null && userTask.getEligibleRoles() != null) {
			List<Actor> eligibleActors = instance.getActorsByRoles(userTask.getEligibleRoles());
			if (!eligibleActors.isEmpty()) {
				actorNotifier.notifyActors(eligibleActors, userTask, instance);
				log.info("Notified {} actors for task: {}", eligibleActors.size(), userTask.getStateId());
			}
		}
	}

	/**
	 * Get the pending user task for a flow awaiting input.
	 *
	 * @param instanceId the flow instance ID
	 * @return the UserTaskState, or null if not awaiting input
	 */
	public UserTaskNode getPendingUserTask(String instanceId) {
		FlowInstance instance = getCurrentRunningFlow(instanceId);
		if (instance == null || !instance.isAwaitingUserInput()) {
			return null;
		}

		BaseFlowNode metadata = instance.getCurrentState().getMetadata();
		return metadata instanceof UserTaskNode ? (UserTaskNode) metadata : null;
	}

	/**
	 * Hook to determine if flow should pause at current state. Can be overridden to
	 * implement custom pause logic.
	 *
	 * @param instance the flow instance
	 * @return true if flow should pause
	 */
	protected boolean shouldPause(FlowInstance instance) {
		// Default: don't auto-pause, require explicit pause call
		return false;
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
