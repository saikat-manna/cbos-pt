package com.cbosgroup.cbos.core.flows.runtime;

import com.cbosgroup.cbos.core.flows.FlowMetadata;

public class SubflowInstance extends FlowInstance {
	

	public SubflowInstance(FlowMetadata flowMeta, FlowInstance parentInstance) {
		super(flowMeta, parentInstance.getVersion());
		// copy the context
		this.getContext().putAll(parentInstance.getContext());
		this.instanceId = parentInstance.getInstanceId() + "/+" + this.getInstanceId();
		this.rootInstanceId = parentInstance.rootInstanceId;
	}

}
