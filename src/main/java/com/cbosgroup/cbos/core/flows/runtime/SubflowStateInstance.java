package com.cbosgroup.cbos.core.flows.runtime;

import com.cbosgroup.cbos.core.flows.FlowContext;
import com.cbosgroup.cbos.core.flows.SubflowNode;

/**
 * Runtime instance of a SubflowNode. Manages the lifecycle of executing
 * a subflow within a parent flow.
 */
public class SubflowStateInstance extends BaseFlowNodeInstance {

	public SubflowStateInstance(SubflowNode metadata) {
		super(metadata);
	}

	public SubflowNode getSubflowMetadata() {
		return (SubflowNode) getMetadata();
	}

	@Override
	protected String doExecute(FlowContext context) {
		// TODO: subflow execution handled by FlowExecuter for now
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	protected String doResume(FlowContext context) {
		// TODO: resume after subflow completion
		throw new UnsupportedOperationException("Not yet implemented");
	}
}
