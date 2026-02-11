package com.cbosgroup.cbos.core.flows.endpoint;

import java.util.List;
import java.util.Map;

/**
 * Endpoint contract for flow execution.
 * All user input arrives as raw text (Map&lt;String, String&gt;);
 * implementations handle marshalling to typed values based on InputField.FieldType.
 */
public interface FlowEndpoint {

	FlowResponse startFlow(StartFlowRequest request);

	FlowResponse submitInput(String instanceId, Map<String, String> userResponse);

	FlowResponse pauseFlow(String instanceId);

	FlowResponse getStatus(String instanceId);

	List<FlowInfo> listAvailableFlows();
}
