package com.cbosgroup.cbos.core.flows.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cbosgroup.cbos.core.flows.FlowMetadata;
import com.cbosgroup.cbos.core.flows.FlowRepository;

/**
 * Simple in-memory implementation of FlowRepository.
 */
public class InMemoryFlowRepository implements FlowRepository {

	private final Map<String, FlowMetadata> flows = new HashMap<>();

	public void registerFlow(FlowMetadata flow) {
		flows.put(flow.getFlowName(), flow);
	}

	@Override
	public FlowMetadata getFlowByName(String name) {
		return flows.get(name);
	}

	@Override
	public List<String> getAllFlowNames() {
		return new ArrayList<>(flows.keySet());
	}
}
