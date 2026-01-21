package com.cbosgroup.cbos.core.flows.runtime;

import lombok.Data;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the execution history of a flow
 */
@Data
public class FlowExecutionHistory {

    private List<HistoryEntry> entries = new ArrayList<>();

    public void addEntry(String stateId, String status, String message) {
        entries.add(new HistoryEntry(stateId, status, message, Instant.now()));
    }

    @Data
    public static class HistoryEntry {
        private final String stateId;
        private final String status;
        private final String message;
        private final Instant timestamp;
    }
}
