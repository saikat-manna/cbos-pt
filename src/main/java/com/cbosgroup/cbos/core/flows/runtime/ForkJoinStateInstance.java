package com.cbosgroup.cbos.core.flows.runtime;

import com.cbosgroup.cbos.core.flows.BaseFlowNode;
import com.cbosgroup.cbos.core.flows.FlowContext;
import com.cbosgroup.cbos.core.flows.FlowStateInstance;
import com.cbosgroup.cbos.core.flows.ForkJoinNode;
import com.cbosgroup.cbos.core.flows.UserTaskNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Runtime instance of a ForkJoinState. Tracks child execution states and
 * results.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class ForkJoinStateInstance extends BaseFlowNodeInstance {

	/**
	 * Execution states for pausable/user task children, keyed by state ID
	 */
	private final Map<String, BaseFlowNodeInstance> childExecutionStates = new HashMap<>();

	/**
	 * Results from completed child states, keyed by state ID
	 */
	private final Map<String, Object> childResults = new HashMap<>();

	public ForkJoinStateInstance(ForkJoinNode metadata) {
		super(metadata);
	}

	/**
	 * Store a child's execution state
	 */
	public void addChildExecutionState(String stateId, BaseFlowNodeInstance instance) {
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

	@Override
	protected String doResume(FlowContext context) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected String doExecute(FlowContext context) {
		String forkStateId = getMetadata().getStateId();
		log.info("Executing fork-join state: {}", forkStateId);

		// instance.getExecutionData().setCurrentState(forkInstance);

		// take out non pausable states
		var nonPauableChildren = ((ForkJoinNode) getMetadata()).getChildStates().stream().filter(a -> a.isPausable())
				.collect(Collectors.toList());
		List<CompletableFuture<Void>> syncFutures = new ArrayList<>(nonPauableChildren.size());

		for (BaseFlowNode childMeta : forkJoinMeta.getChildStates()) {
			String childStateId = childMeta.getStateId();

			if (childMeta instanceof UserTaskNode userTask) {
				// UserTask: store in pending, notify actors
				FlowStateInstance childInstance = new FlowStateInstance(childMeta);
				forkInstance.addChildExecutionState(childStateId, childInstance);
				log.info("Fork-join child {} awaiting user input", childStateId);
				notifyActorsForUserTask(instance, userTask);
			} else if (childMeta.isPausable()) {
				// Pausable: execute and store instance
				FlowStateInstance childInstance = new FlowStateInstance(childMeta);
				childInstance.execute(instance.getContext());
				forkInstance.addChildExecutionState(childStateId, childInstance);
				log.info("Fork-join child {} paused", childStateId);
			} else {
				// Sync: execute in parallel via thread pool
				CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
					FlowStateInstance childInstance = new FlowStateInstance(childMeta);
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
}
