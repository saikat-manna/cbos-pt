package com.cbosgroup.cbos.core.flows.runtime;

import com.cbosgroup.cbos.core.flows.FlowStateMetadata;
import lombok.Data;

import java.util.Map;

/**
 * Runtime instance of a flow state
 */
@Data
public class FlowStateInstance {

    /**
     * Reference to metadata
     */
    private FlowStateMetadata metadata;

    /**
     * Whether this state instance has been executed
     */
    private boolean executed;

    /**
     * Result of the last execution (next state ID or null)
     */
    private String lastResult;

    public FlowStateInstance(FlowStateMetadata metadata) {
        this.metadata = metadata;
        this.executed = false;
    }

    /**
     * Executes the logic held in current state
     * @param context the execution context
     * @return next state ID, or null if terminal
     */
    public String execute(Map<String, Object> context) {
        if (metadata.getAction() != null) {
            lastResult = metadata.getAction().apply(context);
        } else {
            lastResult = null;
        }
        executed = true;
        return lastResult;
    }

    /**
     * Resume a paused state
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
