package com.cbosgroup.cbos.core.flows;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * The flows are modelled as step-by-step process of gathering documents
 */
@Data
@Builder
public class FlowMetadata {

    private String flowId;

    private String flowName;

    private String description;

    /**
     * All states in this flow, keyed by stateId
     */
    private Map<String, BaseFlowNode> states;

    /**
     * The ID of the starting state
     */
    private String startStateId;

    /**
     * Get the start state metadata
     */
    public BaseFlowNode getStartState() {
        return states != null ? states.get(startStateId) : null;
    }

    /**
     * Get a state by its ID
     */
    public BaseFlowNode getState(String stateId) {
        return states != null ? states.get(stateId) : null;
    }
}
