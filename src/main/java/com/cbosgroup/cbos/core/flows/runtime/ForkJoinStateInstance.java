package com.cbosgroup.cbos.core.flows.runtime;

import com.cbosgroup.cbos.core.flows.BaseFlowNode;
import com.cbosgroup.cbos.core.flows.FlowContext;
import com.cbosgroup.cbos.core.flows.ForkJoinNode;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime instance of a ForkJoinNode. Owns all fork-join execution logic:
 * parallel dispatch, child tracking, merge on completion.
 */
@Slf4j
public class ForkJoinStateInstance extends BaseFlowNodeInstance {

	@Getter
	private final Map<String, BaseFlowNodeInstance> childExecutionStates = new ConcurrentHashMap<>();

	@Getter
	private final Map<String, Object> childResults = new ConcurrentHashMap<>();

	public ForkJoinStateInstance(ForkJoinNode metadata) {
		super(metadata);
	}

	public ForkJoinNode getForkJoinMetadata() {
		return (ForkJoinNode) getMetadata();
	}

	public void addChildExecutionState(String stateId, BaseFlowNodeInstance instance) {
		childExecutionStates.put(stateId, instance);
	}

	public BaseFlowNodeInstance getChildExecutionState(String stateId) {
		return childExecutionStates.get(stateId);
	}

	public void addChildResult(String stateId, Object result) {
		childResults.put(stateId, result);
		childExecutionStates.remove(stateId);
	}

	public boolean isAllChildrenCompleted() {
		return childResults.size() == getForkJoinMetadata().getChildCount();
	}

	public boolean hasPendingChildren() {
		return !childExecutionStates.isEmpty();
	}

	/**
	 * Resume a specific child within this fork-join.
	 */
	public String resumeChild(String childStateId, FlowContext context) {
		BaseFlowNodeInstance childInstance = getChildExecutionState(childStateId);
		if (childInstance == null) {
			throw new IllegalStateException("Child state not found or already completed: " + childStateId);
		}
		String result = childInstance.resume(context);
		if (childInstance.status == Status.COMPLETED) {
			addChildResult(childStateId, result);
		}
		return result;
	}

	@Override
	protected String doExecute(FlowContext context) {
		ForkJoinNode forkJoinMeta = getForkJoinMetadata();
		String forkStateId = forkJoinMeta.getStateId();
		log.info("Executing fork-join state: {}", forkStateId);

		status = Status.RUNNING;
		List<CompletableFuture<Void>> syncFutures = new ArrayList<>();

		for (BaseFlowNode childMeta : forkJoinMeta.getChildStates()) {
			String childStateId = childMeta.getStateId();
			BaseFlowNodeInstance childInstance = BaseFlowNodeInstance.createInstance(childMeta);

			if (childMeta.isPausable()) {
				// Pausable (includes UserTask): execute — node sets its own status
				String result = childInstance.execute(context);
				if (childInstance.status == Status.PAUSED) {
					addChildExecutionState(childStateId, childInstance);
					log.info("Fork-join child {} paused", childStateId);
				} else {
					addChildResult(childStateId, result);
					log.info("Fork-join child {} completed with result: {}", childStateId, result);
				}
			} else {
				// Non-pausable: execute in parallel
				CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
					String result = childInstance.execute(context);
					addChildResult(childStateId, result);
					log.info("Fork-join child {} completed with result: {}", childStateId, result);
				});
				syncFutures.add(future);
			}
		}

		if (!syncFutures.isEmpty()) {
			CompletableFuture.allOf(syncFutures.toArray(new CompletableFuture[0])).join();
		}

		if (isAllChildrenCompleted()) {
			return mergeAndComplete(context);
		}

		status = Status.PAUSED;
		log.info("Fork-join {} waiting for {} pending children", forkStateId, childExecutionStates.size());
		return null;
	}

	@Override
	protected String doResume(FlowContext context) {
		if (isAllChildrenCompleted()) {
			return mergeAndComplete(context);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private String mergeAndComplete(FlowContext context) {
		ForkJoinNode meta = getForkJoinMetadata();
		log.info("Fork-join {} all children completed, merging", meta.getStateId());
		status = Status.COMPLETED;
		if (meta.getMergeFunction() != null) {
			return meta.getMergeFunction().apply((Map) context, childResults);
		}
		return null;
	}
}
