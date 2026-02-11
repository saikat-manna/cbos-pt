package com.cbosgroup.cbos.core.flows;

import java.util.List;

/**
 * Repository for resolving flow metadata by name/ID.
 */
public interface FlowRepository {

	FlowMetadata getFlowByName(String name);

	List<String> getAllFlowNames();
}
