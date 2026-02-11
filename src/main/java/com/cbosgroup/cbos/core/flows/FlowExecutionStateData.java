package com.cbosgroup.cbos.core.flows;

import lombok.Builder;
import lombok.Data;
import java.util.HashMap;
import java.util.Map;

import com.cbosgroup.cbos.core.flows.runtime.BaseFlowNodeInstance;
import com.cbosgroup.cbos.core.flows.runtime.FlowStateInstance;

/**
 * Tracks the execution state of a running flow
 */
@Data
@Builder
public class FlowExecutionStateData {

    /**
     * Context data passed between states
     */
    @Builder.Default
    private FlowContext context  = new FlowContext();

    /**
     * Current state instance being executed
     */
    private BaseFlowNodeInstance currentState;

    /**
     * Current state of the flow execution
     */
    private FlowStatus flowStatus;

    /**
     * ID of the current state in the flow
     */
    private String currentStateId;

    /**
     * Flow execution status enum
     */
    public enum FlowStatus {
        NOT_STARTED,
        RUNNING,
        PAUSED,
        AWAITING_USER_INPUT,
        COMPLETED,
        FAILED
    }
}
