package com.cbosgroup.cbos.core.flows;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.cbosgroup.cbos.core.Version;
import com.cbosgroup.cbos.core.flows.FlowExecutionStateData.FlowStatus;
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
	private FlowRepository flowRepository;

	private final Map<String, ConcurrentLinkedDeque<FlowInstance>> activeFlows = new ConcurrentHashMap<>();

	@Setter
	private ActorNotifier actorNotifier;

	private FlowNodeCapabilities capabilities;

	private FlowNodeCapabilities getCapabilities() {
		if (capabilities == null) {
			capabilities = new FlowNodeCapabilities(actorNotifier, flowRepository, this::executeFlowLoop,
					this::resumeFlowInstance);
		}
		return capabilities;
	}

	// ── Public API ──────────────────────────────────────────────

	public String runFlow(FlowMetadata template, Version version) {
		FlowInstance instance = new FlowInstance(template, version);
		instance.getFlowContext().setVersion(version);
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
		resumeFlowInstance(instance, instance.getFlowContext());
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
			log.debug("Executing state: {} ({})", currentState.getMetadata().getStateId(),
					currentState.getMetadata().getStateName());

			String nextStateId = currentState.execute(instance.getFlowContext(), getCapabilities());
			handleExecutionResult(instance, currentState, nextStateId);

			if (!instance.isRunning())
				return;
		}
	}

	private void resumeFlowInstance(FlowInstance instance, FlowContext context) {
		instance.getExecutionData().setFlowStatus(FlowStatus.RUNNING);
		BaseFlowNodeInstance currentState = instance.getCurrentState();
		String nextStateId = currentState.resume(context, getCapabilities());
		handleExecutionResult(instance, currentState, nextStateId);

		if (instance.isRunning()) {
			executeFlowLoop(instance);
		}
	}

	private void handleExecutionResult(FlowInstance instance, BaseFlowNodeInstance state, String nextStateId) {

		if (instance.getCurrentState().isPaused()) {
			instance.pause();
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
		activeFlows.computeIfAbsent(instance.getInstanceId(), k -> new ConcurrentLinkedDeque<>()).push(instance);
	}

}
