package com.cbosgroup.cbos.core.flows;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.util.Map;
import java.util.function.Function;

/**
 * Metadata defining a single state within a flow. Base class for all state
 * types including UserTaskState.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BaseFlowNode {

	private String stateId;

	private String stateName;

	private String description;

	/**
	 * Whether this state can be paused
	 */
	protected boolean pausable;

	/**
	 * Whether this is a terminal/end state
	 */
	private boolean terminal;
}
