package com.cbosgroup.cbos.core.flows.runtime;

import com.cbosgroup.cbos.core.flows.FlowContext;
import com.cbosgroup.cbos.core.flows.FlowMetadata;
import com.cbosgroup.cbos.core.flows.FlowNodeCapabilities;
import com.cbosgroup.cbos.core.flows.SubflowNode;

/**
 * Runtime instance of a SubflowNode. Manages the lifecycle of executing a
 * subflow within a parent flow.
 */
public class SubflowStateInstance extends BaseFlowNodeInstance {

	private SubflowInstance subflowInstance;

	public SubflowStateInstance(SubflowNode metadata) {
		super(metadata);
	}

	@Override
	protected String doExecute(FlowContext context, FlowNodeCapabilities capabilities) {
		SubflowNode meta = (SubflowNode) getMetadata();
		FlowMetadata subflowMeta = capabilities.resolveFlow(meta.getFlowId());
		subflowInstance = new SubflowInstance(subflowMeta, context);
		subflowInstance.initialize();
		capabilities.executeFlow(subflowInstance);
		return evaluateSubflowResult();
	}

	@Override
	protected String doResume(FlowContext context, FlowNodeCapabilities capabilities) {
		subflowInstance.getFlowContext().setInput(context.getInput());
		capabilities.resumeFlow(subflowInstance, subflowInstance.getFlowContext());
		return evaluateSubflowResult();
	}

	private String evaluateSubflowResult() {
		if (subflowInstance.isPaused() || subflowInstance.isAwaitingUserInput()) {
			status = Status.PAUSED;
			return null;
		}
		if (subflowInstance.isCompleted()) {
			return subflowInstance.getExecutionData().getLastTransitionPointer(); // the subflow is complete return the
																					// result
		}
		throw new IllegalStateException("Subflow execution returned with state neither complete or paused");

	}
}
