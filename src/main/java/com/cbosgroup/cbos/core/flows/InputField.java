package com.cbosgroup.cbos.core.flows;

import lombok.Builder;
import lombok.Data;

/**
 * Defines an expected input field for a UserTaskState
 */
@Data
@Builder
public class InputField {

    /**
     * Supported input field types
     */
    public enum FieldType {
        STRING,
        NUMBER,
        BOOLEAN,
        DATE,
        DOCUMENT
    }

    /**
     * Field name (key in response map)
     */
    private String fieldName;

    /**
     * Display label for UI
     */
    private String label;

    /**
     * Type of input expected
     */
    private FieldType fieldType;

    /**
     * Whether this field is required
     */
    private boolean required;

    /**
     * Optional regex pattern for validation (STRING type)
     */
    private String validationPattern;

    /**
     * Optional description/help text
     */
    private String description;
}
