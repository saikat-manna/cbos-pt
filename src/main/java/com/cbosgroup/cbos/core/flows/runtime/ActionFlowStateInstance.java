package com.cbosgroup.cbos.core.flows.runtime;

import com.cbosgroup.cbos.core.flows.ActionFlowNode;
import com.cbosgroup.cbos.core.flows.FlowContext;
import com.cbosgroup.cbos.core.flows.FlowNodeCapabilities;

public class ActionFlowStateInstance extends BaseFlowNodeInstance {

	public ActionFlowStateInstance(ActionFlowNode metadata) {
		super(metadata);
	}

	@Override
	protected String doResume(FlowContext context, FlowNodeCapabilities capabilities) {
		throw new UnsupportedOperationException("Action states can not be resumed");
	}

	@Override
	protected String doExecute(FlowContext context, FlowNodeCapabilities capabilities) {
		return ((ActionFlowNode) getMetadata()).getAction().apply(context);
	}
}
