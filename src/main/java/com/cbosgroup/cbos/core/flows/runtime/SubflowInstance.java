package com.cbosgroup.cbos.core.flows.runtime;

import com.cbosgroup.cbos.core.flows.FlowContext;
import com.cbosgroup.cbos.core.flows.FlowMetadata;

public class SubflowInstance extends FlowInstance {

	public SubflowInstance(FlowMetadata flowMeta, FlowContext parentContext) {
		super(flowMeta, parentContext.getVersion());
		this.getFlowContext().putAll(parentContext);
		this.getFlowContext().setVersion(parentContext.getVersion());
	}
}
