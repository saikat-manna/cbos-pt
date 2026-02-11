package com.cbosgroup.cbos.core.flows;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Metadata for a fork-join state that executes multiple child states in parallel
 * and waits for all to complete before continuing.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ForkJoinNode extends BaseFlowNode {

    /**
     * Child states to execute in parallel
     */
    private final List<BaseFlowNode> childStates;

    /**
     * Merge function called when all child states complete.
     * Takes (context, Map<stateId, result>) and returns next state ID.
     */
    private final BiFunction<Map<String, Object>, Map<String, Object>, String> mergeFunction;

    public ForkJoinNode(String stateId, String stateName, BaseFlowNode[] childStates,
                         BiFunction<Map<String, Object>, Map<String, Object>, String> mergeFunction) {
        super();
        setStateId(stateId);
        setStateName(stateName);
        setPausable(true);
        setTerminal(false);
        this.childStates = childStates != null ? Arrays.asList(childStates) : Collections.emptyList();
        this.mergeFunction = mergeFunction;
    }

    public BaseFlowNode getChildState(String stateId) {
        return childStates.stream()
                .filter(s -> s.getStateId().equals(stateId))
                .findFirst()
                .orElse(null);
    }

    public int getChildCount() {
        return childStates.size();
    }
}
