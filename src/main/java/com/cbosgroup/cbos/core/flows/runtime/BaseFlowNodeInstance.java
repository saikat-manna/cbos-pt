package com.cbosgroup.cbos.core.flows.runtime;

import com.cbosgroup.cbos.core.flows.ActionFlowNode;
import com.cbosgroup.cbos.core.flows.BaseFlowNode;
import com.cbosgroup.cbos.core.flows.FlowContext;
import com.cbosgroup.cbos.core.flows.FlowNodeCapabilities;
import com.cbosgroup.cbos.core.flows.ForkJoinNode;
import com.cbosgroup.cbos.core.flows.SubflowNode;
import com.cbosgroup.cbos.core.flows.UserTaskNode;

import lombok.Getter;

/**
 * Runtime instance of a flow state
 */
public abstract class BaseFlowNodeInstance {

	public enum Status {
		CREATED, RUNNING, PAUSED, COMPLETED;
	}

	@Getter
	private BaseFlowNode metadata;

	protected Status status;

	private String lastResult;

	public BaseFlowNodeInstance(BaseFlowNode metadata) {
		this.metadata = metadata;
		this.status = Status.CREATED;
	}

	/**
	 * Factory method — creates the correct instance subtype from metadata.
	 */
	public static BaseFlowNodeInstance createInstance(BaseFlowNode metadata) {
		if (metadata instanceof ActionFlowNode actionNode) {
			return new ActionFlowStateInstance(actionNode);
		} else if (metadata instanceof UserTaskNode userTaskNode) {
			return new UserTaskInstance(userTaskNode);
		} else if (metadata instanceof ForkJoinNode forkJoinNode) {
			return new ForkJoinStateInstance(forkJoinNode);
		} else if (metadata instanceof SubflowNode subflowNode) {
			return new SubflowStateInstance(subflowNode);
		} else {
			throw new IllegalArgumentException("Unknown node type: " + metadata.getClass().getName());
		}
	}

	public String execute(FlowContext context, FlowNodeCapabilities capabilities) {
		return doExecute(context, capabilities);
	}

	public String resume(FlowContext context, FlowNodeCapabilities capabilities) {
		return doResume(context, capabilities);
	}

	protected String runOrResume(FlowContext context, FlowNodeCapabilities capabilities) {
		if (status == Status.COMPLETED)
			throw new IllegalStateException("Flow node has completed");
		if (status == Status.CREATED) {
			return doExecute(context, capabilities);
		} else if (status == Status.PAUSED) {
			String returnVal = doResume(context, capabilities);
			context.setInput(null);
			return returnVal;
		} else {
			throw new IllegalStateException("The state is already running");
		}
	}

	protected abstract String doResume(FlowContext context, FlowNodeCapabilities capabilities);

	protected abstract String doExecute(FlowContext context, FlowNodeCapabilities capabilities);

	public boolean isTerminal() {
		return metadata.isTerminal();
	}

	public boolean isPausable() {
		return metadata.isPausable();
	}
}
