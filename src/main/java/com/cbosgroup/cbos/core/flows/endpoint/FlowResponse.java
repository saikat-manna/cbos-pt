package com.cbosgroup.cbos.core.flows.endpoint;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlowResponse {

	private String instanceId;

	private FlowEndpointStatus status;

	private String message;

	/**
	 * Non-null only when status == AWAITING_USER_INPUT.
	 * Describes what input the caller needs to collect.
	 */
	private PendingUserInput pendingInput;
}
