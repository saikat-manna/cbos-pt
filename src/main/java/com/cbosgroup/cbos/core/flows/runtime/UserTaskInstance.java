package com.cbosgroup.cbos.core.flows.runtime;

import java.util.Map;
import java.util.function.BiFunction;

import com.cbosgroup.cbos.core.flows.FlowContext;
import com.cbosgroup.cbos.core.flows.FlowNodeCapabilities;
import com.cbosgroup.cbos.core.flows.UserTaskNode;

public class UserTaskInstance extends BaseFlowNodeInstance {

	private Map<String, Object> userInput;

	private BiFunction<FlowContext, Map<String, Object>, Boolean> userInputExtractor;
	private BiFunction<FlowContext, Map<String, Object>, String> onUserInputeceived;

	public UserTaskInstance(UserTaskNode metadata) {
		super(metadata);
	}

	@Override
	protected String doResume(FlowContext context, FlowNodeCapabilities capabilities) {
		return executeOrPause(context);
	}

	private String executeOrPause(FlowContext context) {
		if (userInputExtractor.apply(context, userInput)) {
			String returnVal = onUserInputeceived.apply(context, userInput);
			status = Status.COMPLETED;
			return returnVal;
		}
		status = Status.PAUSED;
		return null;
	}

	@Override
	protected String doExecute(FlowContext context, FlowNodeCapabilities capabilities) {
		return executeOrPause(context);
	}
}
