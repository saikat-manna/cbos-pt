package com.cbosgroup.cbos.core.flows;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.util.Map;
import java.util.function.Function;

/**
 * Metadata defining a single state within a flow.
 * Base class for all state types including UserTaskState.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
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
