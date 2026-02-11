package com.cbosgroup.cbos.core.flows;

import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * A state that encapsulates a substate in it 
 */
@Data
@SuperBuilder
public class SubflowNode extends BaseFlowNode {

	/**
	 * The id of the flow in FlowRepositiry
	 */
	private String flowId; 
}
