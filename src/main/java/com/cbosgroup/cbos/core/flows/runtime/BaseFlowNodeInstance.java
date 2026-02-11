package com.cbosgroup.cbos.core.flows.runtime;

import java.util.Map;

import com.cbosgroup.cbos.core.flows.BaseFlowNode;
import com.cbosgroup.cbos.core.flows.FlowContext;

import lombok.Getter;

/**
 * Runtime instance of a flow state
 */
public abstract class BaseFlowNodeInstance {

	public enum Status {
		CREATED, RUNNING, PAUSED, COMPLETED;
	}

	/**
	 * Reference to metadata
	 */
	@Getter
	private BaseFlowNode metadata;

	/**
	 * Whether this state instance has been executed
	 */
	protected Status status;

	/**
	 * Result of the last execution (next state ID or null)
	 */
	private String lastResult;

	public BaseFlowNodeInstance(BaseFlowNode metadata) {
		this.metadata = metadata;
		this.status = Status.CREATED;
	}

	/**
	 * Executes the logic held in current state
	 * 
	 * @param context the execution context
	 * @return next state ID, or null if terminal
	 */
	public String execute(FlowContext context) {
		return doExecute(context);
	}

	protected String runOrResume(FlowContext context) {
		if (status == Status.COMPLETED)
			throw new IllegalStateException("Flow node has completed");
		if (status == Status.CREATED) {
			return doExecute(context);
		} else if (status == Status.PAUSED) {
			// get additional inputs
			String returnVal = doResume(context);
			context.setInput(null);
			return returnVal;
		} else {
			throw new IllegalStateException("The state is already running");
		}
	}

	protected abstract String doResume(FlowContext context);

	protected abstract String doExecute(FlowContext context);

	/**
	 * Resume a paused state
	 * 
	 * @param context the execution context
	 * @return next state ID, or null if terminal
	 */
	public String resume(Map<String, Object> context) {
		return execute(context);
	}

	/**
	 * Check if this is a terminal state
	 */
	public boolean isTerminal() {
		return metadata.isTerminal();
	}

	/**
	 * Check if this state can be paused
	 */
	public boolean isPausable() {
		return metadata.isPausable();
	}
}
