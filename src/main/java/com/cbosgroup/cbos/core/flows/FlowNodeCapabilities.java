package com.cbosgroup.cbos.core.flows;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.cbosgroup.cbos.core.actors.Actor;
import com.cbosgroup.cbos.core.flows.runtime.FlowInstance;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FlowNodeCapabilities {

	private final ActorNotifier actorNotifier;
	private final FlowRepository flowRepository;
	private final Consumer<FlowInstance> flowExecutor;
	private final BiConsumer<FlowInstance, FlowContext> flowResumer;

	public FlowMetadata resolveFlow(String flowId) {
		FlowMetadata meta = flowRepository.getFlowByName(flowId);
		if (meta == null) {
			throw new IllegalArgumentException("No flow found: " + flowId);
		}
		return meta;
	}

	public void executeFlow(FlowInstance instance) {
		flowExecutor.accept(instance);
	}

	public void resumeFlow(FlowInstance instance, FlowContext context) {
		flowResumer.accept(instance, context);
	}

	public void notifyActors(List<Actor> actors, UserTaskNode task) {
		if (actorNotifier != null && actors != null && !actors.isEmpty()) {
			actorNotifier.notifyActors(actors, task);
		}
	}
}
