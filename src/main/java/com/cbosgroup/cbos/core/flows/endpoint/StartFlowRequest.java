package com.cbosgroup.cbos.core.flows.endpoint;

import java.util.List;
import java.util.Map;

import com.cbosgroup.cbos.core.Version;
import com.cbosgroup.cbos.core.actors.Actor;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StartFlowRequest {

	private String flowName;

	private Version version;

	private Map<String, String> initialContext;

	private List<Actor> actors;
}
