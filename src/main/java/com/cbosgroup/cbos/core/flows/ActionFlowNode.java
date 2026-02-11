package com.cbosgroup.cbos.core.flows;

import java.util.Map;
import java.util.function.Function;

import lombok.Getter;

public class ActionFlowNode extends BaseFlowNode {

	/**
	 * The action to execute when this state runs. Takes context map as input,
	 * returns next state ID (or null if terminal)
	 */
	@Getter
	private Function<FlowContext, String> action;

	public ActionFlowNode() {
		super();
		pausable = false;
	}

}
