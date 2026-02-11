package com.cbosgroup.cbos.core.flows.endpoint;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlowInfo {

	private String flowName;

	private String description;
}
