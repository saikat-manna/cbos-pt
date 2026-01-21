package com.cbosgroup.cbos.core.flows;

import lombok.Builder;
import lombok.Data;
import java.util.Map;
import java.util.function.Function;

/**
 * Metadata defining a single state within a flow
 */
@Data
@Builder
public class FlowStateMetadata {

    private String stateId;

    private String stateName;

    private String description;

    /**
     * The action to execute when this state runs.
     * Takes context map as input, returns next state ID (or null if terminal)
     */
    private Function<Map<String, Object>, String> action;

    /**
     * Whether this state can be paused
     */
    private boolean pausable;

    /**
     * Whether this is a terminal/end state
     */
    private boolean terminal;
}
