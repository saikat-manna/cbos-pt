package com.cbosgroup.cbos.core.flows.endpoint;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.cbosgroup.cbos.core.flows.InputField;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PendingUserInput {

	private List<InputField> expectedInputs;

	private Set<String> eligibleRoles;

	private String taskDescription;

	/**
	 * null = wait indefinitely
	 */
	private Duration timeout;
}
