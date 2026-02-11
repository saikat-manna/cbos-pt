package com.cbosgroup.cbos.core.flows;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * A flow state that pauses execution and waits for user input.
 * Extends FlowStateMetadata with user task specific fields.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserTaskNode extends BaseFlowNode {

    /**
     * List of expected input fields from user
     */
    private List<InputField> expectedInputs;

    /**
     * Roles eligible to respond to this task
     */
    private Set<String> eligibleRoles;

    /**
     * Handler called when user submits response.
     * Takes (context, userResponse) and returns next state ID.
     */
    private BiFunction<Map<String, Object>, Map<String, Object>, String> onResponse;

    /**
     * Optional timeout duration (null = wait indefinitely)
     */
    private Duration timeoutDuration;

    /**
     * State to transition to if timeout occurs (optional)
     */
    private String timeoutNextState;

    /**
     * Validate user response against expected inputs
     * @param response the user response map
     * @return validation result
     */
    public ValidationResult validateResponse(Map<String, Object> response) {
        if (expectedInputs == null || expectedInputs.isEmpty()) {
            return ValidationResult.valid();
        }

        for (InputField field : expectedInputs) {
            Object value = response.get(field.getFieldName());

            // Check required fields
            if (field.isRequired() && (value == null || value.toString().isEmpty())) {
                return ValidationResult.invalid("Required field missing: " + field.getFieldName());
            }

            // Skip further validation if value is null and not required
            if (value == null) {
                continue;
            }

            // Type validation
            if (!isValidType(value, field.getFieldType())) {
                return ValidationResult.invalid("Invalid type for field: " + field.getFieldName() +
                        ". Expected: " + field.getFieldType());
            }

            // Pattern validation for STRING type
            if (field.getFieldType() == InputField.FieldType.STRING &&
                    field.getValidationPattern() != null &&
                    !value.toString().matches(field.getValidationPattern())) {
                return ValidationResult.invalid("Field " + field.getFieldName() +
                        " does not match required pattern");
            }
        }

        return ValidationResult.valid();
    }

    private boolean isValidType(Object value, InputField.FieldType expectedType) {
        return switch (expectedType) {
            case STRING -> value instanceof String;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case DATE -> value instanceof String || value instanceof java.time.temporal.Temporal;
            case DOCUMENT -> true; // Document references can be various types
        };
    }

    /**
     * Result of input validation
     */
    @Data
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}
