package com.cbosgroup.cbos.core.flows;

import java.util.concurrent.ConcurrentHashMap;

import com.cbosgroup.cbos.core.Version;

import lombok.Getter;
import lombok.Setter;

public class FlowContext extends ConcurrentHashMap<String, String> {

	@Getter
	@Setter
	private FlowResuptionInput input;

	@Getter
	@Setter
	private Version version;
}
