package com.cbosgroup.cbos.core.flows;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.cbosgroup.cbos.core.Version;
import com.cbosgroup.cbos.core.flows.FlowExecutionStateData.FlowStatus;
import com.cbosgroup.cbos.core.flows.repository.PrebuiltFlowsResposirty;
import com.cbosgroup.cbos.core.flows.runtime.BaseFlowNodeInstance;
import com.cbosgroup.cbos.core.flows.runtime.FlowInstance;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class FlowExecuter {

	@NonNull
	private PrebuiltFlowsResposirty flowResposirty;

	private final Map<String, ConcurrentLinkedDeque<FlowInstance>> activeFlows = new ConcurrentHashMap<>();

	@Setter
	private ActorNotifier actorNotifier;

	// ── Public API ──────────────────────────────────────────────

	public String runFlow(FlowMetadata template, Version version) {
		FlowInstance instance = new FlowInstance(template, version);
		instance.getFlowContext().setActorNotifier(actorNotifier);
		putCurrentRunningFlow(instance);
		log.info("Starting flow '{}' with instance ID: {}", template.getFlowName(), instance.getInstanceId());
		instance.initialize();
		executeFlowLoop(instance);
		return instance.getInstanceId();
	}

	public void resumeWithInput(String instanceId, FlowResuptionInput input) {
		FlowInstance instance = getActiveFlow(instanceId);
		if (!instance.isPaused() && !instance.isAwaitingUserInput()) {
			throw new IllegalStateException(
					"Flow cannot be resumed. Current status: " + instance.getExecutionData().getFlowStatus());
		}

		if (input != null) {
			instance.getFlowContext().setInput(input);
		}
		instance.getExecutionData().setFlowStatus(FlowStatus.RUNNING);

		BaseFlowNodeInstance currentState = instance.getCurrentState();
		String nextStateId = currentState.resume(instance.getFlowContext());
		handleExecutionResult(instance, currentState, nextStateId);

		if (instance.isRunning()) {
			executeFlowLoop(instance);
		}
	}

	public void pauseFlow(String instanceId) {
		FlowInstance instance = getActiveFlow(instanceId);
		if (instance.isRunning() && instance.getCurrentState().isPausable()) {
			instance.pause();
			log.info("Flow paused at state: {}", instance.getExecutionData().getCurrentStateId());
		} else {
			throw new IllegalStateException("Flow cannot be paused in current state");
		}
	}

	public int getActiveFlowCount() {
		return activeFlows.size();
	}

	// ── Core loop ───────────────────────────────────────────────

	private void executeFlowLoop(FlowInstance instance) {
		while (instance.isRunning()) {
			BaseFlowNodeInstance currentState = instance.getCurrentState();
			log.debug("Executing state: {} ({})",
					currentState.getMetadata().getStateId(), currentState.getMetadata().getStateName());

			if (currentState.isPausable() && shouldPause(instance)) {
				instance.pause();
				return;
			}

			String nextStateId = currentState.execute(instance.getFlowContext());
			handleExecutionResult(instance, currentState, nextStateId);

			if (!instance.isRunning()) return;
		}
	}

	private void handleExecutionResult(FlowInstance instance, BaseFlowNodeInstance state, String nextStateId) {
		if (nextStateId == null) {
			if (state.isTerminal()) {
				instance.complete();
				log.info("Flow completed at: {}", state.getMetadata().getStateId());
			} else {
				instance.pause();
				log.info("Flow paused at: {}", state.getMetadata().getStateId());
			}
			return;
		}
		instance.transitionTo(nextStateId);
	}

	// ── Registry ────────────────────────────────────────────────

	private FlowInstance getActiveFlow(String instanceId) {
		var flowStack = activeFlows.get(instanceId);
		if (flowStack == null || flowStack.isEmpty()) {
			throw new IllegalStateException("Flow instance not found: " + instanceId);
		}
		return flowStack.peek();
	}

	private void putCurrentRunningFlow(FlowInstance instance) {
		activeFlows.computeIfAbsent(instance.getInstanceId(), k -> new ConcurrentLinkedDeque<>())
				.push(instance);
	}

	protected boolean shouldPause(FlowInstance instance) {
		return false;
	}
}
